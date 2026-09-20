package dev.achyutem.cadence.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Date helpers shared by the calendar, the heatmap and the recurrence engine.
 *
 * Everything here operates on [LocalDate] / [LocalTime], local calendar concepts. A habit that
 * happens on "18 September" is a date, not an instant, and is never converted to UTC.
 */

/** Inclusive day-by-day sequence. Lazy so a multi-year heatmap range costs nothing to describe. */
fun LocalDate.datesUntilInclusive(end: LocalDate): Sequence<LocalDate> = sequence {
    var cursor = this@datesUntilInclusive
    while (!cursor.isAfter(end)) {
        yield(cursor)
        cursor = cursor.plusDays(1)
    }
}

/** Inclusive count of days between two dates. `a..a` is 1 day. */
fun daysBetweenInclusive(start: LocalDate, end: LocalDate): Int =
    (ChronoUnit.DAYS.between(start, end) + 1).toInt()

/** Start of the week containing this date, honouring the user's "week starts on" preference. */
fun LocalDate.startOfWeek(weekStart: DayOfWeek): LocalDate {
    val delta = (dayOfWeek.value - weekStart.value + 7) % 7
    return minusDays(delta.toLong())
}

fun LocalDate.endOfWeek(weekStart: DayOfWeek): LocalDate = startOfWeek(weekStart).plusDays(6)

fun LocalDate.startOfMonth(): LocalDate = withDayOfMonth(1)

fun LocalDate.endOfMonth(): LocalDate = YearMonth.from(this).atEndOfMonth()

fun LocalDate.isWeekend(): Boolean = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

fun LocalDate.isWeekday(): Boolean = !isWeekend()

/** The seven weekdays in display order for a given week start. */
fun weekdayOrder(weekStart: DayOfWeek): List<DayOfWeek> =
    (0..6).map { DayOfWeek.of((weekStart.value - 1 + it) % 7 + 1) }

/**
 * Which occurrence of its weekday this date is within its month: 1st Monday, 2nd Monday, ...
 * Used by "second Tuesday of every month" recurrence.
 */
fun LocalDate.weekdayOrdinalInMonth(): Int = (dayOfMonth - 1) / 7 + 1

/** True when this date is the last occurrence of its weekday in its month. */
fun LocalDate.isLastWeekdayOfMonth(): Boolean = plusDays(7).month != month

/**
 * Formatters are built per call rather than cached in a `val`.
 *
 * A static formatter captures [Locale.getDefault] at class-load time, so if the user changes the
 * system language while the process is alive, every date in the app keeps rendering in the old
 * locale until the app is killed. Building one is cheap next to the recomposition that displays it.
 */
fun LocalDate.formatDayAndMonth(): String =
    format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))

fun LocalDate.formatShort(): String =
    format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

fun LocalDate.formatWeekdayFull(): String =
    dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())

fun DayOfWeek.shortLabel(): String = getDisplayName(TextStyle.SHORT, Locale.getDefault())

fun DayOfWeek.narrowLabel(): String = getDisplayName(TextStyle.NARROW, Locale.getDefault())

/**
 * 24-hour or 12-hour clock text depending on the device locale setting is a platform concern;
 * this is the neutral form used for compact timeline labels.
 */
fun LocalTime.formatHourMinute(use24Hour: Boolean): String =
    if (use24Hour) {
        String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    } else {
        val h = when (hour % 12) { 0 -> 12; else -> hour % 12 }
        val suffix = if (hour < 12) "AM" else "PM"
        if (minute == 0) "$h $suffix" else String.format(Locale.getDefault(), "%d:%02d %s", h, minute, suffix)
    }

/** Minutes since local midnight, the coordinate used to lay out the day-view timeline. */
fun LocalTime.minutesOfDay(): Int = hour * 60 + minute

enum class DayPart { NIGHT, MORNING, AFTERNOON, EVENING }

fun LocalTime.dayPart(): DayPart = when (hour) {
    in 0..4 -> DayPart.NIGHT
    in 5..11 -> DayPart.MORNING
    in 12..17 -> DayPart.AFTERNOON
    in 18..21 -> DayPart.EVENING
    else -> DayPart.NIGHT
}
