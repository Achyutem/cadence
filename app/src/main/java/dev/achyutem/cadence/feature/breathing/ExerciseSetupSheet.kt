package dev.achyutem.cadence.feature.breathing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.designsystem.component.ButtonTone
import dev.achyutem.cadence.core.designsystem.component.CadenceButton
import dev.achyutem.cadence.core.designsystem.component.CadenceDivider
import dev.achyutem.cadence.core.designsystem.component.CadenceIconButton
import dev.achyutem.cadence.core.designsystem.component.SectionHeader
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.theme.tabularFigures
import dev.achyutem.cadence.core.designsystem.token.Borders
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.domain.breathing.BreathField
import dev.achyutem.cadence.domain.breathing.BreathPhaseKind
import dev.achyutem.cadence.domain.breathing.BreathingExercise
import dev.achyutem.cadence.domain.breathing.asClock
import dev.achyutem.cadence.domain.breathing.totalSeconds

/**
 * Set an exercise up, then start it.
 *
 * One sheet for all four exercises: it renders whatever [BreathingExercise.fields] the exercise
 * declares, so a CO2 table and box breathing get the same steppers without a line of per-exercise
 * UI. Changes are saved as they are made rather than on Start, because the common case is opening
 * this to adjust one number and the second most common is opening it to check what the numbers
 * currently are.
 *
 * The plan strip underneath is the point of the whole sheet. "Three rounds, one minute, plus ten
 * seconds" is easy to type and hard to picture; showing 1:00, 1:10, 1:20 means nobody starts an
 * eight-round table that ends on a three-minute hold by accident.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExerciseSetupSheet(
    exercise: BreathingExercise,
    onChange: (BreathingExercise) -> Unit,
    onStart: (BreathingExercise) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = Radius.shapeXl,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .navigationBarsPadding(),
        ) {
            Text(
                text = exercise.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    R.string.breathing_setup_total,
                    exercise.totalSeconds().asClock(),
                ),
                style = MaterialTheme.typography.bodyMedium.tabularFigures,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.lg))

            exercise.fields.forEachIndexed { index, field ->
                if (index > 0) CadenceDivider()
                FieldStepper(
                    field = field,
                    value = exercise.valueOf(field),
                    onValue = { onChange(exercise.with(field, it)) },
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            SectionHeader(title = stringResource(R.string.breathing_setup_plan))
            Spacer(Modifier.height(Spacing.xs))
            PlanStrip(exercise)

            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.breathing_setup_auto),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CadenceButton(
                    text = stringResource(R.string.breathing_setup_start),
                    onClick = { onStart(exercise) },
                    tone = ButtonTone.Primary,
                    modifier = Modifier.weight(1f),
                )
                CadenceButton(
                    text = stringResource(R.string.breathing_setup_reset),
                    onClick = { onChange(exercise.resetToDefault()) },
                    tone = ButtonTone.Ghost,
                )
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/** A label, the current value, and a step either side of it. */
@Composable
private fun FieldStepper(
    field: BreathField,
    value: Int,
    onValue: (Int) -> Unit,
) {
    val label = stringResource(field.labelRes())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        CadenceIconButton(
            icon = Icons.Rounded.Remove,
            contentDescription = stringResource(R.string.breathing_field_less, label),
            onClick = { onValue(value - field.step) },
            enabled = value > field.min,
        )
        Text(
            text = field.format(value),
            style = MaterialTheme.typography.titleMedium.tabularFigures,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.Center,
        )
        CadenceIconButton(
            icon = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.breathing_field_more, label),
            onClick = { onValue(value + field.step) },
            enabled = value < field.max,
        )
    }
}

/**
 * Every effort of the session, in order, as a scrollable strip.
 *
 * Shows holds only. The rests matter to the body but not to the decision being made here, and a
 * strip that alternated hold and rest would be twice as long and half as readable.
 */
@Composable
private fun PlanStrip(exercise: BreathingExercise) {
    val holds = exercise.expand()
        .filter { it.kind == BreathPhaseKind.HOLD_FULL && it.round > 0 }
        .map { it.seconds }

    if (holds.isEmpty()) {
        // Box breathing has no effort to chart; every round is identical by design.
        Text(
            text = exercise.subtitle,
            style = MaterialTheme.typography.bodyMedium.tabularFigures,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        holds.forEachIndexed { index, seconds ->
            Column(
                modifier = Modifier
                    .clip(Radius.shapeSm)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(Borders.hairline, CadenceTheme.colors.border, Radius.shapeSm)
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.breathing_plan_round, index + 1),
                    style = MaterialTheme.typography.labelSmall.tabularFigures,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = seconds.asClock(),
                    style = MaterialTheme.typography.bodyMedium.tabularFigures,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.size(Spacing.lg))
    }
}

/** Durations read as `m:ss`; a round count is just a number. */
private fun BreathField.format(value: Int): String =
    if (isDuration) value.asClock() else value.toString()

private fun BreathField.labelRes(): Int = when (this) {
    BreathField.ROUNDS -> R.string.breathing_field_rounds
    BreathField.BOX_SECONDS -> R.string.breathing_field_box_seconds
    BreathField.BREATHE_UP -> R.string.breathing_field_breathe_up
    BreathField.HOLD -> R.string.breathing_field_hold
    BreathField.START_HOLD -> R.string.breathing_field_start_hold
    BreathField.HOLD_INCREMENT -> R.string.breathing_field_hold_increment
    BreathField.REST -> R.string.breathing_field_rest
    BreathField.START_REST -> R.string.breathing_field_start_rest
    BreathField.REST_DECREMENT -> R.string.breathing_field_rest_decrement
}

/** The shipped configuration of whichever exercise this is. */
private fun BreathingExercise.resetToDefault(): BreathingExercise = when (this) {
    is BreathingExercise.Box -> BreathingExercise.Box()
    is BreathingExercise.StaticApnea -> BreathingExercise.StaticApnea()
    is BreathingExercise.Co2Table -> BreathingExercise.Co2Table()
    is BreathingExercise.O2Table -> BreathingExercise.O2Table()
}

/**
 * A speaker toggle small enough to sit next to the screen title.
 *
 * Sound lives here rather than in Settings because it is the one thing people change depending on
 * where they are, and walking to Settings to silence a session you are about to start in a quiet
 * room is three taps too many.
 */
@Composable
fun SoundToggle(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    CadenceIconButton(
        icon = if (enabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
        contentDescription = stringResource(
            if (enabled) R.string.breathing_sound_on else R.string.breathing_sound_off,
        ),
        onClick = { onToggle(!enabled) },
    )
}
