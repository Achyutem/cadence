package dev.achyutem.cadence.domain.breathing

/**
 * The user's own version of the four exercises.
 *
 * Kept as a flat list of numbers rather than as serialised [BreathingExercise] objects: a
 * preferences store is a place for scalars, and this way a rename inside the domain never
 * silently invalidates settings someone has been using for a year.
 *
 * The defaults here are the defaults of the exercises themselves, which is why the constructor
 * parameters and [BreathingExercise]'s repeat each other. That duplication is deliberate and
 * covered by a test: it is the price of the two layers staying independent.
 */
data class BreathingPreferences(
    /** Tones at every phase change and a tick on the last three seconds. */
    val soundEnabled: Boolean = true,

    val boxSeconds: Int = 4,
    val boxRounds: Int = 8,

    val staticBreatheUpSeconds: Int = 120,
    val staticHoldSeconds: Int = 120,
    val staticRounds: Int = 1,

    val co2HoldSeconds: Int = 60,
    val co2StartRestSeconds: Int = 120,
    val co2RestDecrementSeconds: Int = 15,
    val co2Rounds: Int = 8,

    val o2RestSeconds: Int = 120,
    val o2StartHoldSeconds: Int = 60,
    val o2HoldIncrementSeconds: Int = 15,
    val o2Rounds: Int = 8,
) {
    /** The four exercises as this user has configured them, in the order the list shows them. */
    fun exercises(): List<BreathingExercise> = listOf(
        BreathingExercise.Box(seconds = boxSeconds, rounds = boxRounds),
        BreathingExercise.StaticApnea(
            breatheUpSeconds = staticBreatheUpSeconds,
            holdSeconds = staticHoldSeconds,
            rounds = staticRounds,
        ),
        BreathingExercise.Co2Table(
            holdSeconds = co2HoldSeconds,
            startRestSeconds = co2StartRestSeconds,
            restDecrementSeconds = co2RestDecrementSeconds,
            rounds = co2Rounds,
        ),
        BreathingExercise.O2Table(
            restSeconds = o2RestSeconds,
            startHoldSeconds = o2StartHoldSeconds,
            holdIncrementSeconds = o2HoldIncrementSeconds,
            rounds = o2Rounds,
        ),
    )

    /** This, with [exercise]'s numbers folded back in. The other three are untouched. */
    fun with(exercise: BreathingExercise): BreathingPreferences = when (exercise) {
        is BreathingExercise.Box -> copy(
            boxSeconds = exercise.seconds,
            boxRounds = exercise.rounds,
        )

        is BreathingExercise.StaticApnea -> copy(
            staticBreatheUpSeconds = exercise.breatheUpSeconds,
            staticHoldSeconds = exercise.holdSeconds,
            staticRounds = exercise.rounds,
        )

        is BreathingExercise.Co2Table -> copy(
            co2HoldSeconds = exercise.holdSeconds,
            co2StartRestSeconds = exercise.startRestSeconds,
            co2RestDecrementSeconds = exercise.restDecrementSeconds,
            co2Rounds = exercise.rounds,
        )

        is BreathingExercise.O2Table -> copy(
            o2RestSeconds = exercise.restSeconds,
            o2StartHoldSeconds = exercise.startHoldSeconds,
            o2HoldIncrementSeconds = exercise.holdIncrementSeconds,
            o2Rounds = exercise.rounds,
        )
    }

    companion object {
        val Default = BreathingPreferences()
    }
}
