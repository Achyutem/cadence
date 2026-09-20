package dev.achyutem.cadence.domain.breathing

import kotlin.math.max

/**
 * What the body is doing during one phase of an exercise.
 *
 * [HOLD_FULL] and [HOLD_EMPTY] are separate because they are different skills and different
 * sensations; a hold on full lungs is a CO2 tolerance exercise, a hold on empty lungs is much
 * more aggressive, and because the UI animates them differently.
 */
enum class BreathPhaseKind {
    PREPARE,
    INHALE,
    HOLD_FULL,
    EXHALE,
    HOLD_EMPTY,
    /** Free breathing between efforts, in a table. */
    RECOVER,
}

/** One timed segment of a session. Sessions are a flat list of these; see [BreathingExercise]. */
data class BreathPhase(
    val kind: BreathPhaseKind,
    val seconds: Int,
    /** 1-based, for "Round 3 of 8". 0 during a lead-in that belongs to no round. */
    val round: Int = 0,
    val totalRounds: Int = 0,
)

/**
 * One adjustable number on an exercise.
 *
 * The bounds and the step live here rather than in the editor so that there is exactly one
 * definition of "a CO2 rest decrement is 0 to 60 seconds in steps of 5". The editor renders
 * whatever fields an exercise declares, which is what keeps a fifth exercise from needing a
 * fifth settings screen.
 */
enum class BreathField(
    val min: Int,
    val max: Int,
    val step: Int,
    /** Rendered as `m:ss` when true, as a plain number when false. */
    val isDuration: Boolean,
) {
    ROUNDS(1, BreathingExercise.MAX_ROUNDS, 1, isDuration = false),
    BOX_SECONDS(2, 20, 1, isDuration = true),
    BREATHE_UP(30, 600, 15, isDuration = true),
    HOLD(10, 600, 5, isDuration = true),
    START_HOLD(10, 600, 5, isDuration = true),
    HOLD_INCREMENT(0, 60, 5, isDuration = true),
    REST(30, 600, 15, isDuration = true),
    START_REST(30, 600, 15, isDuration = true),
    REST_DECREMENT(0, 60, 5, isDuration = true),
    ;

    fun clamp(value: Int): Int = value.coerceIn(min, max)
}

/**
 * The four exercises.
 *
 * Each one is a **pure function to a flat list of phases**. Nothing here knows about time passing,
 * coroutines or the screen; the runner just walks the list. That is what makes an exercise
 * completely unit-testable; a CO2 table is correct or not before anything is ever rendered, and
 * it is why adding a fifth exercise means adding one `expand()` and nothing else.
 */
sealed interface BreathingExercise {
    val title: String
    val subtitle: String

    /** The numbers this exercise lets you change, in the order the editor should show them. */
    val fields: List<BreathField>

    /** The current value of [field]. Only fields in [fields] are meaningful. */
    fun valueOf(field: BreathField): Int

    /** A copy with [field] set to [value], clamped to the field's bounds. */
    fun with(field: BreathField, value: Int): BreathingExercise

    /** The full session, in order. */
    fun expand(): List<BreathPhase>

    /**
     * Box breathing: equal inhale, hold, exhale, hold.
     *
     * The calm one. Used to settle rather than to train tolerance, so it has no recovery phases
     * and no progression, every round is identical.
     */
    data class Box(
        val seconds: Int = 4,
        val rounds: Int = 8,
    ) : BreathingExercise {
        override val title = "Box breathing"
        override val subtitle = "$seconds · $seconds · $seconds · $seconds"

        override val fields = listOf(BreathField.BOX_SECONDS, BreathField.ROUNDS)

        override fun valueOf(field: BreathField): Int = when (field) {
            BreathField.BOX_SECONDS -> seconds
            BreathField.ROUNDS -> rounds
            else -> 0
        }

        override fun with(field: BreathField, value: Int): BreathingExercise = when (field) {
            BreathField.BOX_SECONDS -> copy(seconds = field.clamp(value))
            BreathField.ROUNDS -> copy(rounds = field.clamp(value))
            else -> this
        }

        override fun expand(): List<BreathPhase> {
            val n = BreathField.ROUNDS.clamp(rounds)
            val s = BreathField.BOX_SECONDS.clamp(seconds)
            return buildList {
                add(BreathPhase(BreathPhaseKind.PREPARE, PREPARE_SECONDS))
                repeat(n) { index ->
                    val round = index + 1
                    add(BreathPhase(BreathPhaseKind.INHALE, s, round, n))
                    add(BreathPhase(BreathPhaseKind.HOLD_FULL, s, round, n))
                    add(BreathPhase(BreathPhaseKind.EXHALE, s, round, n))
                    add(BreathPhase(BreathPhaseKind.HOLD_EMPTY, s, round, n))
                }
            }
        }
    }

