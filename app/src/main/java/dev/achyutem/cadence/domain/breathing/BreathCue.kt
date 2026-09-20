package dev.achyutem.cadence.domain.breathing

/**
 * A sound the session wants to make.
 *
 * The runner emits these; something on the Android side turns them into tones. Keeping the cue
 * as a plain enum is what lets the whole session, including exactly when it beeps, be asserted in
 * a unit test with no audio hardware anywhere near it.
 *
 * A cue is emitted at the *start* of every phase, which is the moment the user has to act on. The
 * point of the sound is that you can run a table with the phone face down and your eyes shut.
 */
enum class BreathCue {
    /** Settle. The lead-in before anything is timed. */
    PREPARE,
    INHALE,
    /** Hold on full lungs. */
    HOLD,
    EXHALE,
    /** Hold on empty lungs. Distinct from [HOLD] because it is a much more aggressive phase. */
    HOLD_EMPTY,
    /** Breathe freely. */
    RECOVER,

    /** One of the last few seconds of a phase. Quiet, so it does not startle mid-hold. */
    COUNTDOWN,

    /** The session is over. */
    FINISH,
    ;

    companion object {
        /** How many seconds of countdown ticks a phase gets before it ends. */
        const val COUNTDOWN_SECONDS = 3

        /**
         * Phases shorter than this get no countdown.
         *
         * A four-second box inhale that ticks for three of its four seconds is not a countdown,
         * it is a metronome, and it drowns out the phase change that actually matters.
         */
        const val MIN_COUNTDOWN_PHASE_SECONDS = 8

        fun forPhase(kind: BreathPhaseKind): BreathCue = when (kind) {
            BreathPhaseKind.PREPARE -> PREPARE
            BreathPhaseKind.INHALE -> INHALE
            BreathPhaseKind.HOLD_FULL -> HOLD
            BreathPhaseKind.EXHALE -> EXHALE
            BreathPhaseKind.HOLD_EMPTY -> HOLD_EMPTY
            BreathPhaseKind.RECOVER -> RECOVER
        }
    }
}
