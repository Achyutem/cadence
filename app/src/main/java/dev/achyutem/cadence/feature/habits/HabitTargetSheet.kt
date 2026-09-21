package dev.achyutem.cadence.feature.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.database.entity.HabitGoalDirection
import dev.achyutem.cadence.core.database.entity.HabitType
import dev.achyutem.cadence.core.designsystem.component.ButtonTone
import dev.achyutem.cadence.core.designsystem.component.CadenceButton
import dev.achyutem.cadence.core.designsystem.component.SectionHeader
import dev.achyutem.cadence.core.designsystem.component.SegmentedControl
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.token.Borders
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing

/**
 * Change what a habit is measuring and how much of it counts.
 *
 * The target used to be a row that cycled to the next plausible number on every tap: 1, 2, 3, 4,
 * 5, 10, 15... and then wrapped back to 1. Fine for nudging 3 to 4, useless for saying 45, and
 * there was no way at all to change a habit from Done to a count once it existed.
 *
 * Measurement type is here rather than frozen at creation because it is the single most common
 * thing to get wrong in the first ten seconds of a habit's life. **Past entries are never
 * rewritten**: an entry stores the value it was logged with and whether it counted at the time,
 * so switching a habit from Done to Minutes leaves last week exactly as it happened.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HabitTargetSheet(
    type: HabitType,
    target: Double,
    unit: String?,
    goalDirection: HabitGoalDirection,
    onDismiss: () -> Unit,
    onApply: (HabitType, Double, String?, HabitGoalDirection) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var editedType by remember { mutableStateOf(type) }
    var editedTarget by remember { mutableStateOf(target.trim()) }
    var editedUnit by remember { mutableStateOf(unit.orEmpty()) }
    var editedDirection by remember { mutableStateOf(goalDirection) }

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
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.habit_target_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.lg))

            SectionHeader(title = stringResource(R.string.habit_measure))
            Spacer(Modifier.height(Spacing.xs))
            SegmentedControl(
                options = HabitType.entries,
                selected = editedType,
                onSelect = { next ->
                    editedType = next
                    // A Done habit's target is always one. Carrying "50" across would leave a
                    // habit that says "done" and quietly needs fifty of something.
                    if (next == HabitType.BOOLEAN) editedTarget = "1"
                },
                label = { stringResource(it.sheetLabelRes()) },
            )

            if (editedType != HabitType.BOOLEAN) {
                Spacer(Modifier.height(Spacing.lg))
                SectionHeader(title = stringResource(R.string.habit_target_placeholder))
                Spacer(Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    TargetField(
                        value = editedTarget,
                        onValueChange = { input ->
                            editedTarget = input.filter { it.isDigit() }.take(5)
                        },
                        placeholder = stringResource(R.string.habit_target_placeholder),
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    if (editedType == HabitType.QUANTITY) {
                        TargetField(
                            value = editedUnit,
                            onValueChange = { editedUnit = it.take(12) },
                            placeholder = stringResource(R.string.habit_unit_placeholder),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.sm))
                SegmentedControl(
                    options = HabitGoalDirection.entries,
                    selected = editedDirection,
                    onSelect = { editedDirection = it },
                    label = { stringResource(it.sheetLabelRes()) },
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.habit_target_history_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            CadenceButton(
                text = stringResource(R.string.habit_target_save),
                onClick = {
                    val value = if (editedType == HabitType.BOOLEAN) {
                        1.0
                    } else {
                        editedTarget.toDoubleOrNull()?.coerceAtLeast(1.0) ?: target
                    }
                    onApply(
                        editedType,
                        value,
                        editedUnit.trim().takeIf {
                            it.isNotEmpty() && editedType == HabitType.QUANTITY
                        },
                        editedDirection,
                    )
                    onDismiss()
                },
                tone = ButtonTone.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun TargetField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(Radius.shapeMd)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(Borders.hairline, CadenceTheme.colors.border, Radius.shapeMd)
            .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun HabitType.sheetLabelRes(): Int = when (this) {
    HabitType.BOOLEAN -> R.string.habit_type_boolean
    HabitType.COUNT -> R.string.habit_type_count
    HabitType.QUANTITY -> R.string.habit_type_quantity
    HabitType.DURATION -> R.string.habit_type_duration
}

private fun HabitGoalDirection.sheetLabelRes(): Int = when (this) {
    HabitGoalDirection.AT_LEAST -> R.string.habit_goal_at_least
    HabitGoalDirection.AT_MOST -> R.string.habit_goal_at_most
}

/** `3.0` reads as `3`; `2.5` keeps its half. */
private fun Double.trim(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()
