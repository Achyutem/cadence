package dev.achyutem.cadence.core.time

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The single source of "now" for the whole application.
 *
 * Nothing in Cadence calls [LocalDate.now] or [System.currentTimeMillis] directly. Every
 * date-sensitive calculation — streaks, recurrence, the Today screen, reminder scheduling —
 * goes through this interface so it can be driven deterministically in tests across midnight,
 * DST transitions, leap days and year boundaries.
 *
 * The zone is read on every call rather than cached: the device time zone can change while the
 * process is alive (travel, DST, manual change) and a cached [ZoneId] would silently produce
 * off-by-one-day results.
 */
interface CadenceClock {
    fun zone(): ZoneId
    fun now(): Instant
    fun today(): LocalDate = now().atZone(zone()).toLocalDate()
    fun timeNow(): LocalTime = now().atZone(zone()).toLocalTime()
    fun dateTimeNow(): LocalDateTime = now().atZone(zone()).toLocalDateTime()
}

/** Production clock: the device's own wall clock and time zone. */
object SystemCadenceClock : CadenceClock {
    override fun zone(): ZoneId = ZoneId.systemDefault()
    override fun now(): Instant = Instant.now()
}

/**
 * Test clock. Mutable on purpose so a test can walk time forward across a boundary:
 *
 * ```
 * val clock = MutableCadenceClock(LocalDateTime.of(2026, 3, 8, 23, 59), ZoneId.of("America/New_York"))
 * clock.advanceBy(Duration.ofMinutes(2))   // crosses midnight *and* the DST spring-forward
 * ```
 */
class MutableCadenceClock(
    private var instant: Instant,
    private var zone: ZoneId = ZoneId.of("UTC"),
) : CadenceClock {

    constructor(dateTime: LocalDateTime, zone: ZoneId) : this(dateTime.atZone(zone).toInstant(), zone)

    override fun zone(): ZoneId = zone
    override fun now(): Instant = instant

    fun setTo(dateTime: LocalDateTime) {
        instant = dateTime.atZone(zone).toInstant()
    }

    fun setZone(newZone: ZoneId) {
        zone = newZone
    }

    fun advanceBy(amount: java.time.Duration) {
        instant = instant.plus(amount)
    }

    fun asJavaClock(): Clock = Clock.fixed(instant, zone)
}
