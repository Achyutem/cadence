package dev.achyutem.cadence

import dev.achyutem.cadence.core.database.entity.HabitEntryEntity
import dev.achyutem.cadence.domain.recurrence.RecurrencePresets
import dev.achyutem.cadence.domain.statistics.HabitStatistics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Streaks and rates.
 *
 * These are the numbers people look at every day, so getting them subtly wrong is worse than not
 * showing them. The cases below are the ones where a naive implementation quietly misbehaves:
 * unscheduled days, today-not-done-yet, and windows that start before the habit existed.
 */
class HabitStatisticsTest {

    private val epoch: Instant = Instant.parse("2026-09-01T00:00:00Z")

    private fun entry(date: String, value: Double = 1.0, completed: Boolean = true) =
        HabitEntryEntity(
            habitId = 1,
            date = LocalDate.parse(date),
            value = value,
            completed = completed,
            createdAt = epoch,
            updatedAt = epoch,
        )

    private fun d(value: String) = LocalDate.parse(value)

    // --- Current streak ---

    @Test
    fun `a daily habit done three days running has a streak of three`() {
        val entries = listOf(entry("2026-09-18"), entry("2026-09-19"), entry("2026-09-20"))
        assertEquals(
            3,
            HabitStatistics.currentStreak(entries, null, d("2026-09-01"), d("2026-09-20")),
        )
    }

    @Test
    fun `today not done yet does not break the streak`() {
        // The grace rule: at 9am, a habit you have not got to has not been broken.
        val entries = listOf(entry("2026-09-18"), entry("2026-09-19"))
        assertEquals(
            2,
            HabitStatistics.currentStreak(entries, null, d("2026-09-01"), d("2026-09-20")),
        )
    }

    @Test
    fun `a missed day yesterday does break the streak`() {
        val entries = listOf(entry("2026-09-17"), entry("2026-09-18"))
        assertEquals(
            0,
            HabitStatistics.currentStreak(entries, null, d("2026-09-01"), d("2026-09-20")),
        )
    }

    @Test
    fun `an incomplete entry counts as a miss, not as a gap`() {
        val entries = listOf(
            entry("2026-09-18"),
            entry("2026-09-19", value = 0.5, completed = false),
            entry("2026-09-20"),
        )
        assertEquals(
            1,
            HabitStatistics.currentStreak(entries, null, d("2026-09-01"), d("2026-09-20")),
        )
    }

    @Test
    fun `a weekday habit keeps its streak across the weekend`() {
        // The case that makes a naive day-by-day walk wrong: Saturday and Sunday are not misses,
        // they are not scheduled at all.
        val rule = RecurrencePresets.weekdays(d("2026-09-01"))
        val entries = listOf(
            entry("2026-09-17"), // Thu
            entry("2026-09-18"), // Fri
            entry("2026-09-21"), // Mon — weekend skipped, streak continues
        )
        assertEquals(
            3,
            HabitStatistics.currentStreak(entries, rule, d("2026-09-01"), d("2026-09-21")),
        )
    }

    @Test
    fun `a weekday habit is not broken by being asked on a Saturday`() {
        val rule = RecurrencePresets.weekdays(d("2026-09-01"))
        val entries = listOf(entry("2026-09-24"), entry("2026-09-25")) // Thu, Fri
        assertEquals(
            2,
            HabitStatistics.currentStreak(entries, rule, d("2026-09-01"), d("2026-09-26")), // Sat
        )
    }

    @Test
    fun `no entries means no streak`() {
        assertEquals(
            0,
            HabitStatistics.currentStreak(emptyList(), null, d("2026-09-01"), d("2026-09-20")),
        )
    }

    @Test
    fun `the streak never reaches back before the habit existed`() {
        val entries = listOf(entry("2026-09-19"), entry("2026-09-20"))
        assertEquals(
            2,
            HabitStatistics.currentStreak(entries, null, d("2026-09-19"), d("2026-09-20")),
        )
    }

    // --- Best streak ---

    @Test
    fun `best streak finds the longest historical run, not the current one`() {
        val entries = listOf(
            entry("2026-09-01"), entry("2026-09-02"), entry("2026-09-03"), entry("2026-09-04"),
            // gap on the 5th
            entry("2026-09-06"), entry("2026-09-07"),
        )
        assertEquals(
            4,
            HabitStatistics.bestStreak(entries, null, d("2026-09-01"), d("2026-09-07")),
        )
    }

