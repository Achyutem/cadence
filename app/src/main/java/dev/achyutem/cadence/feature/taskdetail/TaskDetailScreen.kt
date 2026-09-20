package dev.achyutem.cadence.feature.taskdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.database.entity.ReminderEntity
import dev.achyutem.cadence.core.database.entity.TaskPriority
import dev.achyutem.cadence.core.datastore.TimeFormat
import dev.achyutem.cadence.core.designsystem.component.ButtonTone
import dev.achyutem.cadence.core.designsystem.component.CadenceButton
import dev.achyutem.cadence.core.designsystem.component.CadenceDatePickerDialog
import dev.achyutem.cadence.core.designsystem.component.CadenceDivider
import dev.achyutem.cadence.core.designsystem.component.CadenceIconButton
import dev.achyutem.cadence.core.designsystem.component.CadenceTimePickerDialog
import dev.achyutem.cadence.core.designsystem.component.RecurrencePickerSheet
import dev.achyutem.cadence.core.designsystem.component.SectionHeader
import dev.achyutem.cadence.core.designsystem.component.SegmentedControl
import dev.achyutem.cadence.core.designsystem.component.TaskCheckbox
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.token.Borders
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.core.time.formatHourMinute
import dev.achyutem.cadence.core.time.formatShort
import dev.achyutem.cadence.domain.recurrence.describe
import dev.achyutem.cadence.feature.todos.formatDuration
import java.time.LocalTime

/**
 * One task, editable.
 *
 * Every field writes on change. There is no save button and no cancel, matching the note editor:
 * a half-finished edit that vanishes on back is a worse failure than any edit the user could make
 * by accident, and everything here is reversible by changing it again.
 */
