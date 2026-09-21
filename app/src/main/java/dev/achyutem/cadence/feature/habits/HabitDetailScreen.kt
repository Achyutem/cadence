package dev.achyutem.cadence.feature.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.achyutem.cadence.R
import dev.achyutem.cadence.domain.habit.Habit
import dev.achyutem.cadence.core.database.entity.HabitType
import dev.achyutem.cadence.core.database.entity.HabitGoalDirection
import dev.achyutem.cadence.core.designsystem.component.ButtonTone
import dev.achyutem.cadence.core.designsystem.component.CadenceCard
import dev.achyutem.cadence.core.designsystem.component.CadenceIconButton
import dev.achyutem.cadence.core.designsystem.component.CadenceTimePickerDialog
import dev.achyutem.cadence.core.designsystem.component.Heatmap
import dev.achyutem.cadence.core.designsystem.component.RecurrencePickerSheet
import dev.achyutem.cadence.core.designsystem.component.HeatmapLegend
import dev.achyutem.cadence.core.designsystem.component.SectionHeader
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.theme.tabularFigures
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.domain.habit.trim
import dev.achyutem.cadence.domain.recurrence.describe

/**
 * Habit detail: the streak, the heatmap and the numbers behind them.
 *
 * Streaks are stated, never celebrated. There is no flame animation, no "keep it up!", no
 * milestone. A streak is a fact about the data, and the moment it becomes a reward it starts
 * distorting the behaviour it was supposed to measure.
 */
