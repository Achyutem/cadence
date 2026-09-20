package dev.achyutem.cadence.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import dev.achyutem.cadence.core.database.CadenceDatabase
import dev.achyutem.cadence.core.datastore.SettingsRepository
import dev.achyutem.cadence.core.time.CadenceClock
import dev.achyutem.cadence.domain.recurrence.RecurrenceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Schedules the next few days of reminders.
 *
 * ### Why a rolling horizon rather than everything
 *
 * A daily habit is one database row and infinitely many occurrences. Scheduling them all is
 * impossible; scheduling a year of them would exhaust the system alarm quota and have to be torn
 * down and rebuilt on every edit. So only [HORIZON_DAYS] of occurrences are ever pending, and the
 * window is rolled forward whenever the app runs, after a reboot, and after a clock change.
 *
 * ### Why everything is cancelled and rebuilt
 *
 * Reconciling "which alarms should change" against "which alarms exist" needs a record of what was
 * scheduled, which is a second source of truth that can drift from the database. Cancelling the
 * horizon and re-adding it is O(a few dozen) alarms, runs in milliseconds, and cannot drift.
 * [ScheduledReminder.requestCode] is derived rather than allocated precisely so that a rebuild
 * replaces alarms instead of duplicating them.
 */
class ReminderScheduler(
    private val context: Context,
    private val database: CadenceDatabase,
    private val settings: SettingsRepository,
    private val clock: CadenceClock,
) {

    private val alarmManager: AlarmManager? get() = context.getSystemService()

    /** True when the system will honour an exact alarm. */
    fun canScheduleExact(): Boolean = alarmManager?.canScheduleExactAlarms() ?: false

    suspend fun rescheduleAll() = withContext(Dispatchers.Default) {
        val manager = alarmManager ?: return@withContext
        NotificationChannels.ensureCreated(context)

        val preferences = settings.preferences.first()
        val zone = clock.zone()
        val now = clock.now()
        val today = clock.today()
        val horizonEnd = today.plusDays(HORIZON_DAYS)

        val reminders = mutableListOf<ScheduledReminder>()

        // Tasks with a reminder attached.
        val tasks = database.taskDao().getAllForBackup()
            .filter { !it.archived && !it.completed && it.reminderId != null }
        for (task in tasks) {
            val reminder = database.recurrenceDao().getReminder(task.reminderId!!) ?: continue
            val dates = if (task.recurrenceRuleId != null) {
                val rule = database.recurrenceDao().getRule(task.recurrenceRuleId!!) ?: continue
                RecurrenceEngine.occurrencesBetween(rule, today, horizonEnd)
            } else {
                listOfNotNull(task.scheduledDate?.takeIf { it in today..horizonEnd })
            }
            for (date in dates) {
                val trigger = ReminderScheduling.triggerInstant(reminder, date, task.startTime, zone)
                    ?: continue
                reminders += ScheduledReminder(ReminderTarget.TASK, task.id, date, trigger)
            }
        }

        // Habits with a reminder attached.
        val habits = database.habitDao().getAllForBackup().filter { !it.archived && it.reminderId != null }
        for (habit in habits) {
            val reminder = database.recurrenceDao().getReminder(habit.reminderId!!) ?: continue
            val rule = habit.recurrenceRuleId?.let { database.recurrenceDao().getRule(it) }
            val dates = if (rule == null) {
                generateSequence(today) { it.plusDays(1) }.takeWhile { it <= horizonEnd }.toList()
            } else {
                RecurrenceEngine.occurrencesBetween(rule, today, horizonEnd)
            }
            for (date in dates) {
                val trigger = ReminderScheduling.triggerInstant(reminder, date, null, zone) ?: continue
                reminders += ScheduledReminder(ReminderTarget.HABIT, habit.id, date, trigger)
            }
        }

        // Cancel the horizon, then lay it down again.
        cancelHorizon(today, horizonEnd)

        for (entry in reminders) {
            val shifted = ReminderScheduling.applyQuietHours(
                entry.triggerAt, zone,
                preferences.quietHoursEnabled,
                preferences.quietHoursStart,
                preferences.quietHoursEnd,
            )
            // A trigger already in the past is dropped rather than fired immediately: a reminder
            // for 9am that you open the app at 6pm to discover is noise, not information.
            if (shifted.isBefore(now)) continue
            schedule(manager, entry.copy(triggerAt = shifted))
        }
    }

    private fun schedule(manager: AlarmManager, entry: ScheduledReminder) {
        val pending = pendingIntent(entry, create = true) ?: return
        val millis = entry.triggerAt.toEpochMilli()

        // Exact when allowed, inexact otherwise. Falling back rather than failing is deliberate:
        // a reminder a few minutes late is worth far more than no reminder and a silent error.
        if (canScheduleExact()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
    }

    private fun cancelHorizon(from: LocalDate, to: LocalDate) {
        val manager = alarmManager ?: return
        var date = from
        while (date <= to) {
            for (target in ReminderTarget.entries) {
                // Ids are not known here for every entity, so cancellation is done by re-deriving
                // the request code for every entity that currently has a reminder. Entities whose
                // reminder was just removed are handled by the same pass on the next reschedule.
                for (id in knownIds) {
                    val entry = ScheduledReminder(target, id, date, java.time.Instant.EPOCH)
                    pendingIntent(entry, create = false)?.let {
                        manager.cancel(it)
                        it.cancel()
                    }
                }
            }
            date = date.plusDays(1)
        }
    }

    /**
     * Entity ids seen during the last build, so the next cancellation pass can reach them.
     *
     * Kept in memory rather than persisted: after a process death the reboot receiver rebuilds the
     * whole horizon anyway, and a stale alarm that survives is replaced by its own request code the
     * moment the same occurrence is scheduled again.
     */
    private val knownIds = mutableSetOf<Long>()

    private fun pendingIntent(entry: ScheduledReminder, create: Boolean): PendingIntent? {
        knownIds += entry.entityId
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_TARGET, entry.target.name)
            putExtra(ReminderReceiver.EXTRA_ENTITY_ID, entry.entityId)
            putExtra(ReminderReceiver.EXTRA_DATE, entry.date.toString())
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, entry.requestCode, intent, flags)
    }

    companion object {
        /**
         * How far ahead alarms are laid down. Seven days is comfortably more than the gap between
         * app launches for a daily-use app, and small enough to stay well inside the alarm quota.
         */
        const val HORIZON_DAYS = 7L

    }
}
