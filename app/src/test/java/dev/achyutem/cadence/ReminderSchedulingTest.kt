package dev.achyutem.cadence

import dev.achyutem.cadence.core.database.entity.ReminderEntity
import dev.achyutem.cadence.core.notifications.ReminderScheduling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Reminder timing.
 *
 * The cases here are the ones that make reminders arrive at the wrong hour in real life: daylight
 * saving, travel, and quiet hours that wrap midnight. All of them are why a reminder is stored as
 * a local time rather than as an instant.
 */
class ReminderSchedulingTest {

    private val london = ZoneId.of("Europe/London")
    private val newYork = ZoneId.of("America/New_York")

    private fun reminder(time: String = "09:00", lead: Int = 0, enabled: Boolean = true) =
        ReminderEntity(timeOfDay = LocalTime.parse(time), leadMinutes = lead, enabled = enabled)

    private fun d(v: String) = LocalDate.parse(v)

    @Test
    fun `an all-day reminder fires at its own time of day`() {
        val instant = ReminderScheduling.triggerInstant(
            reminder("09:00"), d("2026-09-21"), startTime = null, zone = london,
        )!!
        assertEquals(LocalTime.of(9, 0), instant.atZone(london).toLocalTime())
    }

    @Test
    fun `a timed task fires ahead of its start by the lead`() {
        val instant = ReminderScheduling.triggerInstant(
            reminder(lead = 15), d("2026-09-21"), startTime = LocalTime.of(11, 0), zone = london,
        )!!
        assertEquals(LocalTime.of(10, 45), instant.atZone(london).toLocalTime())
    }

    @Test
    fun `a disabled reminder schedules nothing`() {
        assertNull(
            ReminderScheduling.triggerInstant(
                reminder(enabled = false), d("2026-09-21"), null, london,
            )
        )
    }

    @Test
    fun `the local time is preserved across a daylight saving change`() {
        // The reason reminders are not stored as instants: a fixed instant for "09:00 daily"
        // arrives an hour out once the clocks move.
        val before = ReminderScheduling.triggerInstant(
            reminder("09:00"), d("2026-10-20"), null, london,
        )!!
        val after = ReminderScheduling.triggerInstant(
            reminder("09:00"), d("2026-11-10"), null, london,
        )!!
        assertEquals(LocalTime.of(9, 0), before.atZone(london).toLocalTime())
        assertEquals(LocalTime.of(9, 0), after.atZone(london).toLocalTime())
        // Same wall clock, and genuinely different offsets either side of the change.
        assertTrue(before.atZone(london).offset != after.atZone(london).offset)
    }

    @Test
    fun `a local time that does not exist in a DST gap resolves forward`() {
        // US spring forward 2026: 02:00 to 03:00 never happens on 8 March.
        val instant = ReminderScheduling.triggerInstant(
            reminder("02:30"), d("2026-03-08"), null, newYork,
        )!!
        val local = instant.atZone(newYork).toLocalTime()
        assertTrue("resolved to $local", local >= LocalTime.of(3, 0))
    }

    @Test
    fun `the same reminder follows the device zone`() {
        val inLondon = ReminderScheduling.triggerInstant(reminder("09:00"), d("2026-09-21"), null, london)!!
        val inNewYork = ReminderScheduling.triggerInstant(reminder("09:00"), d("2026-09-21"), null, newYork)!!
        assertEquals(LocalTime.of(9, 0), inLondon.atZone(london).toLocalTime())
        assertEquals(LocalTime.of(9, 0), inNewYork.atZone(newYork).toLocalTime())
        assertTrue(inNewYork.isAfter(inLondon))
    }

    // --- Quiet hours ---

    @Test
    fun `a reminder outside quiet hours is untouched`() {
        val trigger = d("2026-09-21").atTime(9, 0).atZone(london).toInstant()
        val shifted = ReminderScheduling.applyQuietHours(
            trigger, london, enabled = true, start = LocalTime.of(22, 0), end = LocalTime.of(7, 0),
        )
        assertEquals(trigger, shifted)
    }

    @Test
    fun `a late-night reminder is deferred to the end of quiet hours, not dropped`() {
        val trigger = d("2026-09-21").atTime(23, 30).atZone(london).toInstant()
        val shifted = ReminderScheduling.applyQuietHours(
            trigger, london, enabled = true, start = LocalTime.of(22, 0), end = LocalTime.of(7, 0),
        )
        val local = shifted.atZone(london)
        assertEquals(LocalTime.of(7, 0), local.toLocalTime())
        assertEquals(d("2026-09-22"), local.toLocalDate())
    }

    @Test
    fun `an early-morning reminder resumes the same day`() {
        val trigger = d("2026-09-21").atTime(3, 0).atZone(london).toInstant()
        val shifted = ReminderScheduling.applyQuietHours(
            trigger, london, enabled = true, start = LocalTime.of(22, 0), end = LocalTime.of(7, 0),
        )
        val local = shifted.atZone(london)
        assertEquals(LocalTime.of(7, 0), local.toLocalTime())
        assertEquals(d("2026-09-21"), local.toLocalDate())
    }

    @Test
    fun `a quiet window inside one day works too`() {
        val trigger = d("2026-09-21").atTime(14, 0).atZone(london).toInstant()
        val shifted = ReminderScheduling.applyQuietHours(
            trigger, london, enabled = true, start = LocalTime.of(13, 0), end = LocalTime.of(15, 0),
        )
        assertEquals(LocalTime.of(15, 0), shifted.atZone(london).toLocalTime())
    }

    @Test
    fun `quiet hours disabled changes nothing`() {
        val trigger = d("2026-09-21").atTime(23, 30).atZone(london).toInstant()
        assertEquals(
            trigger,
            ReminderScheduling.applyQuietHours(
                trigger, london, enabled = false,
                start = LocalTime.of(22, 0), end = LocalTime.of(7, 0),
            ),
        )
    }
}