@Composable
fun HabitDetailScreen(
    habitId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HabitDetailViewModel = viewModel(
        key = "habit-$habitId",
        factory = HabitDetailViewModel.factory(habitId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val habitOrNull = state.habit
    var showRecurrence by remember { mutableStateOf(false) }
    var showReminderTime by remember { mutableStateOf(false) }
    var showTarget by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CadenceIconButton(
                icon = Icons.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            CadenceIconButton(
                icon = Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.action_delete),
                onClick = { viewModel.delete(onBack) },
                tone = ButtonTone.Danger,
            )
        }

        val habit = state.habit
        if (state.loading || habit == null) return@Column

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter),
        ) {
            // The name is edited in place. Opening a separate editor to change one word is the
            // kind of friction that stops people fixing a typo they see every day.
            EditableName(value = habit.name, onValueChange = viewModel::setName)

            Spacer(Modifier.height(Spacing.lg))

            // The streak, stated plainly.
            Text(
                text = pluralStringResource(
                    R.plurals.habit_streak,
                    state.currentStreak,
                    state.currentStreak,
                ),
                style = MaterialTheme.typography.displaySmall.tabularFigures,
                color = if (state.currentStreak > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Spacer(Modifier.height(Spacing.lg))

            SectionHeader(title = stringResource(R.string.habit_settings))
            Spacer(Modifier.height(Spacing.xxs))
            SettingRow(
                label = stringResource(R.string.habit_schedule),
                value = state.rule?.describe() ?: stringResource(R.string.habit_schedule_daily),
                onClick = { showRecurrence = true },
            )
            // Shown for every habit, including Done ones. "Change the target" of a Done habit
            // means "make this a count", and hiding the row was the only thing stopping that.
            SettingRow(
                label = stringResource(R.string.habit_target_placeholder),
                value = habit.measurementSummary(),
                onClick = { showTarget = true },
            )
            SettingRow(
                label = stringResource(R.string.task_field_reminder),
                value = state.reminder?.timeOfDay?.toString()
                    ?: stringResource(R.string.task_field_none),
                onClick = { showReminderTime = true },
            )

            Spacer(Modifier.height(Spacing.lg))
            SectionHeader(title = stringResource(R.string.habit_activity))
            Spacer(Modifier.height(Spacing.xs))
            CadenceCard(contentPadding = Spacing.sm) {
                Column {
                    // Six months does not fit on a phone. The heatmap handles its own horizontal
                    // scrolling so that the weekday gutter stays pinned while the grid moves.
                    Heatmap(
                        startDate = state.heatmapStart,
                        endDate = state.heatmapEnd,
                        levels = state.heatmapLevels,
                        weekStart = state.preferences.weekStartsOn,
                        markedDate = state.today,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    HeatmapLegend(modifier = Modifier.align(Alignment.End))
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            SectionHeader(title = stringResource(R.string.habit_this_month))
            Spacer(Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                StatTile(
                    label = stringResource(R.string.habit_consistency),
                    value = state.monthRate.percent?.let { "$it%" }
                        ?: stringResource(R.string.habit_no_data_short),
                    caption = if (state.monthRate.hasData) {
                        "${state.monthRate.completed} / ${state.monthRate.scheduled} days"
                    } else {
                        stringResource(R.string.habit_no_data)
                    },
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.habit_best_streak),
                    value = state.bestStreak.toString(),
                    caption = "days",
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.averageValue != null) {
                Spacer(Modifier.height(Spacing.xs))
                StatTile(
                    label = stringResource(R.string.habit_average),
                    value = state.averageValue!!.trim(),
                    caption = habit.unit ?: "",
                )
            }

            Spacer(Modifier.height(Spacing.dockClearance))
        }
    }

    if (showTarget && habitOrNull != null) {
        HabitTargetSheet(
            type = habitOrNull.type,
            target = habitOrNull.targetValue,
            unit = habitOrNull.unit,
            goalDirection = habitOrNull.goalDirection,
            onDismiss = { showTarget = false },
            onApply = viewModel::setMeasurement,
        )
    }
    if (showRecurrence && habitOrNull != null) {
        RecurrencePickerSheet(
            current = state.rule,
            anchor = habitOrNull.startDate,
            weekStart = state.preferences.weekStartsOn,
            onDismiss = { showRecurrence = false },
            onSelect = viewModel::setRecurrence,
        )
    }
    if (showReminderTime) {
        CadenceTimePickerDialog(
            initial = state.reminder?.timeOfDay,
            use24Hour = state.preferences.timeFormat != dev.achyutem.cadence.core.datastore.TimeFormat.TWELVE_HOUR,
            onDismiss = { showReminderTime = false },
            onSelect = { time ->
                viewModel.setReminder(
                    time?.let {
                        dev.achyutem.cadence.core.database.entity.ReminderEntity(timeOfDay = it)
                    }
                )
            },
        )
    }
}

/** A tappable label and value, matching the task detail rows. */
@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(dev.achyutem.cadence.core.designsystem.token.Radius.shapeSm)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm, horizontal = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EditableName(value: String, onValueChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    androidx.compose.foundation.text.BasicTextField(
        value = text,
        onValueChange = { text = it; onValueChange(it) },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A single number with a label above and a caption below. */
@Composable
private fun StatTile(
    label: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    CadenceCard(modifier = modifier, contentPadding = Spacing.sm) {
        Column {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.tabularFigures,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * What this habit measures, in one line: "30 min", "8 glasses", "At most 2", "Done".
 *
 * The direction is only mentioned when it is the unusual one. Almost every habit is "at least",
 * and prefixing every row with it would make the exceptions harder to spot, not easier.
 */
@Composable
private fun Habit.measurementSummary(): String {
    if (type == HabitType.BOOLEAN) return stringResource(R.string.habit_type_boolean)
    val amount = targetValue.trim()
    val suffix = when (type) {
        HabitType.DURATION -> " " + stringResource(R.string.habit_unit_minutes)
        HabitType.QUANTITY -> unit?.let { " $it" }.orEmpty()
        else -> ""
    }
    return if (goalDirection == HabitGoalDirection.AT_MOST) {
        stringResource(R.string.habit_goal_at_most) + " " + amount + suffix
    } else {
        amount + suffix
    }
}