@Composable
fun TaskDetailScreen(
    taskId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskDetailViewModel = viewModel(
        key = "task-$taskId",
        factory = TaskDetailViewModel.factory(taskId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // A task deleted from underneath this screen (from a widget, a notification, another screen)
    // leaves nothing to edit, so the screen closes rather than showing an empty shell.
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showRecurrence by remember { mutableStateOf(false) }

    val task = state.task
    val use24Hour = state.preferences.timeFormat != TimeFormat.TWELVE_HOUR

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
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
                contentDescription = stringResource(R.string.task_delete),
                onClick = { viewModel.delete(onBack) },
                tone = ButtonTone.Danger,
            )
        }
        CadenceDivider()

        if (state.loading || task == null) return@Column

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter),
        ) {
            Spacer(Modifier.height(Spacing.md))

            Row(verticalAlignment = Alignment.Top) {
                TaskCheckbox(
                    checked = task.completed,
                    onCheckedChange = viewModel::setCompleted,
                    partialProgress = if (task.hasSubtasks) task.progress else 0f,
                    contentDescription = stringResource(
                        if (task.completed) R.string.task_mark_incomplete else R.string.task_mark_complete,
                        task.title,
                    ),
                )
                InlineField(
                    value = task.title,
                    onValueChange = viewModel::setTitle,
                    placeholder = stringResource(R.string.task_title_placeholder),
                    textStyle = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Spacer(Modifier.height(Spacing.sm))
            InlineField(
                value = task.notes.orEmpty(),
                onValueChange = viewModel::setNotes,
                placeholder = stringResource(R.string.task_notes_placeholder),
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = false,
            )

            Spacer(Modifier.height(Spacing.lg))
            SectionHeader(title = stringResource(R.string.task_field_priority))
            Spacer(Modifier.height(Spacing.xs))
            SegmentedControl(
                options = TaskPriority.entries,
                selected = task.priority,
                onSelect = viewModel::setPriority,
                label = { stringResource(it.labelRes()) },
            )

            Spacer(Modifier.height(Spacing.lg))
            FieldRow(
                label = stringResource(R.string.task_field_date),
                value = task.scheduledDate?.formatShort() ?: stringResource(R.string.task_field_none),
                onClick = { showDatePicker = true },
            )
            FieldRow(
                label = stringResource(R.string.task_field_time),
                value = task.startTime?.formatHourMinute(use24Hour)
                    ?: stringResource(R.string.task_field_none),
                onClick = { showTimePicker = true },
            )
            if (task.startTime != null) {
                FieldRow(
                    label = stringResource(R.string.task_field_duration),
                    value = task.durationMinutes?.let { formatDuration(it) }
                        ?: stringResource(R.string.task_field_none),
                    onClick = {
                        // Cycles through the useful block lengths. A picker for a number most
                        // people set to 30 and never touch again is friction for its own sake.
                        val next = when (task.durationMinutes) {
                            null -> 15; 15 -> 30; 30 -> 45; 45 -> 60; 60 -> 90; 90 -> 120
                            else -> null
                        }
                        viewModel.setDuration(next)
                    },
                )
            }
            FieldRow(
                label = stringResource(R.string.task_field_repeat),
                value = state.rule?.describe() ?: stringResource(R.string.recurrence_none),
                onClick = { showRecurrence = true },
            )
            FieldRow(
                label = stringResource(R.string.task_field_reminder),
                value = reminderSummary(state.reminder, task.startTime, use24Hour),
                onClick = {
                    // Off, at the time, then 10 and 30 minutes before. Four states cover almost
                    // every reminder anyone sets on a task.
                    val current = state.reminder
                    val next = when {
                        current == null -> ReminderEntity(
                            timeOfDay = task.startTime ?: LocalTime.of(9, 0),
                            leadMinutes = 0,
                        )
                        current.leadMinutes == 0 -> current.copy(leadMinutes = 10)
                        current.leadMinutes == 10 -> current.copy(leadMinutes = 30)
                        else -> null
                    }
                    viewModel.setReminder(next)
                },
            )

            Spacer(Modifier.height(Spacing.lg))
            SectionHeader(
                title = stringResource(R.string.task_subtasks),
                trailing = {
                    if (task.hasSubtasks) {
                        Text(
                            text = "${task.completedSubtaskCount}/${task.subtasks.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            Spacer(Modifier.height(Spacing.xxs))
            task.subtasks.forEach { subtask ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TaskCheckbox(
                        checked = subtask.completed,
                        onCheckedChange = { viewModel.setSubtaskCompleted(subtask, it) },
                        size = 17.dp,
                        contentDescription = stringResource(
                            if (subtask.completed) R.string.task_mark_incomplete else R.string.task_mark_complete,
                            subtask.title,
                        ),
                    )
                    Text(
                        text = subtask.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (subtask.completed) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                    CadenceIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_delete),
                        onClick = { viewModel.deleteSubtask(subtask) },
                    )
                }
            }
            SubtaskComposer(onAdd = viewModel::addSubtask)

            Spacer(Modifier.height(Spacing.huge))
        }
    }

    if (showDatePicker) {
        CadenceDatePickerDialog(
            initial = task?.scheduledDate,
            onDismiss = { showDatePicker = false },
            onSelect = viewModel::setDate,
        )
    }
    if (showTimePicker) {
        CadenceTimePickerDialog(
            initial = task?.startTime,
            use24Hour = use24Hour,
            onDismiss = { showTimePicker = false },
            onSelect = viewModel::setTime,
        )
    }
    if (showRecurrence && task != null) {
        RecurrencePickerSheet(
            current = state.rule,
            anchor = task.scheduledDate ?: state.today,
            weekStart = state.preferences.weekStartsOn,
            onDismiss = { showRecurrence = false },
            onSelect = viewModel::setRecurrence,
        )
    }
}

@Composable
private fun FieldRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.shapeSm)
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
private fun InlineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
) {
    // Local state so typing is not fighting the database round trip on every keystroke.
    var text by remember(value) { mutableStateOf(value) }
    Box(modifier = modifier.fillMaxWidth()) {
        if (text.isEmpty()) {
            Text(text = placeholder, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it; onValueChange(it) },
            singleLine = singleLine,
            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SubtaskComposer(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs)
            .height(38.dp)
            .clip(Radius.shapeSm)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(Borders.hairline, CadenceTheme.colors.border, Radius.shapeSm)
            .padding(horizontal = Spacing.sm),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (text.isEmpty()) {
            Text(
                text = stringResource(R.string.task_add_subtask),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            // Stays focused and clears, so several subtasks are several sentences.
            keyboardActions = KeyboardActions(onDone = { onAdd(text); text = "" }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun reminderSummary(
    reminder: ReminderEntity?,
    startTime: LocalTime?,
    use24Hour: Boolean,
): String = when {
    reminder == null -> stringResource(R.string.task_field_none)
    startTime != null && reminder.leadMinutes == 0 -> stringResource(R.string.reminder_at_time)
    startTime != null -> stringResource(R.string.reminder_before, reminder.leadMinutes)
    else -> stringResource(R.string.reminder_at, reminder.timeOfDay.formatHourMinute(use24Hour))
}

private fun TaskPriority.labelRes(): Int = when (this) {
    TaskPriority.NONE -> R.string.priority_none_short
    TaskPriority.LOW -> R.string.priority_low
    TaskPriority.MEDIUM -> R.string.priority_medium
    TaskPriority.HIGH -> R.string.priority_high
}
