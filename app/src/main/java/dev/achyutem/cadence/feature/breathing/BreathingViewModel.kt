package dev.achyutem.cadence.feature.breathing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.achyutem.cadence.core.common.cadenceViewModelFactory
import dev.achyutem.cadence.core.database.dao.BreathingDao
import dev.achyutem.cadence.core.database.entity.BreathingKind
import dev.achyutem.cadence.core.database.entity.BreathingSessionEntity
import dev.achyutem.cadence.core.time.CadenceClock
import dev.achyutem.cadence.domain.breathing.BreathPhase
import dev.achyutem.cadence.domain.breathing.BreathPhaseKind
import dev.achyutem.cadence.domain.breathing.BreathingExercise
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The running session, or null when the user is still choosing an exercise. */
data class BreathingRun(
    val exercise: BreathingExercise,
    val phases: List<BreathPhase>,
    val phaseIndex: Int = 0,
    /** Whole seconds remaining in the current phase. */
    val secondsLeft: Int = 0,
    val paused: Boolean = false,
    val finished: Boolean = false,
    /** Seconds elapsed across the whole session, for the log. */
    val elapsedSeconds: Int = 0,
) {
    val phase: BreathPhase get() = phases[phaseIndex.coerceIn(phases.indices)]
    val round: Int get() = phase.round
    val totalRounds: Int get() = phase.totalRounds

    /** 0f..1f through the current phase, for the breathing circle. */
    val phaseProgress: Float
        get() {
            val total = phase.seconds.coerceAtLeast(1)
            return ((total - secondsLeft).toFloat() / total).coerceIn(0f, 1f)
        }

    val sessionProgress: Float
        get() {
            val total = phases.sumOf { it.seconds }.coerceAtLeast(1)
            return (elapsedSeconds.toFloat() / total).coerceIn(0f, 1f)
        }
}

data class BreathingUiState(
    val run: BreathingRun? = null,
    val longestHoldSeconds: Int? = null,
    val sessionsToday: Int = 0,
    val safetyAcknowledged: Boolean = false,
)

class BreathingViewModel(
    private val sessions: BreathingDao,
    private val clock: CadenceClock,
) : ViewModel() {

    private val _run = MutableStateFlow<BreathingRun?>(null)
    val run: StateFlow<BreathingRun?> = _run.asStateFlow()

    val longestHold: StateFlow<Int?> = sessions.observeLongestHold()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sessionsToday: StateFlow<Int> = sessions.observeCountOn(clock.today())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private var ticker: Job? = null
    private var longestHoldThisRun = 0

    fun start(exercise: BreathingExercise) {
        ticker?.cancel()
        val phases = exercise.expand()
        longestHoldThisRun = 0
        _run.value = BreathingRun(
            exercise = exercise,
            phases = phases,
            phaseIndex = 0,
            secondsLeft = phases.first().seconds,
        )
        runTicker()
    }

    /**
     * The clock.
     *
     * Ticks once a second and decrements. Deliberately *not* wall-clock-corrected: this is a
     * guided exercise, not a stopwatch, and a session that silently skips ahead after the screen
     * was off would be worse than one that drifts by a few hundred milliseconds. Pausing simply
     * stops the ticker, so a paused hold does not keep counting down.
     */
    private fun runTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val current = _run.value ?: break
                if (current.paused || current.finished) continue
                advanceOneSecond()
                if (_run.value?.finished == true) break
            }
        }
    }

    private fun advanceOneSecond() {
        _run.update { current ->
            if (current == null) return@update null
            val remaining = current.secondsLeft - 1
            val elapsed = current.elapsedSeconds + 1

            if (remaining > 0) {
                return@update current.copy(secondsLeft = remaining, elapsedSeconds = elapsed)
            }

            // Phase finished. Record the hold before moving on, so a session abandoned during the
            // next phase still keeps the hold the user actually achieved.
            if (current.phase.kind == BreathPhaseKind.HOLD_FULL) {
                longestHoldThisRun = maxOf(longestHoldThisRun, current.phase.seconds)
            }

            val nextIndex = current.phaseIndex + 1
            if (nextIndex >= current.phases.size) {
                finish(current.copy(elapsedSeconds = elapsed), completed = true)
                current.copy(secondsLeft = 0, elapsedSeconds = elapsed, finished = true)
            } else {
                current.copy(
                    phaseIndex = nextIndex,
                    secondsLeft = current.phases[nextIndex].seconds,
                    elapsedSeconds = elapsed,
                )
            }
        }
    }

    fun togglePause() {
        _run.update { it?.copy(paused = !it.paused) }
    }

    /**
     * Skip the current phase.
     *
     * Present on purpose: the honest thing for a hold is to let people come up when they need to,
     * rather than making them stare at a countdown they are no longer doing. Skipping a hold does
     * not credit it.
     */
    fun skipPhase() {
        _run.update { current ->
            if (current == null) return@update null
            val nextIndex = current.phaseIndex + 1
            if (nextIndex >= current.phases.size) {
                finish(current, completed = true)
                current.copy(finished = true, secondsLeft = 0)
            } else {
                current.copy(phaseIndex = nextIndex, secondsLeft = current.phases[nextIndex].seconds)
            }
        }
    }

    /** Stop early. The session is still logged, where you stopped is the measurement. */
    fun stop() {
        val current = _run.value
        ticker?.cancel()
        if (current != null && !current.finished && current.elapsedSeconds > MIN_LOGGED_SECONDS) {
            finish(current, completed = false)
        }
        _run.value = null
    }

    fun dismissFinished() {
        ticker?.cancel()
        _run.value = null
    }

    private fun finish(current: BreathingRun, completed: Boolean) = viewModelScope.launch {
        val holds = current.phases.count { it.kind == BreathPhaseKind.HOLD_FULL }
        sessions.insert(
            BreathingSessionEntity(
                date = clock.today(),
                kind = current.exercise.toKind(),
                durationSeconds = current.elapsedSeconds,
                roundsCompleted = current.round,
                roundsPlanned = current.phases.maxOfOrNull { it.totalRounds } ?: holds,
                longestHoldSeconds = longestHoldThisRun,
                completed = completed,
                createdAt = clock.now(),
            )
        )
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }

    companion object {
        /** Below this, it was a mis-tap rather than a session. */
        private const val MIN_LOGGED_SECONDS = 10

        val Factory = cadenceViewModelFactory { container ->
            BreathingViewModel(container.database.breathingDao(), container.clock)
        }
    }
}

private fun BreathingExercise.toKind(): BreathingKind = when (this) {
    is BreathingExercise.Box -> BreathingKind.BOX
    is BreathingExercise.StaticApnea -> BreathingKind.STATIC_APNEA
    is BreathingExercise.Co2Table -> BreathingKind.CO2_TABLE
    is BreathingExercise.O2Table -> BreathingKind.O2_TABLE
}
