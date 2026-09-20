package dev.achyutem.cadence

import dev.achyutem.cadence.core.database.entity.RecurrenceFrequency
import dev.achyutem.cadence.core.database.entity.RecurrenceRuleEntity
import dev.achyutem.cadence.domain.recurrence.RecurrenceEngine
import dev.achyutem.cadence.domain.recurrence.RecurrenceEngine.LAST_WEEK_OF_MONTH
import dev.achyutem.cadence.domain.recurrence.RecurrencePresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The recurrence engine's test suite.
 *
 * Every case in `docs/RECURRENCE.md` appears here. Date arithmetic fails quietly — a rule that is
 * wrong one month in four looks fine in a demo and is infuriating in daily use — so this is the
 * densest set of tests in the project by design.
 *
 * Dates are chosen for their calendar shape, not at random, and each is spelled out in a comment
 * where the shape is the point.
 */
class RecurrenceEngineTest {

    private fun on(rule: RecurrenceRuleEntity, date: String) =
        RecurrenceEngine.occursOn(rule, LocalDate.parse(date))

    private fun between(rule: RecurrenceRuleEntity, start: String, end: String) =
        RecurrenceEngine.occurrencesBetween(rule, LocalDate.parse(start), LocalDate.parse(end))

    // --- Daily ---

    @Test
    fun `every day matches every day`() {
        val rule = RecurrencePresets.daily(LocalDate.parse("2026-09-01"))
        assertTrue(on(rule, "2026-09-01"))
        assertTrue(on(rule, "2026-09-02"))
        assertTrue(on(rule, "2027-03-15"))
    }

    @Test
    fun `nothing occurs before the anchor`() {
        val rule = RecurrencePresets.daily(LocalDate.parse("2026-09-10"))
        assertFalse(on(rule, "2026-09-09"))
        assertTrue(on(rule, "2026-09-10"))
    }

    @Test
    fun `every 2 days is phased from the anchor`() {
        val rule = RecurrencePresets.daily(LocalDate.parse("2026-09-01"), interval = 2)
        assertTrue(on(rule, "2026-09-01"))
        assertFalse(on(rule, "2026-09-02"))
        assertTrue(on(rule, "2026-09-03"))
        // Still in phase months later — the arithmetic is from the anchor, not from the month.
        assertTrue(on(rule, "2026-11-30"))
        assertFalse(on(rule, "2026-12-01"))
    }

    @Test
    fun `every 3 days stays in phase across a leap day`() {
        val rule = RecurrencePresets.daily(LocalDate.parse("2024-02-26"), interval = 3)
        assertTrue(on(rule, "2024-02-26"))
        assertTrue(on(rule, "2024-02-29")) // the leap day itself
        assertTrue(on(rule, "2024-03-03"))
        assertFalse(on(rule, "2024-03-02"))
    }

    // --- Weekly ---

    @Test
    fun `weekdays matches Monday to Friday only`() {
        val rule = RecurrencePresets.weekdays(LocalDate.parse("2026-09-21")) // a Monday
        assertTrue(on(rule, "2026-09-21"))
        assertTrue(on(rule, "2026-09-25")) // Friday
        assertFalse(on(rule, "2026-09-26")) // Saturday
        assertFalse(on(rule, "2026-09-27")) // Sunday
    }

    @Test
    fun `specific weekdays match only those days`() {
        val rule = RecurrencePresets.weekly(
            anchor = LocalDate.parse("2026-09-21"),
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )
        assertTrue(on(rule, "2026-09-21"))  // Mon
        assertFalse(on(rule, "2026-09-22")) // Tue
        assertTrue(on(rule, "2026-09-23"))  // Wed
        assertTrue(on(rule, "2026-09-25"))  // Fri
    }

    @Test
    fun `every 2 weeks does not drift across multiple weekdays`() {
        // The bug this guards: counting the interval in days instead of whole weeks puts Monday
        // and Thursday in different parity buckets, so one of them silently lands on the wrong
        // weeks.
        val rule = RecurrencePresets.weekly(
            anchor = LocalDate.parse("2026-09-21"), // Monday
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            interval = 2,
        )
        assertTrue(on(rule, "2026-09-21"))  // Mon, week 0
        assertTrue(on(rule, "2026-09-24"))  // Thu, week 0 — same week, must also match
        assertFalse(on(rule, "2026-09-28")) // Mon, week 1
        assertFalse(on(rule, "2026-10-01")) // Thu, week 1
        assertTrue(on(rule, "2026-10-05"))  // Mon, week 2
        assertTrue(on(rule, "2026-10-08"))  // Thu, week 2
    }

    @Test
    fun `an empty weekday set falls back to the anchor weekday rather than matching nothing`() {
        val rule = RecurrenceRuleEntity(
            frequency = RecurrenceFrequency.WEEKLY,
            interval = 1,
            daysOfWeek = emptySet(),
            anchorDate = LocalDate.parse("2026-09-23"), // Wednesday
        )
        assertTrue(on(rule, "2026-09-30"))
        assertFalse(on(rule, "2026-09-29"))
    }