    @Test
    fun `best streak counts scheduled days only`() {
        val rule = RecurrencePresets.weekly(
            anchor = d("2026-09-07"),
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        )
        val entries = listOf(
            entry("2026-09-07"), entry("2026-09-10"), entry("2026-09-14"), entry("2026-09-17"),
        )
        assertEquals(
            4,
            HabitStatistics.bestStreak(entries, rule, d("2026-09-07"), d("2026-09-17")),
        )
    }

    // --- Completion rate ---

    @Test
    fun `completion rate is measured against scheduled days, not calendar days`() {
        // A weekday habit over a two-week window: 10 scheduled days, not 14.
        val rule = RecurrencePresets.weekdays(d("2026-09-01"))
        val entries = (14..18).map { entry("2026-09-%02d".format(it)) } // Mon–Fri, all done
        val rate = HabitStatistics.completionRate(
            entries, rule, d("2026-09-01"), d("2026-09-14"), d("2026-09-27"), d("2026-09-27"),
        )
        assertEquals(10, rate.scheduled)
        assertEquals(5, rate.completed)
        assertEquals(50, rate.percent)
    }

    @Test
    fun `completion rate excludes days before the habit was created`() {
        // A habit created yesterday is not 3% consistent because the month is nearly over.
        val entries = listOf(entry("2026-09-20"))
        val rate = HabitStatistics.completionRate(
            entries, null, d("2026-09-20"), d("2026-09-01"), d("2026-09-30"), d("2026-09-20"),
        )
        assertEquals(1, rate.scheduled)
        assertEquals(1, rate.completed)
        assertEquals(100, rate.percent)
    }

    @Test
    fun `completion rate excludes the future`() {
        val entries = listOf(entry("2026-09-19"), entry("2026-09-20"))
        val rate = HabitStatistics.completionRate(
            entries, null, d("2026-09-19"), d("2026-09-19"), d("2026-09-30"), d("2026-09-20"),
        )
        assertEquals(2, rate.scheduled)
    }

    @Test
    fun `a rate with no scheduled days reports no data rather than zero percent`() {
        val rate = HabitStatistics.completionRate(
            emptyList(), null, d("2026-10-01"), d("2026-09-01"), d("2026-09-30"), d("2026-09-20"),
        )
        assertEquals(false, rate.hasData)
        assertNull(rate.percent)
    }

    // --- Averages ---

    @Test
    fun `average ignores days with no entry rather than counting them as zero`() {
        val entries = listOf(
            entry("2026-09-18", value = 30.0),
            entry("2026-09-20", value = 50.0),
        )
        assertEquals(
            40.0,
            HabitStatistics.averageValue(entries, d("2026-09-01"), d("2026-09-30"))!!,
            0.001,
        )
    }

    @Test
    fun `average with no entries is null, not zero`() {
        assertNull(HabitStatistics.averageValue(emptyList(), d("2026-09-01"), d("2026-09-30")))
    }

    // --- Heatmap ---

    @Test
    fun `heatmap intensity is relative to the target`() {
        val entries = listOf(
            entry("2026-09-01", value = 8.0),  // 100% → 4
            entry("2026-09-02", value = 6.0),  // 75%  → 3
            entry("2026-09-03", value = 4.0),  // 50%  → 2
            entry("2026-09-04", value = 1.0),  // 12%  → 1
        )
        val levels = HabitStatistics.heatmapLevels(entries, 8.0, d("2026-09-01"), d("2026-09-05"))
        assertEquals(4, levels[d("2026-09-01")])
        assertEquals(3, levels[d("2026-09-02")])
        assertEquals(2, levels[d("2026-09-03")])
        assertEquals(1, levels[d("2026-09-04")])
        assertEquals(0, levels[d("2026-09-05")]) // no entry
    }

    @Test
    fun `exceeding the target does not overflow the top level`() {
        val entries = listOf(entry("2026-09-01", value = 75.0))
        val levels = HabitStatistics.heatmapLevels(entries, 50.0, d("2026-09-01"), d("2026-09-01"))
        assertEquals(4, levels[d("2026-09-01")])
    }

    @Test
    fun `heatmap covers every date in the range`() {
        val levels = HabitStatistics.heatmapLevels(
            emptyList(), 1.0, d("2026-09-01"), d("2026-09-30"),
        )
        assertEquals(30, levels.size)
        assertTrue(levels.values.all { it == 0 })
    }
}
