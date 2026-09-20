package dev.achyutem.cadence

import dev.achyutem.cadence.core.database.converter.CadenceTypeConverters as C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * The storage format is a contract: rows written by version 1 must still parse in version 12.
 * These tests pin the exact on-disk representation, not just round-trip behaviour, a change that
 * silently switched dates to epoch-day integers would round-trip perfectly and corrupt every
 * existing database.
 */
class CadenceTypeConvertersTest {

    @Test
    fun `local date is stored as ISO text`() {
        assertEquals("2026-09-20", C.localDateToString(LocalDate.of(2026, 9, 20)))
        assertEquals("2026-01-05", C.localDateToString(LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun `ISO date text sorts chronologically`() {
        // This is why dates are text: BETWEEN and ORDER BY in SQL rely on it.
        val dates = listOf("2026-01-05", "2025-12-31", "2026-09-20", "2026-01-10")
        assertEquals(
            listOf("2025-12-31", "2026-01-05", "2026-01-10", "2026-09-20"),
            dates.sorted(),
        )
    }

    @Test
    fun `local date round trips`() {
        val date = LocalDate.of(2024, 2, 29) // leap day
        assertEquals(date, C.stringToLocalDate(C.localDateToString(date)))
    }

    @Test
    fun `local time round trips at boundaries`() {
        listOf(LocalTime.MIDNIGHT, LocalTime.of(0, 1), LocalTime.of(23, 59), LocalTime.NOON)
            .forEach { time ->
                assertEquals(time, C.stringToLocalTime(C.localTimeToString(time)))
            }
    }

    @Test
    fun `midnight is stored unambiguously`() {
        assertEquals("00:00", C.localTimeToString(LocalTime.MIDNIGHT))
    }

    @Test
    fun `instant round trips as epoch millis`() {
        val instant = Instant.ofEpochMilli(1_789_056_000_000)
        assertEquals(1_789_056_000_000L, C.instantToLong(instant))
        assertEquals(instant, C.longToInstant(C.instantToLong(instant)))
    }

    @Test
    fun `days of week are stored sorted so equal sets compare equal as text`() {
        val friFirst = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        val monFirst = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        assertEquals("1,3,5", C.daysOfWeekToString(friFirst))
        assertEquals(C.daysOfWeekToString(monFirst), C.daysOfWeekToString(friFirst))
    }

    @Test
    fun `days of week round trip`() {
        val days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        assertEquals(days, C.stringToDaysOfWeek(C.daysOfWeekToString(days)))
    }

    @Test
    fun `empty day set decodes to empty rather than throwing`() {
        assertTrue(C.stringToDaysOfWeek("")!!.isEmpty())
    }

    @Test
    fun `nulls pass through in both directions`() {
        assertNull(C.localDateToString(null))
        assertNull(C.stringToLocalDate(null))
        assertNull(C.localTimeToString(null))
        assertNull(C.stringToLocalTime(null))
        assertNull(C.instantToLong(null))
        assertNull(C.longToInstant(null))
        assertNull(C.daysOfWeekToString(null))
        assertNull(C.stringToDaysOfWeek(null))
    }
}
