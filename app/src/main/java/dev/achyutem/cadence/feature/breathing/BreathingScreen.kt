package dev.achyutem.cadence.feature.breathing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.designsystem.component.ButtonTone
import dev.achyutem.cadence.core.designsystem.component.CadenceButton
import dev.achyutem.cadence.core.designsystem.component.CadenceCard
import dev.achyutem.cadence.core.designsystem.component.CadenceIconButton
import dev.achyutem.cadence.core.designsystem.component.CadenceProgressBar
import dev.achyutem.cadence.core.designsystem.component.SectionHeader
import dev.achyutem.cadence.core.designsystem.theme.CadencePreviewTheme
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.theme.tabularFigures
import dev.achyutem.cadence.core.designsystem.token.Borders
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.domain.breathing.BreathPhaseKind
import dev.achyutem.cadence.domain.breathing.BreathingExercise
import dev.achyutem.cadence.domain.breathing.BreathingPreferences
import dev.achyutem.cadence.domain.breathing.asClock
import dev.achyutem.cadence.domain.breathing.totalSeconds

/**
 * Breathe.
 *
 * Two modes in one destination: a list of exercises, and, once one is running, a full-screen
 * session that replaces it. A running session takes the whole screen deliberately: these
 * exercises want your attention on one thing, and a dock floating over a breath-hold is an
 * invitation to tap away mid-round.
 */
@Composable
fun BreathingScreen(
    /**
     * Raised while a session is running, so the shell can hide the dock.
     *
     * A running exercise wants the whole screen: these are the only places in the app where the
     * user is meant to stop looking at the phone, and a navigation bar floating over a breath-hold
     * is an invitation to tap away mid-round.
     */
    onImmersiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BreathingViewModel = viewModel(factory = BreathingViewModel.Factory),
) {
    val run by viewModel.run.collectAsStateWithLifecycle()
    val longestHold by viewModel.longestHold.collectAsStateWithLifecycle()
    val sessionsToday by viewModel.sessionsToday.collectAsStateWithLifecycle()
    val breathing by viewModel.breathing.collectAsStateWithLifecycle()

    LaunchedEffect(run != null) { onImmersiveChange(run != null) }
    DisposableEffect(Unit) { onDispose { onImmersiveChange(false) } }

    /**
     * Turn the session's cues into sound.
     *
     * Collected here rather than played from the ViewModel so that nothing in the runner touches
     * audio; muting is a plain early return, which also means a muted session costs nothing.
     */
    val tones = rememberBreathingTones()
    LaunchedEffect(viewModel, breathing.soundEnabled) {
        if (!breathing.soundEnabled) return@LaunchedEffect
        viewModel.cues.collect(tones::play)
    }

    if (run != null) {
        BackHandler { viewModel.stop() }
        SessionView(
            run = run!!,
            soundEnabled = breathing.soundEnabled,
            onToggleSound = { viewModel.setSoundEnabled(it) },
            onTogglePause = viewModel::togglePause,
            onSkip = viewModel::skipPhase,
            onStop = viewModel::stop,
            onDone = viewModel::dismissFinished,
            modifier = modifier,
        )
    } else {
        ExerciseList(
            breathing = breathing,
            longestHoldSeconds = longestHold,
            sessionsToday = sessionsToday,
            onEdit = viewModel::save,
            onStart = viewModel::start,
            onToggleSound = { viewModel.setSoundEnabled(it) },
            modifier = modifier,
        )
    }
}

