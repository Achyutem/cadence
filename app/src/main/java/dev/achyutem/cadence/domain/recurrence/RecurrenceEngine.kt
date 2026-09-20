package dev.achyutem.cadence.domain.recurrence

import dev.achyutem.cadence.core.database.entity.RecurrenceFrequency
import dev.achyutem.cadence.core.database.entity.RecurrenceRuleEntity
import dev.achyutem.cadence.core.time.datesUntilInclusive
import dev.achyutem.cadence.core.time.isLastWeekdayOfMonth
import dev.achyutem.cadence.core.time.weekdayOrdinalInMonth
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The one recurrence engine. Tasks and habits both use it; there is no second implementation.
 *
 * Everything here is a pure function of a rule and a date. No clock, no database, no Android —
 * which is what lets the whole surface be covered by fast JVM tests, and why this is the module
 * with the heaviest test weight in the app.
 *
 * ### Two ideas do all the work
 *
 * **Phase comes from the anchor.** "Every 2 days" is meaningless without a starting point.
 * Interval arithmetic is always measured from [RecurrenceRuleEntity.anchorDate], so the same rule
 * always produces the same dates regardless of when it is asked.
 *
 * **Occurrences are computed, never stored.** A daily habit running for five years is one row.
 * The calendar asks for a week and gets a week; nothing is materialised in advance.
 */
object RecurrenceEngine {

    /** Does this rule land on [date]? */
    fun occursOn(rule: RecurrenceRuleEntity, date: LocalDate): Boolean {
        if (date < rule.anchorDate) return false
        rule.endDate?.let { if (date > it) return false }

        val matches = when (rule.frequency) {
            RecurrenceFrequency.DAILY -> matchesDaily(rule, date)
            RecurrenceFrequency.WEEKLY -> matchesWeekly(rule, date)
            RecurrenceFrequency.MONTHLY_BY_DAY -> matchesMonthlyByDay(rule, date)
            RecurrenceFrequency.MONTHLY_BY_WEEKDAY -> matchesMonthlyByWeekday(rule, date)
            RecurrenceFrequency.YEARLY -> matchesYearly(rule, date)
        }
        if (!matches) return false

        // An occurrence limit has to be evaluated by counting from the anchor, because "the 10th
        // occurrence" is not a date property — it depends on everything before it.
        rule.occurrenceLimit?.let { limit ->
            if (limit <= 0) return false
            val index = countOccurrencesUpTo(rule, date)
            if (index > limit) return false
        }
        return true
    }

    /**
     * Every date the rule lands on within an inclusive range.
     *
     * Range-based by construction. There is deliberately no "all occurrences" call: for an
     * unbounded rule that list is infinite, and for a long one it is a memory problem.
     */
    fun occurrencesBetween(
        rule: RecurrenceRuleEntity,
        start: LocalDate,
        end: LocalDate,
    ): List<LocalDate> {
        if (end < start) return emptyList()
        val from = maxOf(start, rule.anchorDate)
        val to = rule.endDate?.let { minOf(end, it) } ?: end
        if (to < from) return emptyList()
        return from.datesUntilInclusive(to).filter { occursOn(rule, it) }.toList()
    }

    /**
     * The next occurrence strictly after [date], or null if the rule has ended.
     *
     * Bounded by [SEARCH_LIMIT_DAYS] rather than looping forever: a rule can be satisfiable in
     * principle but have no future date (an interval that has passed its end, an exhausted
     * occurrence limit), and an unbounded search would hang instead of returning null.
     */
    fun nextOccurrenceAfter(rule: RecurrenceRuleEntity, date: LocalDate): LocalDate? {
        var cursor = date.plusDays(1)
        val limit = date.plusDays(SEARCH_LIMIT_DAYS)
        while (cursor <= limit) {
            rule.endDate?.let { if (cursor > it) return null }
            if (occursOn(rule, cursor)) return cursor
            cursor = cursor.plusDays(1)
        }
        return null
    }

    /** How many occurrences have happened from the anchor through [date], inclusive. */
    fun countOccurrencesUpTo(rule: RecurrenceRuleEntity, date: LocalDate): Int {
        if (date < rule.anchorDate) return 0
        return rule.anchorDate.datesUntilInclusive(date)
            .count { candidate -> matchesWithoutLimit(rule, candidate) }
    }

    /** The same predicate as [occursOn] minus the limit check, to avoid infinite recursion. */
    private fun matchesWithoutLimit(rule: RecurrenceRuleEntity, date: LocalDate): Boolean {
        if (date < rule.anchorDate) return false
        rule.endDate?.let { if (date > it) return false }
        return when (rule.frequency) {
            RecurrenceFrequency.DAILY -> matchesDaily(rule, date)
            RecurrenceFrequency.WEEKLY -> matchesWeekly(rule, date)
            RecurrenceFrequency.MONTHLY_BY_DAY -> matchesMonthlyByDay(rule, date)
            RecurrenceFrequency.MONTHLY_BY_WEEKDAY -> matchesMonthlyByWeekday(rule, date)
            RecurrenceFrequency.YEARLY -> matchesYearly(rule, date)
        }
    }

