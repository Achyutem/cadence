package dev.achyutem.cadence.domain.recurrence

import dev.achyutem.cadence.core.database.entity.RecurrenceFrequency
import dev.achyutem.cadence.core.database.entity.RecurrenceRuleEntity
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The recurrence patterns people actually pick, as constructors.
 *
 * These exist so that a screen never hand-assembles a [RecurrenceRuleEntity] with the wrong
 * fields populated for its frequency; a weekly rule with a `dayOfMonth`, say, which would be
 * silently ignored and produce a schedule nobody asked for.
 */
object RecurrencePresets {

    fun daily(anchor: LocalDate, interval: Int = 1) = RecurrenceRuleEntity(
        frequency = RecurrenceFrequency.DAILY,
        interval = interval.coerceAtLeast(1),
        anchorDate = anchor,
    )

    fun weekdays(anchor: LocalDate) = RecurrenceRuleEntity(
        frequency = RecurrenceFrequency.WEEKLY,
        interval = 1,
        daysOfWeek = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        ),
        anchorDate = anchor,
    )

    fun weekly(anchor: LocalDate, days: Set<DayOfWeek>, interval: Int = 1) = RecurrenceRuleEntity(
        frequency = RecurrenceFrequency.WEEKLY,
        interval = interval.coerceAtLeast(1),
        // An empty day set would match nothing at all, which is never what the user meant; fall
        // back to the anchor's own weekday.
        daysOfWeek = days.ifEmpty { setOf(anchor.dayOfWeek) },
        anchorDate = anchor,
    )

    fun monthlyOnDay(anchor: LocalDate, dayOfMonth: Int = anchor.dayOfMonth, interval: Int = 1) =
        RecurrenceRuleEntity(
            frequency = RecurrenceFrequency.MONTHLY_BY_DAY,
            interval = interval.coerceAtLeast(1),
            dayOfMonth = dayOfMonth.coerceIn(1, 31),
            anchorDate = anchor,
        )

    /** [week] is 1..4, or [RecurrenceEngine.LAST_WEEK_OF_MONTH] for "last". */
    fun monthlyOnWeekday(
        anchor: LocalDate,
        week: Int,
        weekday: DayOfWeek,
        interval: Int = 1,
    ) = RecurrenceRuleEntity(
        frequency = RecurrenceFrequency.MONTHLY_BY_WEEKDAY,
        interval = interval.coerceAtLeast(1),
        weekOfMonth = week,
        weekdayOfMonth = weekday,
        anchorDate = anchor,
    )

    fun yearly(anchor: LocalDate, interval: Int = 1) = RecurrenceRuleEntity(
        frequency = RecurrenceFrequency.YEARLY,
        interval = interval.coerceAtLeast(1),
        monthOfYear = anchor.monthValue,
        dayOfMonth = anchor.dayOfMonth,
        anchorDate = anchor,
    )
}

/**
 * A short human description of a rule, for the list row and the editor.
 *
 * Lives here rather than in the UI because the phrasing is part of the recurrence vocabulary,
 * the task row, the habit row and the widget must all describe the same rule the same way.
 */
fun RecurrenceRuleEntity.describe(): String {
    val every = if (interval > 1) "Every $interval " else "Every "
    return when (frequency) {
        RecurrenceFrequency.DAILY ->
            if (interval == 1) "Every day" else "${every}days"

        RecurrenceFrequency.WEEKLY -> {
            val days = daysOfWeek.orEmpty()
            when {
                days.size == 7 -> if (interval == 1) "Every day" else "${every}weeks"
                days == WEEKDAY_SET -> "Weekdays"
                days == WEEKEND_SET -> "Weekends"
                days.isEmpty() -> "${every}weeks"
                interval == 1 && days.size == 1 ->
                    "Every ${days.first().displayName()}"
                else -> {
                    val names = days.sortedBy { it.value }.joinToString(" · ") { it.shortName() }
                    if (interval == 1) names else "$every weeks on $names".replace("  ", " ")
                }
            }
        }

        RecurrenceFrequency.MONTHLY_BY_DAY -> {
            val day = dayOfMonth ?: anchorDate.dayOfMonth
            if (interval == 1) "Monthly on the ${day.ordinal()}" else "${every}months on the ${day.ordinal()}"
        }

        RecurrenceFrequency.MONTHLY_BY_WEEKDAY -> {
            val weekday = (weekdayOfMonth ?: anchorDate.dayOfWeek).displayName()
            val position = when (weekOfMonth) {
                RecurrenceEngine.LAST_WEEK_OF_MONTH -> "last"
                1 -> "first"; 2 -> "second"; 3 -> "third"; 4 -> "fourth"
                else -> "first"
            }
            if (interval == 1) "Monthly on the $position $weekday" else "${every}months on the $position $weekday"
        }

        RecurrenceFrequency.YEARLY ->
            if (interval == 1) "Every year" else "${every}years"
    }
}

private val WEEKDAY_SET = setOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
)
private val WEEKEND_SET = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

private fun DayOfWeek.displayName(): String =
    getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())

private fun DayOfWeek.shortName(): String =
    getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())

/** 1 → "1st", 22 → "22nd". English-only; this is display text, not data. */
private fun Int.ordinal(): String {
    val suffix = when {
        this % 100 in 11..13 -> "th"
        this % 10 == 1 -> "st"
        this % 10 == 2 -> "nd"
        this % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$this$suffix"
}
