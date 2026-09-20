package dev.achyutem.cadence

import dev.achyutem.cadence.domain.breathing.BreathPhaseKind
import dev.achyutem.cadence.domain.breathing.BreathingExercise
import dev.achyutem.cadence.domain.breathing.asClock
import dev.achyutem.cadence.domain.breathing.totalSeconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The exercises are pure functions to a list of phases, so a table is provably correct before
 * anything is ever rendered. The cases that matter are the progressions, a table whose rest or
 * hold moves the wrong way is both useless as training and, for a CO2 table, unsafe.
 */
class BreathingExerciseTest {

    @Test
    fun `box breathing repeats an identical four-phase round`() {
        val phases = BreathingExercise.Box(seconds = 4, rounds = 3).expand()
        // 1 prepare + 3 rounds x 4 phases
        assertEquals(13, phases.size)
        val round = phases.drop(1).take(4)
        assertEquals(
            listOf(
                BreathPhaseKind.INHALE,
                BreathPhaseKind.HOLD_FULL,
                BreathPhaseKind.EXHALE,
                BreathPhaseKind.HOLD_EMPTY,
            ),
            round.map { it.kind },
        )
        assertTrue(round.all { it.seconds == 4 })
    }

    @Test
    fun `box breathing rounds are numbered for the display`() {
        val phases = BreathingExercise.Box(rounds = 3).expand().filter { it.round > 0 }
        assertEquals(1, phases.first().round)
        assertEquals(3, phases.last().round)
        assertTrue(phases.all { it.totalRounds == 3 })
    }

    @Test
    fun `static apnea is a single hold with a breathe-up before it`() {
        val phases = BreathingExercise.StaticApnea(breatheUpSeconds = 90, holdSeconds = 150).expand()
        val holds = phases.filter { it.kind == BreathPhaseKind.HOLD_FULL }
        assertEquals(1, holds.size)
        assertEquals(150, holds.single().seconds)
        assertEquals(90, phases.first { it.kind == BreathPhaseKind.RECOVER }.seconds)
    }

    @Test
    fun `CO2 table keeps the hold constant and shrinks the rest`() {
        val phases = BreathingExercise.Co2Table(
            holdSeconds = 60, startRestSeconds = 120, restDecrementSeconds = 15, rounds = 4,
        ).expand()

        val holds = phases.filter { it.kind == BreathPhaseKind.HOLD_FULL }.map { it.seconds }
        val rests = phases.filter { it.kind == BreathPhaseKind.RECOVER }.map { it.seconds }

        assertEquals(listOf(60, 60, 60, 60), holds)
        assertEquals(listOf(120, 105, 90, 75), rests)
    }

    @Test
    fun `CO2 table rest never falls below the floor`() {
        // The safety property: a table that decrements to zero becomes one continuous breath-hold.
        val phases = BreathingExercise.Co2Table(
            holdSeconds = 60, startRestSeconds = 60, restDecrementSeconds = 30, rounds = 8,
        ).expand()
        val rests = phases.filter { it.kind == BreathPhaseKind.RECOVER }.map { it.seconds }
        assertTrue(
            "rest dropped below the floor: $rests",
            rests.all { it >= BreathingExercise.MIN_REST_SECONDS },
        )
    }

    @Test
    fun `CO2 table starts with rest, not with a hold`() {
        // Starting on a hold would skip the breathe-up before the first effort.
        val phases = BreathingExercise.Co2Table(rounds = 2).expand()
        assertEquals(BreathPhaseKind.PREPARE, phases[0].kind)
        assertEquals(BreathPhaseKind.RECOVER, phases[1].kind)
        assertEquals(BreathPhaseKind.HOLD_FULL, phases[2].kind)
    }

    @Test
    fun `O2 table keeps the rest constant and grows the hold`() {
        val phases = BreathingExercise.O2Table(
            restSeconds = 120, startHoldSeconds = 60, holdIncrementSeconds = 15, rounds = 4,
        ).expand()

        val holds = phases.filter { it.kind == BreathPhaseKind.HOLD_FULL }.map { it.seconds }
        val rests = phases.filter { it.kind == BreathPhaseKind.RECOVER }.map { it.seconds }

        assertEquals(listOf(60, 75, 90, 105), holds)
        assertEquals(listOf(120, 120, 120, 120), rests)
    }

    @Test
    fun `O2 table holds are capped so a long table cannot generate an absurd target`() {
        val phases = BreathingExercise.O2Table(
            startHoldSeconds = 300, holdIncrementSeconds = 120, rounds = 20,
        ).expand()
        val holds = phases.filter { it.kind == BreathPhaseKind.HOLD_FULL }.map { it.seconds }
        assertTrue(holds.all { it <= 900 })
    }

    @Test
    fun `round counts are clamped to something a person could finish`() {
        val phases = BreathingExercise.Box(rounds = 500).expand().filter { it.round > 0 }
        assertEquals(BreathingExercise.MAX_ROUNDS, phases.maxOf { it.totalRounds })
    }

    @Test
    fun `a zero or negative round count still produces one round`() {
        assertTrue(BreathingExercise.Box(rounds = 0).expand().any { it.round == 1 })
    }

    @Test
    fun `total duration is the sum of the phases`() {
        val exercise = BreathingExercise.Box(seconds = 4, rounds = 2)
        assertEquals(
            BreathingExercise.PREPARE_SECONDS + 2 * 4 * 4,
            exercise.totalSeconds(),
        )
    }

    @Test
    fun `clock formatting pads seconds`() {
        assertEquals("0:45", 45.asClock())
        assertEquals("1:00", 60.asClock())
        assertEquals("1:35", 95.asClock())
        assertEquals("10:05", 605.asClock())
        assertEquals("0:00", (-5).asClock())
    }
}