    // --- Monthly by day ---

    @Test
    fun `first of every month`() {
        val rule = RecurrencePresets.monthlyOnDay(LocalDate.parse("2026-09-01"), dayOfMonth = 1)
        assertTrue(on(rule, "2026-09-01"))
        assertTrue(on(rule, "2026-10-01"))
        assertTrue(on(rule, "2027-01-01"))
        assertFalse(on(rule, "2026-10-02"))
    }

    @Test
    fun `the 31st clamps to the last day of shorter months`() {
        // The product decision: "the 31st" means the end of the month, so it must not skip
        // February, April, June, September or November.
        val rule = RecurrencePresets.monthlyOnDay(LocalDate.parse("2026-01-31"), dayOfMonth = 31)
        assertTrue(on(rule, "2026-01-31"))
        assertTrue(on(rule, "2026-02-28")) // clamped — 2026 is not a leap year
        assertTrue(on(rule, "2026-04-30")) // clamped
        assertTrue(on(rule, "2026-05-31"))
        assertFalse(on(rule, "2026-02-27"))
    }

    @Test
    fun `the 31st clamps to 29 February in a leap year`() {
        val rule = RecurrencePresets.monthlyOnDay(LocalDate.parse("2024-01-31"), dayOfMonth = 31)
        assertTrue(on(rule, "2024-02-29"))
        assertFalse(on(rule, "2024-02-28"))
    }

    @Test
    fun `every 2 months is phased from the anchor month`() {
        val rule = RecurrencePresets.monthlyOnDay(
            LocalDate.parse("2026-09-15"), dayOfMonth = 15, interval = 2,
        )
        assertTrue(on(rule, "2026-09-15"))
        assertFalse(on(rule, "2026-10-15"))
        assertTrue(on(rule, "2026-11-15"))
        assertTrue(on(rule, "2027-01-15")) // phase survives the year boundary
    }

    // --- Monthly by weekday ---

    @Test
    fun `second Tuesday of every month`() {
        val rule = RecurrencePresets.monthlyOnWeekday(
            anchor = LocalDate.parse("2026-09-08"),
            week = 2,
            weekday = DayOfWeek.TUESDAY,
        )
        assertTrue(on(rule, "2026-09-08"))
        assertFalse(on(rule, "2026-09-01")) // first Tuesday
        assertTrue(on(rule, "2026-10-13"))  // second Tuesday of October
    }

    @Test
    fun `last Friday works whether it is the fourth or the fifth`() {
        val rule = RecurrencePresets.monthlyOnWeekday(
            anchor = LocalDate.parse("2026-09-25"),
            week = LAST_WEEK_OF_MONTH,
            weekday = DayOfWeek.FRIDAY,
        )
        // September 2026 has four Fridays; the 25th is the last.
        assertTrue(on(rule, "2026-09-25"))
        assertFalse(on(rule, "2026-09-18"))
        // October 2026 has five Fridays; the 30th is the last.
        assertTrue(on(rule, "2026-10-30"))
        assertFalse(on(rule, "2026-10-23"))
    }

    // --- Yearly ---

    @Test
    fun `yearly repeats on the same month and day`() {
        val rule = RecurrencePresets.yearly(LocalDate.parse("2026-05-04"))
        assertTrue(on(rule, "2026-05-04"))
        assertTrue(on(rule, "2027-05-04"))
        assertFalse(on(rule, "2027-05-05"))
    }

    @Test
    fun `29 February falls back to the 28th in common years`() {
        val rule = RecurrencePresets.yearly(LocalDate.parse("2024-02-29"))
        assertTrue(on(rule, "2024-02-29"))
        assertTrue(on(rule, "2025-02-28")) // clamped
        assertTrue(on(rule, "2028-02-29")) // next leap year, back to the real date
        assertFalse(on(rule, "2025-03-01"))
    }

    // --- Termination ---

    @Test
    fun `end date is inclusive`() {
        val rule = RecurrencePresets.daily(LocalDate.parse("2026-09-01"))
            .copy(endDate = LocalDate.parse("2026-09-03"))
        assertTrue(on(rule, "2026-09-03"))
        assertFalse(on(rule, "2026-09-04"))
    }

    @Test
    fun `occurrence limit counts occurrences, not days`() {
        // Every 3 days from the 1st: 1, 4, 7, 10, ... A limit of 3 stops after the 7th.
        val rule = RecurrencePresets.daily(LocalDate.parse("2026-09-01"), interval = 3)
            .copy(occurrenceLimit = 3)
        assertTrue(on(rule, "2026-09-01"))
        assertTrue(on(rule, "2026-09-04"))
        assertTrue(on(rule, "2026-09-07"))
        assertFalse(on(rule, "2026-09-10"))
    }

