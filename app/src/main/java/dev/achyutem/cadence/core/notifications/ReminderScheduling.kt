package dev.achyutem.cadence.core.notifications

import dev.achyutem.cadence.core.database.entity.ReminderEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** What a scheduled alarm is for, so the receiver can build the right notification. */
enum class ReminderTarget { TASK, HABIT }

/**
 * One alarm: a target, a date, and the exact instant it should fire.
 */
data class ScheduledReminder(
    val target: ReminderTarget,
    val entityId: Long,
    val date: LocalDate,
    val triggerAt: Instant,
) {
    /**
     * A stable request code for `PendingIntent`.
     *
     * Derived from the target, the entity and the date rather than allocated, so rescheduling the
     * same occurrence replaces its alarm instead of stacking a second one. Collisions across
     * different entities are possible in principle but need a hash clash on a 31-bit space, and
     * the cost of one would be a single missed reminder rather than data loss.
     */
    val requestCode: Int
        get() = (target.ordinal * 31 + entityId.hashCode()) * 31 + date.toEpochDay().toInt()
}

/**
 * Turning a reminder plus a date into an instant.
 *
 * This is the whole reason a reminder is stored as a [LocalTime] and an offset rather than as an
 * absolute instant: the instant is computed **here, at scheduling time, in the current zone**. A
 * stored `Instant` for "09:00 daily" would arrive at 08:00 after a DST change or a flight, and
 * nothing downstream could tell that it was wrong.
 */
object ReminderScheduling {

    /**
     * When should this reminder fire for [date]?
     *
     * An item with its own [startTime] fires [ReminderEntity.leadMinutes] before it. An item
     * without one (an all-day task, a habit) fires at the reminder's own time of day.
     *
     * Returns null when the reminder is disabled.
     */
    fun triggerInstant(
        reminder: ReminderEntity,
        date: LocalDate,
        startTime: LocalTime?,
        zone: ZoneId,
    ): Instant? {
        if (!reminder.enabled) return null
        val base = startTime ?: reminder.timeOfDay
        val local = date.atTime(base).minusMinutes(reminder.leadMinutes.toLong())
        // A DST gap can make a local time non-existent; `atZone` resolves it forward, which is the
        // behaviour a person expects (the reminder still happens, at the next valid moment).
        return local.atZone(zone).toInstant()
    }

    /**
     * Shift a trigger out of quiet hours.
     *
     * Deferred to the end of the window rather than dropped: a reminder the user asked for should
     * still arrive, just not at 3am. A window that wraps midnight (22:00 to 07:00) is handled by
     * testing the two halves separately.
     */
    fun applyQuietHours(
        trigger: Instant,
        zone: ZoneId,
        enabled: Boolean,
        start: LocalTime,
        end: LocalTime,
    ): Instant {
        if (!enabled || start == end) return trigger
        val local = trigger.atZone(zone)
        val time = local.toLocalTime()

        val inQuiet = if (start < end) {
            time >= start && time < end
        } else {
            // Wraps midnight.
            time >= start || time < end
        }
        if (!inQuiet) return trigger

        val resumeDate = if (start < end || time < end) local.toLocalDate() else local.toLocalDate().plusDays(1)
        return resumeDate.atTime(end).atZone(zone).toInstant()
    }
}