    /**
     * A single static hold, with a breathe-up before it.
     *
     * One round by design. Repeated maximal holds are what the tables are for; stacking them
     * freehand is how people get hurt.
     */
    data class StaticApnea(
        val breatheUpSeconds: Int = 120,
        val holdSeconds: Int = 120,
    ) : BreathingExercise {
        override val title = "Static apnea"
        override val subtitle = "Hold ${holdSeconds.asClock()}"

        override val fields = listOf(BreathField.BREATHE_UP, BreathField.HOLD)

        override fun valueOf(field: BreathField): Int = when (field) {
            BreathField.BREATHE_UP -> breatheUpSeconds
            BreathField.HOLD -> holdSeconds
            else -> 0
        }

        override fun with(field: BreathField, value: Int): BreathingExercise = when (field) {
            BreathField.BREATHE_UP -> copy(breatheUpSeconds = field.clamp(value))
            BreathField.HOLD -> copy(holdSeconds = field.clamp(value))
            else -> this
        }

        override fun expand(): List<BreathPhase> = listOf(
            BreathPhase(BreathPhaseKind.PREPARE, PREPARE_SECONDS),
            BreathPhase(BreathPhaseKind.RECOVER, BreathField.BREATHE_UP.clamp(breatheUpSeconds), 1, 1),
            BreathPhase(BreathPhaseKind.INHALE, INHALE_SECONDS, 1, 1),
            BreathPhase(BreathPhaseKind.HOLD_FULL, BreathField.HOLD.clamp(holdSeconds), 1, 1),
            BreathPhase(BreathPhaseKind.EXHALE, RECOVERY_EXHALE_SECONDS, 1, 1),
        )
    }

    /**
     * CO2 tolerance table: the hold stays the same, the rest **shrinks** each round.
     *
     * CO2 builds up because there is less and less time to clear it, so the urge to breathe
     * arrives earlier and earlier at a hold length that never changes. Rest is floored at
     * [MIN_REST_SECONDS] rather than being allowed to reach zero.
     */
    data class Co2Table(
        val holdSeconds: Int = 60,
        val startRestSeconds: Int = 120,
        val restDecrementSeconds: Int = 15,
        val rounds: Int = 8,
    ) : BreathingExercise {
        override val title = "CO₂ table"
        override val subtitle = "$rounds × ${holdSeconds.asClock()} hold, rest −${restDecrementSeconds}s each round"

        override val fields = listOf(
            BreathField.ROUNDS,
            BreathField.HOLD,
            BreathField.START_REST,
            BreathField.REST_DECREMENT,
        )

        override fun valueOf(field: BreathField): Int = when (field) {
            BreathField.ROUNDS -> rounds
            BreathField.HOLD -> holdSeconds
            BreathField.START_REST -> startRestSeconds
            BreathField.REST_DECREMENT -> restDecrementSeconds
            else -> 0
        }

        override fun with(field: BreathField, value: Int): BreathingExercise = when (field) {
            BreathField.ROUNDS -> copy(rounds = field.clamp(value))
            BreathField.HOLD -> copy(holdSeconds = field.clamp(value))
            BreathField.START_REST -> copy(startRestSeconds = field.clamp(value))
            BreathField.REST_DECREMENT -> copy(restDecrementSeconds = field.clamp(value))
            else -> this
        }

        override fun expand(): List<BreathPhase> {
            val n = BreathField.ROUNDS.clamp(rounds)
            val hold = BreathField.HOLD.clamp(holdSeconds)
            return buildList {
                add(BreathPhase(BreathPhaseKind.PREPARE, PREPARE_SECONDS))
                repeat(n) { index ->
                    val round = index + 1
                    val rest = max(
                        MIN_REST_SECONDS,
                        startRestSeconds - restDecrementSeconds * index,
                    )
                    // Rest comes first: the table is a recovery/hold cycle, and starting on a
                    // hold would skip the breathe-up before the first effort.
                    add(BreathPhase(BreathPhaseKind.RECOVER, rest, round, n))
                    add(BreathPhase(BreathPhaseKind.HOLD_FULL, hold, round, n))
                }
            }
        }
    }