    @Test
    fun `a zero occurrence limit produces nothing`() {
        val rule = RecurrencePresets.daily(LocalDate.parse("2026-09-01")).copy(occurrenceLimit = 0)
        assertFalse(on(rule, "2026-09-01"))
    }

    // --- Ranges ---

    @Test
    fun `range query returns exactly the dates in the window`() {
        val rule = RecurrencePresets.weekly(
            anchor = LocalDate.parse("2026-09-21"),
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY),
        )
        val dates = between(rule, "2026-09-21", "2026-10-04")
        assertEquals(
            listOf("2026-09-21", "2026-09-25", "2026-09-28", "2026-10-02").map(LocalDate::parse),
            dates,
        )
    }

    @Test
    fun `range query clips to the anchor and the end date`() {
        val rule = RecurrencePresets.daily(LocalDate.parse("2026-09-10"))
            .copy(endDate = LocalDate.parse("2026-09-12"))
        val dates = between(rule, "2026-09-01", "2026-09-30")
        assertEquals(3, dates.size)
        assertEquals(LocalDate.parse("2026-09-10"), dates.first())
        assertEquals(LocalDate.parse("2026-09-12"), dates.last())
    }

    @Test
    fun `an inverted range is empty rather than an error`() {
        val rule = RecurrencePresets.daily(LocalDate.parse("2026-09-01"))
        assertTrue(between(rule, "2026-09-10", "2026-09-01").isEmpty())
    }

    @Test
    fun `a single day range containing an occurrence returns one date`() {
        val rule = RecurrencePresets.daily(LocalDate.parse("2026-09-01"))
        assertEquals(1, between(rule, "2026-09-05", "2026-09-05").size)
    }

    // --- Next occurrence ---

    @Test
    fun `next occurrence is strictly after the given date`() {
        val rule = RecurrencePresets.daily(LocalDate.parse("2026-09-01"))
        assertEquals(
            LocalDate.parse("2026-09-06"),
            RecurrenceEngine.nextOccurrenceAfter(rule, LocalDate.parse("2026-09-05")),
        )
    }

    @Test
    fun `next occurrence skips to the following month for a monthly rule`() {
        val rule = RecurrencePresets.monthlyOnDay(LocalDate.parse("2026-09-01"), dayOfMonth = 1)
        assertEquals(
            LocalDate.parse("2026-10-01"),
            RecurrenceEngine.nextOccurrenceAfter(rule, LocalDate.parse("2026-09-01")),
        )
    }

    @Test
    fun `next occurrence is null once the rule has ended`() {
        val rule = RecurrencePresets.daily(LocalDate.parse("2026-09-01"))
            .copy(endDate = LocalDate.parse("2026-09-05"))
        assertNull(RecurrenceEngine.nextOccurrenceAfter(rule, LocalDate.parse("2026-09-05")))
    }

    @Test
    fun `next occurrence is null once an occurrence limit is exhausted`() {
        val rule = RecurrencePresets.daily(LocalDate.parse("2026-09-01")).copy(occurrenceLimit = 2)
        assertNull(RecurrenceEngine.nextOccurrenceAfter(rule, LocalDate.parse("2026-09-02")))
    }

    // --- Counting ---

    @Test
    fun `occurrence count is inclusive of the anchor`() {
        val rule = RecurrencePresets.daily(LocalDate.parse("2026-09-01"))
        assertEquals(1, RecurrenceEngine.countOccurrencesUpTo(rule, LocalDate.parse("2026-09-01")))
        assertEquals(5, RecurrenceEngine.countOccurrencesUpTo(rule, LocalDate.parse("2026-09-05")))
    }

    @Test
    fun `occurrence count is zero before the anchor`() {
        val rule = RecurrencePresets.daily(LocalDate.parse("2026-09-10"))
        assertEquals(0, RecurrenceEngine.countOccurrencesUpTo(rule, LocalDate.parse("2026-09-01")))
    }

    // --- Interval normalisation ---

    @Test
    fun `a zero or negative interval behaves as one instead of dividing by zero`() {
        val rule = RecurrenceRuleEntity(
            frequency = RecurrenceFrequency.DAILY,
            interval = 0,
            anchorDate = LocalDate.parse("2026-09-01"),
        )
        assertTrue(on(rule, "2026-09-02"))
    }

    // --- Year boundaries ---

    @Test
    fun `weekly rules cross the year boundary without a phase break`() {
        val rule = RecurrencePresets.weekly(
            anchor = LocalDate.parse("2026-12-28"), // Monday
            days = setOf(DayOfWeek.MONDAY),
            interval = 2,
        )
        assertTrue(on(rule, "2026-12-28"))
        assertFalse(on(rule, "2027-01-04"))
        assertTrue(on(rule, "2027-01-11"))
    }
}
