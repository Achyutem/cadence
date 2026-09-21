package dev.achyutem.cadence

import dev.achyutem.cadence.domain.breathing.BreathCue
import dev.achyutem.cadence.domain.breathing.BreathField
import dev.achyutem.cadence.domain.breathing.BreathPhaseKind
import dev.achyutem.cadence.domain.breathing.BreathingExercise
import dev.achyutem.cadence.domain.breathing.BreathingPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Configuring an exercise.
 *
 * The first test is the one that matters: it is the session a user described wanting, written out
 * as the numbers they would type, and it asserts that what comes back is three holds growing by
 * ten seconds with nothing to press in between.
 */
class BreathingSettingsTest {

    @Test
    fun `three rounds of one minute with a ten second increment runs 1_00 1_10 1_20`() {
        val exercise = BreathingExercise.O2Table(
            rounds = 3,
            startHoldSeconds = 60,
            holdIncrementSeconds = 10,
        )

        val holds = exercise.expand()
            .filter { it.kind == BreathPhaseKind.HOLD_FULL }
            .map { it.seconds }

        assertEquals(listOf(60, 70, 80), holds)
    }

    @Test
    fun `every round of a configured session is planned up front, so nothing needs a tap`() {
        val exercise = BreathingExercise.O2Table(
            rounds = 3,
            startHoldSeconds = 60,
            holdIncrementSeconds = 10,
        )
        val phases = exercise.expand()

        // Rest, hold, rest, hold, rest, hold, behind one lead-in. Walking this list start to
        // finish is the entire session; the runner never waits for input.
        assertEquals(1 + 3 * 2, phases.size)
        assertEquals(listOf(1, 1, 2, 2, 3, 3), phases.drop(1).map { it.round })
        assertTrue(phases.drop(1).all { it.totalRounds == 3 })
    }

    @Test
    fun `static apnea is rest and hold, with no invented breath in between`() {
        val phases = BreathingExercise.StaticApnea(rounds = 3).expand()

        // A lead-in, then two phases per round. An earlier version wedged a fixed four-second
        // inhale and an eight-second exhale around a single hold; both were the app deciding how
        // someone should breathe.
        assertEquals(1 + 3 * 2, phases.size)
        assertEquals(
            listOf(
                BreathPhaseKind.PREPARE,
                BreathPhaseKind.RECOVER, BreathPhaseKind.HOLD_FULL,
                BreathPhaseKind.RECOVER, BreathPhaseKind.HOLD_FULL,
                BreathPhaseKind.RECOVER, BreathPhaseKind.HOLD_FULL,
            ),
            phases.map { it.kind },
        )
    }

    @Test
    fun `static apnea holds can grow by a step you choose`() {
        // Three rounds of a minute, ten seconds more each time, written as the numbers a user
        // would type into the sheet.
        val exercise = BreathingExercise.StaticApnea(
            rounds = 3,
            holdSeconds = 60,
            holdIncrementSeconds = 10,
            breatheUpSeconds = 60,
        )

        val holds = exercise.expand()
            .filter { it.kind == BreathPhaseKind.HOLD_FULL }
            .map { it.seconds }

        assertEquals(listOf(60, 70, 80), holds)
        assertEquals(80, exercise.finalHoldSeconds())
    }

    @Test
    fun `a zero increment leaves every static apnea round the same`() {
        val holds = BreathingExercise.StaticApnea(rounds = 4, holdSeconds = 90).expand()
            .filter { it.kind == BreathPhaseKind.HOLD_FULL }
            .map { it.seconds }
        assertEquals(listOf(90, 90, 90, 90), holds)
    }

    @Test
    fun `static apnea rounds are the users to set`() {
        val five = BreathingExercise.StaticApnea().with(BreathField.ROUNDS, 5)
        assertEquals(5, five.valueOf(BreathField.ROUNDS))
        assertEquals(5, five.expand().count { it.kind == BreathPhaseKind.HOLD_FULL })
    }

    @Test
    fun `editing a field returns a new exercise with only that field changed`() {
        val before = BreathingExercise.Co2Table()
        val after = before.with(BreathField.ROUNDS, 12)

        assertEquals(12, after.valueOf(BreathField.ROUNDS))
        assertEquals(
            before.valueOf(BreathField.HOLD),
            after.valueOf(BreathField.HOLD),
        )
    }

    @Test
    fun `a field cannot be pushed past its own bounds`() {
        val tooMany = BreathingExercise.Box().with(BreathField.ROUNDS, 9_999)
        assertEquals(BreathingExercise.MAX_ROUNDS, tooMany.valueOf(BreathField.ROUNDS))

        val tooFew = BreathingExercise.Box().with(BreathField.ROUNDS, -4)
        assertEquals(1, tooFew.valueOf(BreathField.ROUNDS))
    }

    @Test
    fun `every field an exercise declares can be read and written`() {
        for (exercise in BreathingPreferences.Default.exercises()) {
            for (field in exercise.fields) {
                val bumped = exercise.with(field, exercise.valueOf(field) + field.step)
                assertEquals(
                    "${exercise.title} did not keep a change to $field",
                    field.clamp(exercise.valueOf(field) + field.step),
                    bumped.valueOf(field),
                )
            }
        }
    }

    @Test
    fun `settings survive a round trip through preferences`() {
        val edited = BreathingExercise.O2Table(
            rounds = 3,
            startHoldSeconds = 60,
            holdIncrementSeconds = 10,
            restSeconds = 90,
        )

        val restored = BreathingPreferences.Default.with(edited).exercises()

        assertTrue(restored.contains(edited))
        // The other three are untouched by editing one.
        assertTrue(restored.contains(BreathingExercise.Box()))
        assertTrue(restored.contains(BreathingExercise.Co2Table()))
    }

    /**
     * The defaults are written out twice, once on the exercises and once on the preferences, so
     * that the two layers stay independent. This is the test that keeps the copies honest.
     */
    @Test
    fun `preference defaults match the exercises own defaults`() {
        assertEquals(
            listOf(
                BreathingExercise.Box(),
                BreathingExercise.StaticApnea(),
                BreathingExercise.Co2Table(),
                BreathingExercise.O2Table(),
            ),
            BreathingPreferences.Default.exercises(),
        )
    }

    @Test
    fun `a phase change always has a cue, and holds on full and empty lungs sound different`() {
        val cues = BreathPhaseKind.entries.map(BreathCue::forPhase)
        assertEquals(BreathPhaseKind.entries.size, cues.distinct().size)
    }

    @Test
    fun `a short phase is too short to count down`() {
        // A four-second box inhale ticking three times is a metronome, not a countdown.
        assertTrue(BreathingExercise.Box().valueOf(BreathField.BOX_SECONDS) <
            BreathCue.MIN_COUNTDOWN_PHASE_SECONDS)
        assertTrue(BreathCue.MIN_COUNTDOWN_PHASE_SECONDS > BreathCue.COUNTDOWN_SECONDS)
    }
}
