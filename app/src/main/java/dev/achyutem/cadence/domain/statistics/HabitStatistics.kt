package dev.achyutem.cadence.domain.statistics

import dev.achyutem.cadence.core.database.entity.HabitEntryEntity
import dev.achyutem.cadence.core.database.entity.RecurrenceRuleEntity
import dev.achyutem.cadence.core.time.datesUntilInclusive
import dev.achyutem.cadence.domain.recurrence.RecurrenceEngine
import java.time.LocalDate

/**
 * Everything derived from habit history.
 *
 * Nothing in here is stored. Streaks, rates and averages are functions of the entry rows plus the
 * habit's recurrence rule, computed on demand, which is why editing a day in the past
 * immediately corrects every number that depends on it, and why no two screens can ever disagree.
 *
 * Pure functions with no clock and no database, so every rule below is unit-testable.
 */
object HabitStatistics {

    /**
     * The current streak, counted backwards from [asOf].
     *
     * Two decisions make this behave the way people expect rather than the way it is easy to
     * implement:
     *
     * **Only scheduled days count.** A weekday habit does not lose its streak over the weekend,
     * because Saturday was never a day it was supposed to happen. Days the rule does not include
     * are skipped entirely, not treated as successes or as failures.
     *
     * **Today is grace, not a gap.** If today is scheduled and not yet done, the streak is
     * measured up to yesterday instead of being reported as zero. A habit you have not got to yet
     * at 9am has not been broken, and showing "0 day streak" every morning would be both wrong
     * and dispiriting.
     */
    fun currentStreak(
        entries: List<HabitEntryEntity>,
        rule: RecurrenceRuleEntity?,
        startDate: LocalDate,
        asOf: LocalDate,
    ): Int {
        val completedDates = entries.filter { it.completed }.map { it.date }.toSet()
        if (completedDates.isEmpty()) return 0

        var cursor = asOf
        // Today counts only if it was actually done; otherwise start from yesterday.
        if (isScheduled(rule, cursor, startDate) && cursor !in completedDates) {
            cursor = cursor.minusDays(1)
        }

        var streak = 0
        while (!cursor.isBefore(startDate)) {
            if (!isScheduled(rule, cursor, startDate)) {
                cursor = cursor.minusDays(1)
                continue
            }
            if (cursor in completedDates) {
                streak++
                cursor = cursor.minusDays(1)
            } else {
                break
            }
        }
        return streak
    }

    /**
     * The longest run of consecutive scheduled days ever completed.
     *
     * Walks forward from the habit's start so that unscheduled days break nothing, and a missed
     * scheduled day ends the run.
     */
    fun bestStreak(
        entries: List<HabitEntryEntity>,
        rule: RecurrenceRuleEntity?,
        startDate: LocalDate,
        asOf: LocalDate,
    ): Int {
        val completedDates = entries.filter { it.completed }.map { it.date }.toSet()
        if (completedDates.isEmpty()) return 0

        var best = 0
        var run = 0
        startDate.datesUntilInclusive(asOf).forEach { date ->
            if (!isScheduled(rule, date, startDate)) return@forEach
            if (date in completedDates) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
        }
        return best
    }

    /**
     * Completion rate over a window: completed days ÷ scheduled days.
     *
     * The denominator is **scheduled** days, not calendar days. A weekday habit measured over a
     * month is judged against ~22 days, not 30, anything else quietly punishes the user for
     * having chosen a schedule.
     *
     * Days before the habit existed are excluded, and so are days in the future: a habit created
     * yesterday is not 3% consistent because the month is nearly over.
     */
    fun completionRate(
        entries: List<HabitEntryEntity>,
        rule: RecurrenceRuleEntity?,
        startDate: LocalDate,
        from: LocalDate,
        to: LocalDate,
        asOf: LocalDate,
    ): CompletionRate {
        val windowStart = maxOf(from, startDate)
        val windowEnd = minOf(to, asOf)
        if (windowEnd < windowStart) return CompletionRate(0, 0)

        val completedDates = entries.filter { it.completed }.map { it.date }.toSet()
        var scheduled = 0
        var completed = 0
        windowStart.datesUntilInclusive(windowEnd).forEach { date ->
            if (!isScheduled(rule, date, startDate)) return@forEach
            scheduled++
            if (date in completedDates) completed++
        }
        return CompletionRate(completed = completed, scheduled = scheduled)
    }

    /**
     * Mean recorded value across days that have an entry.
     *
     * Days with no entry are excluded rather than counted as zero. "Average reading session:
     * 34 minutes" means the average of the sessions that happened; folding in every skipped day
     * as a zero answers a different and much less useful question.
     */
    fun averageValue(entries: List<HabitEntryEntity>, from: LocalDate, to: LocalDate): Double? {
        val relevant = entries.filter { it.date >= from && it.date <= to && it.value > 0.0 }
        if (relevant.isEmpty()) return null
        return relevant.sumOf { it.value } / relevant.size
    }

    fun totalValue(entries: List<HabitEntryEntity>, from: LocalDate, to: LocalDate): Double =
        entries.filter { it.date >= from && it.date <= to }.sumOf { it.value }

    /**
     * Heatmap intensity, 0..4, for each date in a range.
     *
     * Intensity is **relative to the habit's own target**, not to its best-ever day. A target is
     * what the user decided success means; scaling to the maximum would make a good week look
     * pale simply because one exceptional day exists somewhere in the history.
     *
     * Level 0 is "nothing", so a day with any progress at all is always visibly non-empty.
     */
    fun heatmapLevels(
        entries: List<HabitEntryEntity>,
        target: Double,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, Int> {
        val byDate = entries.associateBy { it.date }
        return from.datesUntilInclusive(to).associateWith { date ->
            val entry = byDate[date]
            when {
                entry == null || entry.value <= 0.0 -> 0
                target <= 0.0 -> if (entry.completed) 4 else 1
                else -> {
                    val ratio = entry.value / target
                    when {
                        ratio >= 1.0 -> 4
                        ratio >= 0.66 -> 3
                        ratio >= 0.33 -> 2
                        else -> 1
                    }
                }
            }
        }
    }

    /** A null rule means "every day", substituted rather than special-cased at every call site. */
    private fun isScheduled(
        rule: RecurrenceRuleEntity?,
        date: LocalDate,
        startDate: LocalDate,
    ): Boolean {
        if (date < startDate) return false
        if (rule == null) return true
        return RecurrenceEngine.occursOn(rule, date)
    }
}

/**
 * A completion rate, kept as its two counts rather than a single fraction.
 *
 * "24 of 28 days" is more honest and more useful than "86%", and the UI needs both. Keeping the
 * numerator and denominator also makes "no scheduled days yet" representable, which a bare float
 * cannot do without pretending it is zero.
 */
data class CompletionRate(val completed: Int, val scheduled: Int) {
    val fraction: Float? get() = if (scheduled == 0) null else completed.toFloat() / scheduled
    val percent: Int? get() = fraction?.let { (it * 100).toInt() }
    val hasData: Boolean get() = scheduled > 0
}