    private fun matchesDaily(rule: RecurrenceRuleEntity, date: LocalDate): Boolean {
        val interval = rule.interval.coerceAtLeast(1)
        val elapsed = ChronoUnit.DAYS.between(rule.anchorDate, date)
        return elapsed % interval == 0L
    }

    /**
     * Weekly, on a set of weekdays.
     *
     * The interval is counted in **whole weeks from the anchor's week**, not in days. Counting
     * days would make "every 2 weeks on Mon and Thu" drift, because Monday and Thursday are
     * different numbers of days from the anchor and would land in different parity buckets.
     *
     * Weeks are measured from Monday here regardless of the user's "week starts on" preference:
     * that setting governs how a calendar is *displayed*, and letting it change which dates a
     * rule produces would silently rewrite someone's schedule when they changed a display option.
     */
    private fun matchesWeekly(rule: RecurrenceRuleEntity, date: LocalDate): Boolean {
        val days = rule.daysOfWeek?.takeIf { it.isNotEmpty() } ?: setOf(rule.anchorDate.dayOfWeek)
        if (date.dayOfWeek !in days) return false

        val interval = rule.interval.coerceAtLeast(1)
        if (interval == 1) return true

        val anchorWeekStart = rule.anchorDate.with(DayOfWeek.MONDAY)
        val dateWeekStart = date.with(DayOfWeek.MONDAY)
        val weeksBetween = ChronoUnit.WEEKS.between(anchorWeekStart, dateWeekStart)
        return weeksBetween % interval == 0L
    }

    /**
     * Monthly on a day number.
     *
     * A day number past the end of a short month **clamps to the last day** rather than being
     * skipped. "The 31st of every month" should happen in February; a user who set that up meant
     * "the end of the month", and silently skipping four months a year is the wrong reading.
     */
    private fun matchesMonthlyByDay(rule: RecurrenceRuleEntity, date: LocalDate): Boolean {
        val target = rule.dayOfMonth ?: rule.anchorDate.dayOfMonth
        val lastDayOfMonth = date.lengthOfMonth()
        val effective = target.coerceAtMost(lastDayOfMonth)
        if (date.dayOfMonth != effective) return false
        return monthsFromAnchor(rule, date) % rule.interval.coerceAtLeast(1) == 0L
    }

    /**
     * Monthly on the Nth weekday: "second Tuesday", "last Friday".
     *
     * `weekOfMonth = -1` means last, which is the 4th occurrence in some months and the 5th in
     * others — so it is tested by asking whether another one fits in the month, not by counting.
     */
    private fun matchesMonthlyByWeekday(rule: RecurrenceRuleEntity, date: LocalDate): Boolean {
        val weekday = rule.weekdayOfMonth ?: rule.anchorDate.dayOfWeek
        if (date.dayOfWeek != weekday) return false

        val week = rule.weekOfMonth ?: rule.anchorDate.weekdayOrdinalInMonth()
        val positionMatches = if (week == LAST_WEEK_OF_MONTH) {
            date.isLastWeekdayOfMonth()
        } else {
            date.weekdayOrdinalInMonth() == week
        }
        if (!positionMatches) return false

        return monthsFromAnchor(rule, date) % rule.interval.coerceAtLeast(1) == 0L
    }

    /**
     * Yearly.
     *
     * 29 February falls back to the 28th in common years, for the same reason short months clamp:
     * an anniversary that vanishes for three years out of four is not what anyone meant.
     */
    private fun matchesYearly(rule: RecurrenceRuleEntity, date: LocalDate): Boolean {
        val month = rule.monthOfYear ?: rule.anchorDate.monthValue
        if (date.monthValue != month) return false

        val targetDay = rule.dayOfMonth ?: rule.anchorDate.dayOfMonth
        val effective = targetDay.coerceAtMost(date.lengthOfMonth())
        if (date.dayOfMonth != effective) return false

        val yearsBetween = (date.year - rule.anchorDate.year).toLong()
        return yearsBetween >= 0 && yearsBetween % rule.interval.coerceAtLeast(1) == 0L
    }

    private fun monthsFromAnchor(rule: RecurrenceRuleEntity, date: LocalDate): Long =
        (date.year - rule.anchorDate.year) * 12L + (date.monthValue - rule.anchorDate.monthValue)

    /** `weekOfMonth` sentinel for "the last one in the month". */
    const val LAST_WEEK_OF_MONTH = -1

    /**
     * How far [nextOccurrenceAfter] will look before giving up. Two years covers every rule this
     * engine can express — the sparsest is yearly — with room to spare.
     */
    private const val SEARCH_LIMIT_DAYS = 366L * 2
}
