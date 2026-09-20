package dev.achyutem.cadence

import dev.achyutem.cadence.core.database.entity.DailyCheckInEntity
import dev.achyutem.cadence.core.database.entity.HabitEntryEntity
import dev.achyutem.cadence.core.database.entity.TaskEntity
import dev.achyutem.cadence.domain.insights.InsightConfidence
import dev.achyutem.cadence.domain.insights.InsightsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The insights engine.
 *
 * The tests that matter here are the ones that assert the engine says **nothing**. Producing a
 * confident claim off thin data is the specific failure mode this feature exists to avoid, so
 * silence on small or noisy samples is the behaviour under test, not an edge case.
 */
class InsightsEngineTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val epoch: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private var nextId = 1L

    private fun task(
        date: LocalDate,
        completed: Boolean,
        completedAtHour: Int? = null,
    ) = TaskEntity(
        id = nextId++,
        title = "t",
        scheduledDate = date,
        completed = completed,
        completedAt = completedAtHour?.let { date.atTime(it, 0).atZone(zone).toInstant() },
        createdAt = epoch,
        updatedAt = epoch,
    )

    private fun analyse(
        tasks: List<TaskEntity>,
        habits: List<HabitEntryEntity> = emptyList(),
        checkIns: List<DailyCheckInEntity> = emptyList(),
        from: LocalDate = LocalDate.parse("2026-01-01"),
        to: LocalDate = LocalDate.parse("2026-12-31"),
    ) = InsightsEngine.analyse(tasks, habits, checkIns, from, to, zone)

    // --- Silence ---

    @Test
    fun `no data produces nothing`() {
        assertTrue(analyse(emptyList()).isEmpty())
    }

    @Test
    fun `below the minimum number of days nothing is reported`() {
        // 13 days of perfect data still says nothing. This is the rule that stops the engine
        // inventing a personality for someone in their first fortnight.
        val tasks = (1..13).flatMap { day ->
            val date = LocalDate.parse("2026-01-01").plusDays(day.toLong())
            listOf(task(date, completed = true), task(date, completed = false))
        }
        assertTrue(analyse(tasks).isEmpty())
    }

    @Test
    fun `uniform data produces no weekday pattern`() {
        // Every day identical: there is no difference to find, so nothing must be claimed.
        val tasks = (0..59).flatMap { day ->
            val date = LocalDate.parse("2026-01-05").plusDays(day.toLong())
            listOf(task(date, completed = true), task(date, completed = false))
        }
        val weekday = analyse(tasks).firstOrNull { it.id == "weekday" }
        assertEquals(null, weekday)
    }

    @Test
    fun `a difference smaller than the effect size is not reported`() {
        // Mondays 60%, other days 55%: a real difference in the data, below the noise floor.
        val tasks = (0..83).flatMap { day ->
            val date = LocalDate.parse("2026-01-05").plusDays(day.toLong())
            val target = if (date.dayOfWeek == DayOfWeek.MONDAY) 6 else 5
            (1..10).map { index -> task(date, completed = index <= target) }
        }
        assertEquals(null, analyse(tasks).firstOrNull { it.id == "weekday" })
    }

    // --- Weekday ---

    @Test
    fun `a large weekday difference is reported as a tendency`() {
        val tasks = (0..83).flatMap { day ->
            val date = LocalDate.parse("2026-01-05").plusDays(day.toLong())
            val target = if (date.dayOfWeek == DayOfWeek.TUESDAY) 9 else 3
            (1..10).map { index -> task(date, completed = index <= target) }
        }
        val insight = analyse(tasks).firstOrNull { it.id == "weekday" }
        assertTrue("expected a weekday insight", insight != null)
        assertEquals(InsightConfidence.PATTERN, insight!!.confidence)
        assertTrue("should be worded as a tendency: ${insight.text}", insight.text.contains("tend"))
        assertTrue(insight.text.contains("Tuesday"))
        assertTrue("must carry its basis", insight.basis.isNotBlank())
    }

    @Test
    fun `no insight ever claims causation`() {
        val tasks = (0..83).flatMap { day ->
            val date = LocalDate.parse("2026-01-05").plusDays(day.toLong())
            val target = if (date.dayOfWeek == DayOfWeek.TUESDAY) 9 else 3
            (1..10).map { index -> task(date, completed = index <= target) }
        }
        val habits = (0..40).map { day ->
            HabitEntryEntity(
                habitId = 1,
                date = LocalDate.parse("2026-01-05").plusDays(day.toLong() * 2),
                value = 1.0,
                completed = true,
                createdAt = epoch,
                updatedAt = epoch,
            )
        }
        analyse(tasks, habits).forEach { insight ->
            listOf("because", "causes", "caused", "makes you", "leads to").forEach { word ->
                assertFalse(
                    "insight ${insight.id} claims causation: ${insight.text}",
                    insight.text.lowercase().contains(word),
                )
            }
        }
    }

    // --- Load ---

    @Test
    fun `overloading is reported when completion really does fall`() {
        val tasks = (0..59).flatMap { day ->
            val date = LocalDate.parse("2026-01-05").plusDays(day.toLong())
            if (day % 2 == 0) {
                // Light day: 2 tasks, both done.
                listOf(task(date, true), task(date, true))
            } else {
                // Heavy day: 10 tasks, 2 done.
                (1..10).map { index -> task(date, completed = index <= 2) }
            }
        }
        val insight = analyse(tasks).firstOrNull { it.id == "load" }
        assertTrue("expected a load insight", insight != null)
        assertTrue(insight!!.text.contains("tend"))
    }

    // --- Time of day ---

    @Test
    fun `a clear time-of-day concentration is an observation, not a pattern`() {
        val tasks = (0..29).map { day ->
            task(LocalDate.parse("2026-01-05").plusDays(day.toLong()), completed = true, completedAtHour = 20)
        }
        val insight = analyse(tasks).firstOrNull { it.id == "time-of-day" }
        assertTrue("expected a time-of-day insight", insight != null)
        assertEquals(InsightConfidence.OBSERVATION, insight!!.confidence)
        assertTrue(insight.text.contains("evening"))
    }

    @Test
    fun `a spread of completion times claims no concentration`() {
        val tasks = (0..29).map { day ->
            task(
                LocalDate.parse("2026-01-05").plusDays(day.toLong()),
                completed = true,
                completedAtHour = listOf(8, 14, 20, 23)[day % 4],
            )
        }
        assertEquals(null, analyse(tasks).firstOrNull { it.id == "time-of-day" })
    }

    // --- Check-ins ---

    @Test
    fun `energy is not compared without enough check-ins on both sides`() {
        val tasks = (0..59).flatMap { day ->
            val date = LocalDate.parse("2026-01-05").plusDays(day.toLong())
            listOf(task(date, completed = true), task(date, completed = false))
        }
        // Plenty of check-ins, but only two low-energy days.
        val checkIns = (0..29).map { day ->
            DailyCheckInEntity(
                id = day.toLong(),
                date = LocalDate.parse("2026-01-05").plusDays(day.toLong()),
                mood = 4,
                energy = if (day < 2) 1 else 5,
                createdAt = epoch,
                updatedAt = epoch,
            )
        }
        assertEquals(null, analyse(tasks, checkIns = checkIns).firstOrNull { it.id == "energy" })
    }

    @Test
    fun `every insight carries a basis the user can check`() {
        val tasks = (0..83).flatMap { day ->
            val date = LocalDate.parse("2026-01-05").plusDays(day.toLong())
            val target = if (date.dayOfWeek == DayOfWeek.TUESDAY) 9 else 3
            (1..10).map { index -> task(date, completed = index <= target, completedAtHour = 20) }
        }
        val insights = analyse(tasks)
        assertTrue(insights.isNotEmpty())
        insights.forEach { assertTrue("${it.id} has no basis", it.basis.isNotBlank()) }
    }
}