    /**
     * O2 tolerance table: the rest stays the same, the hold **grows** each round.
     *
     * The opposite stress; the body has to work with less oxygen each time, from a constant
     * recovery. This is the more demanding of the two tables.
     */
    data class O2Table(
        val restSeconds: Int = 120,
        val startHoldSeconds: Int = 60,
        val holdIncrementSeconds: Int = 15,
        val rounds: Int = 8,
    ) : BreathingExercise {
        override val title = "O₂ table"
        override val subtitle =
            "$rounds holds, ${startHoldSeconds.asClock()} growing to ${finalHoldSeconds().asClock()}"

        override val fields = listOf(
            BreathField.ROUNDS,
            BreathField.START_HOLD,
            BreathField.HOLD_INCREMENT,
            BreathField.REST,
        )

        override fun valueOf(field: BreathField): Int = when (field) {
            BreathField.ROUNDS -> rounds
            BreathField.START_HOLD -> startHoldSeconds
            BreathField.HOLD_INCREMENT -> holdIncrementSeconds
            BreathField.REST -> restSeconds
            else -> 0
        }

        override fun with(field: BreathField, value: Int): BreathingExercise = when (field) {
            BreathField.ROUNDS -> copy(rounds = field.clamp(value))
            BreathField.START_HOLD -> copy(startHoldSeconds = field.clamp(value))
            BreathField.HOLD_INCREMENT -> copy(holdIncrementSeconds = field.clamp(value))
            BreathField.REST -> copy(restSeconds = field.clamp(value))
            else -> this
        }

        /** The last round's hold, which is what the subtitle promises and the body has to do. */
        fun finalHoldSeconds(): Int {
            val n = BreathField.ROUNDS.clamp(rounds)
            return holdAt(n - 1)
        }

        private fun holdAt(index: Int): Int =
            (startHoldSeconds + holdIncrementSeconds * index).coerceAtMost(BreathField.HOLD.max)

        override fun expand(): List<BreathPhase> {
            val n = BreathField.ROUNDS.clamp(rounds)
            val rest = BreathField.REST.clamp(restSeconds)
            return buildList {
                add(BreathPhase(BreathPhaseKind.PREPARE, PREPARE_SECONDS))
                repeat(n) { index ->
                    val round = index + 1
                    val hold = holdAt(index)
                    add(BreathPhase(BreathPhaseKind.RECOVER, rest, round, n))
                    add(BreathPhase(BreathPhaseKind.HOLD_FULL, hold, round, n))
                }
            }
        }
    }

    companion object {
        /** A moment to put the phone down and settle before anything is timed. */
        const val PREPARE_SECONDS = 5
        const val INHALE_SECONDS = 4
        const val RECOVERY_EXHALE_SECONDS = 8

        /**
         * Rest never reaches zero in a CO2 table.
         *
         * A table whose rest decrements to nothing stops being a training table and becomes a
         * continuous breath-hold, which is both useless as a stimulus and the single most
         * dangerous thing this screen could generate.
         */
        const val MIN_REST_SECONDS = 15

        const val MAX_ROUNDS = 20
    }
}

/** Total session length, for the summary on the card before you start it. */
fun BreathingExercise.totalSeconds(): Int = expand().sumOf { it.seconds }

/** `95` → `1:35`, `60` → `1:00`, `45` → `0:45`. */
fun Int.asClock(): String {
    val safe = coerceAtLeast(0)
    return "${safe / 60}:${(safe % 60).toString().padStart(2, '0')}"
}
