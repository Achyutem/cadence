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
import dev.achyutem.cadence.core.database.entity.RecurrenceRuleEntity
import dev.achyutem.cadence.core.designsystem.component.ButtonTone
import dev.achyutem.cadence.core.designsystem.component.CadenceButton
import dev.achyutem.cadence.core.designsystem.component.SectionHeader
import dev.achyutem.cadence.core.designsystem.component.SegmentedControl
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.token.Borders
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.core.time.CadenceClock
import dev.achyutem.cadence.domain.recurrence.RecurrencePresets
import java.time.DayOfWeek
import java.time.LocalDate

/** The schedules offered when creating a habit. Custom rules come from the detail screen. */
private enum class HabitSchedule { DAILY, WEEKDAYS, WEEKENDS }

/**
 * Habit creation.
 *
 * Only a name is required. Everything else has a working default, a boolean habit, every day,
 * so the fast path is type a name and press Create. The extra fields appear in place rather than
 * behind an "advanced" disclosure, because for a habit the target *is* the point and hiding it
 * would mean most habits get created wrong and edited immediately.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HabitEditorSheet(
    /** Anchor for any recurrence rule created here. From the app clock; see `CLAUDE.md` #15. */
    today: LocalDate,
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        description: String?,
        type: HabitType,
        target: Double,
        unit: String?,
        goalDirection: HabitGoalDirection,
        rule: RecurrenceRuleEntity?,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(HabitType.BOOLEAN) }
    var target by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf(HabitGoalDirection.AT_LEAST) }
    var schedule by remember { mutableStateOf(HabitSchedule.DAILY) }

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
                text = stringResource(R.string.habit_new_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.lg))

            SheetField(
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.habit_name_placeholder),
            )
            Spacer(Modifier.height(Spacing.lg))

            SectionHeader(title = stringResource(R.string.habit_measure))
            Spacer(Modifier.height(Spacing.xs))
            SegmentedControl(
                options = HabitType.entries,
                selected = type,
                onSelect = { next ->
                    type = next
                    // A boolean habit's target is always 1; carrying "50" over from a count would
                    // make the habit quietly impossible.
                    if (next == HabitType.BOOLEAN) target = "1"
                },
                label = { stringResource(it.labelRes()) },
            )

            if (type != HabitType.BOOLEAN) {
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SheetField(
                        value = target,
                        onValueChange = { input -> target = input.filter(Char::isDigit).take(5) },
                        placeholder = stringResource(R.string.habit_target_placeholder),
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    if (type == HabitType.QUANTITY) {
                        SheetField(
                            value = unit,
                            onValueChange = { unit = it.take(16) },
                            placeholder = stringResource(R.string.habit_unit_placeholder),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.sm))
                SegmentedControl(
                    options = HabitGoalDirection.entries,
                    selected = direction,
                    onSelect = { direction = it },
                    label = { stringResource(it.labelRes()) },
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            SectionHeader(title = stringResource(R.string.habit_schedule))
            Spacer(Modifier.height(Spacing.xs))
            SegmentedControl(
                options = HabitSchedule.entries,
                selected = schedule,
                onSelect = { schedule = it },
                label = { stringResource(it.labelRes()) },
            )

            Spacer(Modifier.height(Spacing.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                CadenceButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    tone = ButtonTone.Secondary,
                    modifier = Modifier.weight(1f),
                )
                CadenceButton(
                    text = stringResource(R.string.habit_create),
                    onClick = {
                        onCreate(
                            name,
                            null,
                            type,
                            target.toDoubleOrNull() ?: 1.0,
                            unit.ifBlank { null },
                            direction,
                            schedule.toRule(today),
                        )
                    },
                    tone = ButtonTone.Primary,
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/**
 * A daily habit gets a **null rule**, not a daily one.
 *
 * Null already means "every day" everywhere in the engine, and storing a row to say the same
 * thing would put a `recurrence_rules` entry behind most habits in the database for no benefit.
 */
private fun HabitSchedule.toRule(anchor: LocalDate): RecurrenceRuleEntity? =
    when (this) {
        HabitSchedule.DAILY -> null
        HabitSchedule.WEEKDAYS -> RecurrencePresets.weekdays(anchor)
        HabitSchedule.WEEKENDS -> RecurrencePresets.weekly(
            anchor = anchor,
            days = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
        )
    }

@Composable
private fun SheetField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(Radius.shapeMd)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(Borders.hairline, CadenceTheme.colors.border, Radius.shapeMd)
            .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun HabitType.labelRes(): Int = when (this) {
    HabitType.BOOLEAN -> R.string.habit_type_boolean
    HabitType.COUNT -> R.string.habit_type_count
    HabitType.QUANTITY -> R.string.habit_type_quantity
    HabitType.DURATION -> R.string.habit_type_duration
}

private fun HabitGoalDirection.labelRes(): Int = when (this) {
    HabitGoalDirection.AT_LEAST -> R.string.habit_goal_at_least
    HabitGoalDirection.AT_MOST -> R.string.habit_goal_at_most
}

private fun HabitSchedule.labelRes(): Int = when (this) {
    HabitSchedule.DAILY -> R.string.habit_schedule_daily
    HabitSchedule.WEEKDAYS -> R.string.habit_schedule_weekdays
    HabitSchedule.WEEKENDS -> R.string.habit_schedule_weekends
}
