package dev.achyutem.cadence

import dev.achyutem.cadence.core.time.MutableCadenceClock
import dev.achyutem.cadence.core.time.daysBetweenInclusive
import dev.achyutem.cadence.core.time.datesUntilInclusive
import dev.achyutem.cadence.core.time.endOfMonth
import dev.achyutem.cadence.core.time.endOfWeek
import dev.achyutem.cadence.core.time.isLastWeekdayOfMonth
import dev.achyutem.cadence.core.time.isWeekday
import dev.achyutem.cadence.core.time.minutesOfDay
import dev.achyutem.cadence.core.time.startOfWeek
import dev.achyutem.cadence.core.time.weekdayOrder
import dev.achyutem.cadence.core.time.weekdayOrdinalInMonth
import dev.achyutem.cadence.core.time.formatHourMinute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Date arithmetic the recurrence engine, the calendar and the heatmap all depend on. These are
 * pure functions, so the edge cases that are painful to reproduce on a device, leap days, DST,
 * month ends, week starts, are cheap to pin down here.
 */
class DateTimeExtensionsTest {

    @Test
    fun `week start respects the user preference`() {
        val wednesday = LocalDate.of(2026, 9, 23)
        assertEquals(LocalDate.of(2026, 9, 21), wednesday.startOfWeek(DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 9, 20), wednesday.startOfWeek(DayOfWeek.SUNDAY))
        assertEquals(LocalDate.of(2026, 9, 19), wednesday.startOfWeek(DayOfWeek.SATURDAY))
    }

    @Test
    fun `week start on the day itself is that day`() {
        val monday = LocalDate.of(2026, 9, 21)
        assertEquals(monday, monday.startOfWeek(DayOfWeek.MONDAY))
    }

    @Test
    fun `week is always seven days`() {
        val date = LocalDate.of(2026, 9, 23)
        DayOfWeek.entries.forEach { start ->
            assertEquals(
                7,
                daysBetweenInclusive(date.startOfWeek(start), date.endOfWeek(start)),
            )
        }
    }

    @Test
    fun `weekday order rotates with the week start`() {
        assertEquals(
            listOf(
                DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
            ),
            weekdayOrder(DayOfWeek.SUNDAY),
        )
        assertEquals(DayOfWeek.MONDAY, weekdayOrder(DayOfWeek.MONDAY).first())
    }

    @Test
    fun `end of month handles February in leap and common years`() {
        assertEquals(LocalDate.of(2024, 2, 29), LocalDate.of(2024, 2, 10).endOfMonth())
        assertEquals(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 2, 10).endOfMonth())
    }

    @Test
    fun `inclusive day count includes both ends`() {
        val day = LocalDate.of(2026, 9, 20)
        assertEquals(1, daysBetweenInclusive(day, day))
        assertEquals(2, daysBetweenInclusive(day, day.plusDays(1)))
        assertEquals(366, daysBetweenInclusive(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31)))
    }

    @Test
    fun `date sequence spans a year boundary`() {
        val dates = LocalDate.of(2025, 12, 30)
            .datesUntilInclusive(LocalDate.of(2026, 1, 2))
            .toList()
        assertEquals(4, dates.size)
        assertEquals(LocalDate.of(2026, 1, 2), dates.last())
    }

    @Test
    fun `date sequence with end before start is empty`() {
        val dates = LocalDate.of(2026, 9, 20)
            .datesUntilInclusive(LocalDate.of(2026, 9, 19))
            .toList()
        assertTrue(dates.isEmpty())
    }

    @Test
    fun `weekday ordinal counts occurrences within the month`() {
        // September 2026: Fridays fall on the 4th, 11th, 18th, 25th.
        assertEquals(1, LocalDate.of(2026, 9, 4).weekdayOrdinalInMonth())
        assertEquals(3, LocalDate.of(2026, 9, 18).weekdayOrdinalInMonth())
        assertEquals(4, LocalDate.of(2026, 9, 25).weekdayOrdinalInMonth())
    }

    @Test
    fun `last weekday of month is detected regardless of whether it is the fourth or fifth`() {
        // "Last Friday of every month"; September 2026 has four Fridays, so it is the 4th.
        assertTrue(LocalDate.of(2026, 9, 25).isLastWeekdayOfMonth())
        assertFalse(LocalDate.of(2026, 9, 18).isLastWeekdayOfMonth())
        // July 2026 has five Fridays; the 31st is the last.
        assertTrue(LocalDate.of(2026, 7, 31).isLastWeekdayOfMonth())
        assertFalse(LocalDate.of(2026, 7, 24).isLastWeekdayOfMonth())
    }

    @Test
    fun `weekday detection excludes the weekend`() {
        assertTrue(LocalDate.of(2026, 9, 21).isWeekday())  // Monday
        assertFalse(LocalDate.of(2026, 9, 26).isWeekday()) // Saturday
        assertFalse(LocalDate.of(2026, 9, 27).isWeekday()) // Sunday
    }

    @Test
    fun `minutes of day is monotonic across the day`() {
        assertEquals(0, LocalTime.MIDNIGHT.minutesOfDay())
        assertEquals(570, LocalTime.of(9, 30).minutesOfDay())
        assertEquals(1439, LocalTime.of(23, 59).minutesOfDay())
    }

    @Test
    fun `twelve hour formatting handles noon and midnight`() {
        assertEquals("12 AM", LocalTime.MIDNIGHT.formatHourMinute(use24Hour = false))
        assertEquals("12 PM", LocalTime.NOON.formatHourMinute(use24Hour = false))
        assertEquals("7:05 PM", LocalTime.of(19, 5).formatHourMinute(use24Hour = false))
        assertEquals("19:05", LocalTime.of(19, 5).formatHourMinute(use24Hour = true))
    }

    @Test
    fun `clock crossing midnight moves to the next calendar day`() {
        val clock = MutableCadenceClock(
            LocalDateTime.of(2026, 9, 20, 23, 59),
            ZoneId.of("Europe/London"),
        )
        assertEquals(LocalDate.of(2026, 9, 20), clock.today())
        clock.advanceBy(Duration.ofMinutes(2))
        assertEquals(LocalDate.of(2026, 9, 21), clock.today())
    }

    @Test
    fun `changing time zone can change today without time passing`() {
        // 23:30 in London on the 20th is already the 21st in Tokyo. A habit's "today" has to
        // follow the device zone, which is exactly why the clock re-reads it every call.
        val clock = MutableCadenceClock(
            LocalDateTime.of(2026, 9, 20, 23, 30),
            ZoneId.of("Europe/London"),
        )
        assertEquals(LocalDate.of(2026, 9, 20), clock.today())
        clock.setZone(ZoneId.of("Asia/Tokyo"))
        assertEquals(LocalDate.of(2026, 9, 21), clock.today())
    }

    @Test
    fun `spring forward does not skip a calendar day`() {
        // US DST 2026: clocks jump 02:00 to 03:00 on 8 March. The date must still advance once.
        val clock = MutableCadenceClock(
            LocalDateTime.of(2026, 3, 7, 23, 0),
            ZoneId.of("America/New_York"),
        )
        assertEquals(LocalDate.of(2026, 3, 7), clock.today())
        clock.advanceBy(Duration.ofHours(24))
        // 24 absolute hours across a spring-forward lands at 00:00 on the 9th, not the 8th.
        assertEquals(LocalDate.of(2026, 3, 9), clock.today())
    }
}
