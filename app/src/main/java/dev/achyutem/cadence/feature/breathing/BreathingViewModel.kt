package dev.achyutem.cadence.feature.breathing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.achyutem.cadence.core.common.cadenceViewModelFactory
import dev.achyutem.cadence.core.database.dao.BreathingDao
import dev.achyutem.cadence.core.database.entity.BreathingKind
import dev.achyutem.cadence.core.database.entity.BreathingSessionEntity
import dev.achyutem.cadence.core.datastore.SettingsRepository
import dev.achyutem.cadence.core.time.CadenceClock
import dev.achyutem.cadence.domain.breathing.BreathCue
import dev.achyutem.cadence.domain.breathing.BreathPhase
import dev.achyutem.cadence.domain.breathing.BreathPhaseKind
import dev.achyutem.cadence.domain.breathing.BreathingExercise
import dev.achyutem.cadence.domain.breathing.BreathingPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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
    private val settings: SettingsRepository,
    private val clock: CadenceClock,
) : ViewModel() {

    private val _run = MutableStateFlow<BreathingRun?>(null)
    val run: StateFlow<BreathingRun?> = _run.asStateFlow()

    /**
     * Sounds the session wants to make.
     *
     * A hot flow rather than part of the state: a cue is an event that happens once, and a state
     * field would replay the last beep on every recomposition and every screen rotation. `replay`
     * is 0 and the buffer exists only so that emitting from the ticker never suspends.
     */
    private val _cues = MutableSharedFlow<BreathCue>(extraBufferCapacity = 8)
    val cues: SharedFlow<BreathCue> = _cues.asSharedFlow()

    val breathing: StateFlow<BreathingPreferences> = settings.preferences
        .map { it.breathing }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            BreathingPreferences.Default,
        )

    val longestHold: StateFlow<Int?> = sessions.observeLongestHold()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val sessionsToday: StateFlow<Int> = sessions.observeCountOn(clock.today())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private var ticker: Job? = null
    private var longestHoldThisRun = 0

    /**
     * Save one exercise's numbers without starting it.
     *
     * Editing is persisted on every change rather than behind a Save button, so the numbers you
     * left an exercise on are the numbers it has next week.
     */
    fun save(exercise: BreathingExercise) = viewModelScope.launch {
        settings.setBreathing(breathing.value.with(exercise))
    }

    fun setSoundEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setBreathingSoundEnabled(enabled)
    }

    /**
     * Begin a session.
     *
     * Once this returns the session runs itself to the end: every phase of every round is already
     * in [BreathingRun.phases], and the ticker walks them without waiting for input. Pause, skip
     * and stop exist, but nothing has to be touched to get from the first round to the last.
     */
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
        _cues.tryEmit(BreathCue.forPhase(phases.first().kind))
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
        val current = _run.value ?: return
        val remaining = current.secondsLeft - 1
        val elapsed = current.elapsedSeconds + 1

        if (remaining > 0) {
            _run.value = current.copy(secondsLeft = remaining, elapsedSeconds = elapsed)
            if (remaining <= BreathCue.COUNTDOWN_SECONDS &&
                current.phase.seconds >= BreathCue.MIN_COUNTDOWN_PHASE_SECONDS
            ) {
                _cues.tryEmit(BreathCue.COUNTDOWN)
            }
            return
        }

        // Phase finished. Record the hold before moving on, so a session abandoned during the
        // next phase still keeps the hold the user actually achieved.
        if (current.phase.kind == BreathPhaseKind.HOLD_FULL) {
            longestHoldThisRun = maxOf(longestHoldThisRun, current.phase.seconds)
        }

        val nextIndex = current.phaseIndex + 1
        if (nextIndex >= current.phases.size) {
            finish(current.copy(elapsedSeconds = elapsed), completed = true)
            _run.value = current.copy(secondsLeft = 0, elapsedSeconds = elapsed, finished = true)
            _cues.tryEmit(BreathCue.FINISH)
        } else {
            val next = current.phases[nextIndex]
            _run.value = current.copy(
                phaseIndex = nextIndex,
                secondsLeft = next.seconds,
                elapsedSeconds = elapsed,
            )
            _cues.tryEmit(BreathCue.forPhase(next.kind))
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
        val current = _run.value ?: return
        val nextIndex = current.phaseIndex + 1
        if (nextIndex >= current.phases.size) {
            finish(current, completed = true)
            _run.value = current.copy(finished = true, secondsLeft = 0)
            _cues.tryEmit(BreathCue.FINISH)
        } else {
            val next = current.phases[nextIndex]
            _run.value = current.copy(phaseIndex = nextIndex, secondsLeft = next.seconds)
            _cues.tryEmit(BreathCue.forPhase(next.kind))
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
            BreathingViewModel(
                sessions = container.database.breathingDao(),
                settings = container.settingsRepository,
                clock = container.clock,
            )
        }
    }
}

private fun BreathingExercise.toKind(): BreathingKind = when (this) {
    is BreathingExercise.Box -> BreathingKind.BOX
    is BreathingExercise.StaticApnea -> BreathingKind.STATIC_APNEA
    is BreathingExercise.Co2Table -> BreathingKind.CO2_TABLE
    is BreathingExercise.O2Table -> BreathingKind.O2_TABLE
}
