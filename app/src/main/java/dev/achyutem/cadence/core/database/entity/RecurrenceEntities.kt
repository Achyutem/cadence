package dev.achyutem.cadence.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * How a recurrence repeats. One vocabulary, shared by tasks and habits — there is exactly one
 * recurrence implementation in Cadence (see `domain/recurrence`).
 */
enum class RecurrenceFrequency {
    /** Every [RecurrenceRuleEntity.interval] days. `interval = 1` is "every day". */
    DAILY,

    /**
     * Every [RecurrenceRuleEntity.interval] weeks, on [RecurrenceRuleEntity.daysOfWeek].
     * "Weekdays" and "Mon/Wed/Fri" are both this, with different day sets.
     */
    WEEKLY,

    /** Every [RecurrenceRuleEntity.interval] months on [RecurrenceRuleEntity.dayOfMonth]. */
    MONTHLY_BY_DAY,

    /**
     * Every [RecurrenceRuleEntity.interval] months on the Nth weekday:
     * [RecurrenceRuleEntity.weekOfMonth] = 1..4, or -1 for "last".
     */
    MONTHLY_BY_WEEKDAY,

    /** Every [RecurrenceRuleEntity.interval] years on a month/day pair. */
    YEARLY,
}

/**
 * A recurrence rule is a pure description of *which dates* something lands on. It holds no
 * completion state and no task/habit identity, which is what lets one engine serve both.
 *
 * [anchorDate] is the phase reference for interval arithmetic ("every 2 days" counted from
 * where?). It is never changed after creation; editing the start of a recurring item creates a
 * new rule rather than mutating this one, so historical occurrences stay reconstructible.
 */
@Entity(tableName = "recurrence_rules")
data class RecurrenceRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val frequency: RecurrenceFrequency,
    /** Always >= 1. "Every 2 weeks" is WEEKLY with interval 2. */
    val interval: Int = 1,
    /** WEEKLY only. Empty is invalid and is normalised to the anchor's weekday on write. */
    val daysOfWeek: Set<DayOfWeek>? = null,
    /** MONTHLY_BY_DAY / YEARLY. 29–31 clamp to the last valid day of short months. */
    val dayOfMonth: Int? = null,
    /** MONTHLY_BY_WEEKDAY. 1..4, or -1 for "last". */
    val weekOfMonth: Int? = null,
    /** MONTHLY_BY_WEEKDAY. The weekday being counted. */
    val weekdayOfMonth: DayOfWeek? = null,
    /** YEARLY. 1..12. */
    val monthOfYear: Int? = null,
    val anchorDate: LocalDate,
    /** Inclusive last date, or null for "forever". */
    val endDate: LocalDate? = null,
    /** Stop after N occurrences, or null for unlimited. Mutually exclusive with [endDate]. */
    val occurrenceLimit: Int? = null,
)

/**
 * A reminder is a time-of-day plus an offset, not an absolute instant. The absolute alarm time
 * is derived per occurrence at scheduling time, in the *current* zone — which is what makes
 * reminders survive travel and DST correctly.
 */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /**
     * For an item with its own start time, the reminder fires at `startTime - leadMinutes` and
     * this field is ignored. For an item with no start time (an all-day task, a habit), the
     * reminder fires at this time of day.
     */
    val timeOfDay: java.time.LocalTime,
    val leadMinutes: Int = 0,
    val enabled: Boolean = true,
)
