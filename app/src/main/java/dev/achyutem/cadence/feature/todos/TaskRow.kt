package dev.achyutem.cadence.feature.todos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.database.entity.TaskPriority
import dev.achyutem.cadence.core.designsystem.component.TaskCheckbox
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.theme.tabularFigures
import dev.achyutem.cadence.core.designsystem.token.Motion
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.core.time.formatHourMinute
import dev.achyutem.cadence.domain.task.Task

/**
 * One task in a list.
 *
 * Completion is expressed three ways at once, the box fills, the title strikes through, and the
 * whole row fades back, because any one of them alone is either too subtle to notice in a dense
 * list or too loud to live with. Together they say "done" without the row shouting about it.
 *
 * The strike-through and the fade are animated on the same curve as the checkbox, so the row
 * settles as one object rather than as three things that happen to change at once.
 */
@Composable
fun TaskRow(
    task: Task,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    use24Hour: Boolean = true,
    showSubtasks: Boolean = true,
    onToggleSubtask: (Task, Boolean) -> Unit = { _, _ -> },
) {
    val completed = task.completed
    val rowAlpha by animateFloatAsState(
        targetValue = if (completed) 0.55f else 1f,
        animationSpec = tween(CadenceTheme.duration(Motion.STANDARD), easing = Motion.standardEasing),
        label = "taskRowAlpha",
    )
    val titleColor by animateColorAsState(
        targetValue = if (completed) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(CadenceTheme.duration(Motion.STANDARD)),
        label = "taskTitleColor",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Radius.shapeSm)
                .clickable(onClick = onClick)
                .alpha(rowAlpha),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskCheckbox(
                checked = completed,
                onCheckedChange = onToggle,
                partialProgress = if (task.hasSubtasks) task.progress else 0f,
                contentDescription = stringResource(
                    if (completed) R.string.task_mark_incomplete else R.string.task_mark_complete,
                    task.title,
                ),
            )

            Column(modifier = Modifier.weight(1f).padding(vertical = Spacing.xs)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    textDecoration = if (completed) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                TaskMetaRow(task = task, use24Hour = use24Hour)
            }

            PriorityMark(task.priority)
            Spacer(Modifier.width(Spacing.sm))
        }

        if (showSubtasks) {
            AnimatedVisibility(
                visible = task.hasSubtasks,
                enter = fadeIn(tween(CadenceTheme.duration(Motion.QUICK))) +
                    expandVertically(tween(CadenceTheme.duration(Motion.STANDARD))),
                exit = fadeOut(tween(CadenceTheme.duration(Motion.MICRO))) +
                    shrinkVertically(tween(CadenceTheme.duration(Motion.QUICK))),
            ) {
                Column(modifier = Modifier.padding(start = Spacing.xxl)) {
                    task.subtasks.forEach { subtask ->
                        SubtaskRow(
                            subtask = subtask,
                            onToggle = { checked -> onToggleSubtask(subtask, checked) },
                        )
                    }
                }
            }
        }
    }
}

/** Time, duration, subtask count and recurrence, on one quiet line under the title. */
@Composable
private fun TaskMetaRow(task: Task, use24Hour: Boolean) {
    val pieces = buildList {
        task.startTime?.let { add(it.formatHourMinute(use24Hour)) }
        task.durationMinutes?.takeIf { it > 0 }?.let { add(formatDuration(it)) }
        if (task.hasSubtasks) add("${task.completedSubtaskCount}/${task.subtasks.size}")
    }
    if (pieces.isEmpty() && !task.isRecurring) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.padding(top = 1.dp),
    ) {
        if (task.isRecurring) {
            Icon(
                imageVector = Icons.Rounded.Repeat,
                contentDescription = stringResource(R.string.task_recurring),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp),
            )
        }
        if (pieces.isNotEmpty()) {
            Text(
                text = pieces.joinToString(" · "),
                style = MaterialTheme.typography.labelMedium.tabularFigures,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Priority, as a single small dot.
 *
 * No chip, no colour-coded row, no icon with a label. Most tasks have no priority, and the ones
 * that do need to be findable at a glance without turning the list into a traffic light.
 */
@Composable
private fun PriorityMark(priority: TaskPriority) {
    if (priority == TaskPriority.NONE) return
    val color = when (priority) {
        TaskPriority.HIGH -> CadenceTheme.colors.priorityHigh
        TaskPriority.MEDIUM -> CadenceTheme.colors.priorityMedium
        else -> CadenceTheme.colors.priorityLow
    }
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun SubtaskRow(subtask: Task, onToggle: (Boolean) -> Unit) {
    val completed = subtask.completed
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskCheckbox(
            checked = completed,
            onCheckedChange = onToggle,
            size = 16.dp,
            contentDescription = stringResource(
                if (completed) R.string.task_mark_incomplete else R.string.task_mark_complete,
                subtask.title,
            ),
        )
        Text(
            text = subtask.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (completed) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (completed) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** "90m" is harder to read at a glance than "1h 30m". */
internal fun formatDuration(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}
