package dev.achyutem.cadence.core.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dev.achyutem.cadence.MainActivity
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.common.AppContainer
import dev.achyutem.cadence.CadenceApplication
import dev.achyutem.cadence.domain.habit.isValueComplete
import dev.achyutem.cadence.core.database.entity.HabitEntryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Fires a reminder, and handles its actions.
 *
 * ### Why the actions live here rather than in the app
 *
 * The point of a notification action is that it works **without opening the app**. Completing a
 * task from the shade writes through the same DAOs the app uses, dismisses the notification and
 * refreshes the widgets, all inside the receiver.
 *
 * Database work runs on a `goAsync()` pending result so the process is kept alive long enough to
 * finish the write. Without it the receiver can return before the coroutine commits and the tap
 * silently does nothing.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as? CadenceApplication)?.container ?: return
        val pending = goAsync()

        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> fire(context, container, intent)
                    ACTION_COMPLETE -> complete(context, container, intent)
                    ACTION_SNOOZE -> snooze(context, container, intent)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun fire(context: Context, container: AppContainer, intent: Intent) {
        val target = intent.getStringExtra(EXTRA_TARGET)
            ?.let { runCatching { ReminderTarget.valueOf(it) }.getOrNull() } ?: return
        val entityId = intent.getLongExtra(EXTRA_ENTITY_ID, 0)
        val date = intent.getStringExtra(EXTRA_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return

        if (!hasNotificationPermission(context)) return
        NotificationChannels.ensureCreated(context)

        when (target) {
            ReminderTarget.TASK -> {
                val task = container.taskDao.getById(entityId) ?: return
                // Suppressed if the thing already happened. A reminder for a task you finished an
                // hour ago is worse than no reminder.
                if (task.completed || task.archived) return
                notify(
                    context,
                    id = notificationId(target, entityId, date),
                    channel = NotificationChannels.TASKS,
                    title = task.title,
                    body = task.notes?.takeIf { it.isNotBlank() },
                    actions = listOf(
                        action(context, ACTION_COMPLETE, target, entityId, date, R.string.notification_action_complete),
                        action(context, ACTION_SNOOZE, target, entityId, date, R.string.notification_action_snooze),
                    ),
                )
            }

            ReminderTarget.HABIT -> {
                val habit = container.habitDao.getById(entityId) ?: return
                if (habit.archived) return
                val entry = container.habitDao.getEntry(entityId, date)
                if (entry?.completed == true) return
                notify(
                    context,
                    id = notificationId(target, entityId, date),
                    channel = NotificationChannels.HABITS,
                    title = context.getString(R.string.notification_habit_title, habit.name),
                    body = context.getString(R.string.notification_habit_body),
                    actions = listOf(
                        action(context, ACTION_COMPLETE, target, entityId, date, R.string.notification_action_complete),
                    ),
                )
            }

        }
    }

    private suspend fun complete(context: Context, container: AppContainer, intent: Intent) {
        val target = intent.getStringExtra(EXTRA_TARGET)
            ?.let { runCatching { ReminderTarget.valueOf(it) }.getOrNull() } ?: return
        val entityId = intent.getLongExtra(EXTRA_ENTITY_ID, 0)
        val date = intent.getStringExtra(EXTRA_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val at = container.clock.now()

        when (target) {
            ReminderTarget.TASK -> container.taskDao.setCompleted(entityId, true, at)
            ReminderTarget.HABIT -> {
                val habit = container.habitDao.getById(entityId) ?: return
                val existing = container.habitDao.getEntry(entityId, date)
                val value = habit.targetValue.coerceAtLeast(1.0)
                container.habitDao.upsertEntry(
                    existing?.copy(value = value, completed = habit.isValueComplete(value), updatedAt = at)
                        ?: HabitEntryEntity(
                            habitId = entityId, date = date, value = value,
                            completed = habit.isValueComplete(value), createdAt = at, updatedAt = at,
                        )
                )
            }
        }

        context.getSystemService<NotificationManager>()?.cancel(notificationId(target, entityId, date))
        container.refreshWidgets()
    }

    /**
     * Snooze.
     *
     * Ten minutes, not configurable. A snooze the user has to think about is a decision, and the
     * whole point of the button is to postpone without deciding anything.
     */
    private fun snooze(context: Context, container: AppContainer, intent: Intent) {
        val target = intent.getStringExtra(EXTRA_TARGET) ?: return
        val entityId = intent.getLongExtra(EXTRA_ENTITY_ID, 0)
        val dateText = intent.getStringExtra(EXTRA_DATE) ?: return
        val date = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: return
        val parsedTarget = runCatching { ReminderTarget.valueOf(target) }.getOrNull() ?: return

        val manager = context.getSystemService<android.app.AlarmManager>() ?: return
        val triggerAt = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L

        val fireIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra(EXTRA_TARGET, target)
            putExtra(EXTRA_ENTITY_ID, entityId)
            putExtra(EXTRA_DATE, dateText)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            ScheduledReminder(parsedTarget, entityId, date, java.time.Instant.EPOCH).requestCode,
            fireIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        manager.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pending)

        context.getSystemService<NotificationManager>()
            ?.cancel(notificationId(parsedTarget, entityId, date))
    }

    private fun notify(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        body: String?,
        actions: List<NotificationCompat.Action>,
    ) {
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification: Notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .apply { body?.let { setContentText(it) } }
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .apply { actions.forEach { addAction(it) } }
            .build()

        context.getSystemService<NotificationManager>()?.notify(id, notification)
    }

    private fun action(
        context: Context,
        actionName: String,
        target: ReminderTarget,
        entityId: Long,
        date: LocalDate,
        labelRes: Int,
    ): NotificationCompat.Action {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = actionName
            putExtra(EXTRA_TARGET, target.name)
            putExtra(EXTRA_ENTITY_ID, entityId)
            putExtra(EXTRA_DATE, date.toString())
        }
        val pending = PendingIntent.getBroadcast(
            context,
            // Distinct from the alarm's own request code so completing does not cancel the alarm.
            notificationId(target, entityId, date) * 31 + actionName.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Action.Builder(0, context.getString(labelRes), pending).build()
    }

    companion object {
        const val ACTION_FIRE = "dev.achyutem.cadence.REMINDER_FIRE"
        const val ACTION_COMPLETE = "dev.achyutem.cadence.REMINDER_COMPLETE"
        const val ACTION_SNOOZE = "dev.achyutem.cadence.REMINDER_SNOOZE"

        const val EXTRA_TARGET = "target"
        const val EXTRA_ENTITY_ID = "entityId"
        const val EXTRA_DATE = "date"

        const val SNOOZE_MINUTES = 10

        fun notificationId(target: ReminderTarget, entityId: Long, date: LocalDate): Int =
            ((target.ordinal * 31 + entityId.hashCode()) * 31 + date.toEpochDay().toInt())

        fun hasNotificationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    }
}