@Composable
private fun ExerciseList(
    breathing: BreathingPreferences,
    longestHoldSeconds: Int?,
    sessionsToday: Int,
    onEdit: (BreathingExercise) -> Unit,
    onStart: (BreathingExercise) -> Unit,
    onToggleSound: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val exercises = breathing.exercises()

    /**
     * Which exercise is open in the setup sheet.
     *
     * Held as a whole exercise rather than an index so the sheet can edit it live; every change
     * goes straight back to the ViewModel through [onEdit] as well, so what the sheet shows and
     * what is stored never drift apart.
     */
    var editing by remember { mutableStateOf<BreathingExercise?>(null) }

    editing?.let { exercise ->
        ExerciseSetupSheet(
            exercise = exercise,
            onChange = {
                editing = it
                onEdit(it)
            },
            onStart = {
                editing = null
                onStart(it)
            },
            onDismiss = { editing = null },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = Spacing.screenGutter,
            end = Spacing.screenGutter,
            top = Spacing.xl,
            bottom = Spacing.dockClearance,
        ),
    ) {
        item(key = "title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.breathing_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                SoundToggle(enabled = breathing.soundEnabled, onToggle = onToggleSound)
            }
            if (sessionsToday > 0 || longestHoldSeconds != null) {
                Spacer(Modifier.height(Spacing.xxs))
                Text(
                    text = buildString {
                        if (sessionsToday > 0) {
                            append(
                                if (sessionsToday == 1) "1 session today" else "$sessionsToday sessions today",
                            )
                        }
                        longestHoldSeconds?.takeIf { it > 0 }?.let {
                            if (isNotEmpty()) append(" · ")
                            append("best hold ${it.asClock()}")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium.tabularFigures,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }

        item(key = "safety") {
            SafetyNotice()
            Spacer(Modifier.height(Spacing.lg))
        }

        item(key = "exercises-header") {
            SectionHeader(title = stringResource(R.string.breathing_exercises))
            Spacer(Modifier.height(Spacing.xs))
        }

        items(exercises, key = { it.title }) { exercise ->
            ExerciseCard(exercise = exercise, onClick = { editing = exercise })
            Spacer(Modifier.height(Spacing.xs))
        }
    }
}

/**
 * The safety notice.
 *
 * Shown on the list rather than behind a one-time dismissal, and written as instructions rather
 * than as a disclaimer. Breath-hold training can cause a blackout with no warning sensation,
 * which is exactly why it is dangerous in water, and why a line of text here is not boilerplate.
 */
@Composable
private fun SafetyNotice() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.shapeLg)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(Borders.hairline, CadenceTheme.colors.border, Radius.shapeLg)
            .padding(Spacing.md),
    ) {
        Column {
            Text(
                text = stringResource(R.string.breathing_safety_title),
                style = MaterialTheme.typography.titleSmall,
                color = CadenceTheme.colors.warning,
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = stringResource(R.string.breathing_safety_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExerciseCard(exercise: BreathingExercise, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.shapeLg)
            .background(MaterialTheme.colorScheme.surface)
            .border(Borders.hairline, CadenceTheme.colors.border, Radius.shapeLg)
            .clickable(onClick = onClick)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = exercise.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = exercise.subtitle,
                style = MaterialTheme.typography.bodySmall.tabularFigures,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = exercise.totalSeconds().asClock(),
                style = MaterialTheme.typography.labelMedium.tabularFigures,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SessionView(
    run: BreathingRun,
    soundEnabled: Boolean,
    onToggleSound: (Boolean) -> Unit,
    onTogglePause: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    /**
     * Hold the screen on for the length of the session.
     *
     * A table can run twenty minutes without a touch, which is the whole point, and a phone that
     * locks itself in round three takes the countdown with it. Scoped to this composable, so the
     * flag is gone the moment the session ends rather than for as long as the app is open.
     */
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = Spacing.screenGutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CadenceIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.breathing_stop),
                onClick = onStop,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = run.exercise.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            SoundToggle(enabled = soundEnabled, onToggle = onToggleSound)
        }

        Spacer(Modifier.height(Spacing.md))
        CadenceProgressBar(
            progress = run.sessionProgress,
            height = 3.dp,
            contentDescription = null,
        )

        Spacer(Modifier.weight(1f))

        if (run.finished) {
            Text(
                text = stringResource(R.string.breathing_complete),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = stringResource(
                    R.string.breathing_complete_summary,
                    run.elapsedSeconds.asClock(),
                ),
                style = MaterialTheme.typography.bodyMedium.tabularFigures,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xl))
            CadenceButton(
                text = stringResource(R.string.breathing_done),
                onClick = onDone,
                tone = ButtonTone.Primary,
            )
        } else {
            Text(
                text = stringResource(run.phase.kind.labelRes()),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (run.totalRounds > 0) {
                Text(
                    text = stringResource(R.string.breathing_round, run.round, run.totalRounds),
                    style = MaterialTheme.typography.labelMedium.tabularFigures,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Spacing.xl))
            BreathCircle(
                kind = run.phase.kind,
                phaseProgress = run.phaseProgress,
                secondsLeft = run.secondsLeft,
                phaseIndex = run.phaseIndex,
            )
            Spacer(Modifier.height(Spacing.xxl))

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                CadenceButton(
                    text = stringResource(
                        if (run.paused) R.string.breathing_resume else R.string.breathing_pause,
                    ),
                    onClick = onTogglePause,
                    icon = if (run.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    tone = ButtonTone.Secondary,
                )
                CadenceButton(
                    text = stringResource(R.string.breathing_skip),
                    onClick = onSkip,
                    icon = Icons.Rounded.SkipNext,
                    tone = ButtonTone.Ghost,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(Spacing.xl))
    }
}

private fun BreathPhaseKind.labelRes(): Int = when (this) {
    BreathPhaseKind.PREPARE -> R.string.breathing_phase_prepare
    BreathPhaseKind.INHALE -> R.string.breathing_phase_inhale
    BreathPhaseKind.HOLD_FULL -> R.string.breathing_phase_hold
    BreathPhaseKind.EXHALE -> R.string.breathing_phase_exhale
    BreathPhaseKind.HOLD_EMPTY -> R.string.breathing_phase_hold_empty
    BreathPhaseKind.RECOVER -> R.string.breathing_phase_recover
}

@Preview(showBackground = true)
@Composable
private fun BreathingPreview() = CadencePreviewTheme {
    ExerciseList(
        breathing = BreathingPreferences.Default,
        longestHoldSeconds = 185,
        sessionsToday = 2,
        onEdit = {},
        onStart = {},
        onToggleSound = {},
    )
}
