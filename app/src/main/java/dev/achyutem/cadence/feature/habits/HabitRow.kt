package dev.achyutem.cadence.feature.habits

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.database.entity.HabitType
import dev.achyutem.cadence.core.designsystem.component.ButtonTone
import dev.achyutem.cadence.core.designsystem.component.CadenceIconButton
import dev.achyutem.cadence.core.designsystem.component.CadenceProgressBar
import dev.achyutem.cadence.core.designsystem.component.TaskCheckbox
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.theme.tabularFigures
import dev.achyutem.cadence.core.designsystem.token.Motion
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.domain.habit.Habit

/**
 * One habit for one day.
 *
 * Boolean habits get a checkbox; the same control as a task, because it is the same gesture.
 * Everything else gets −/+ steppers and a progress bar, so logging four glasses of water is four
 * taps in one place rather than four trips into a detail screen.
 *
 * A habit that is not scheduled today is dimmed rather than hidden: hiding makes the list look
 * like it lost something, and showing it as due would be a lie.
 */
@Composable
fun HabitRow(
    habit: Habit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val valueColor by animateColorAsState(
        targetValue = if (habit.completed) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(CadenceTheme.duration(Motion.STANDARD)),
        label = "habitValueColor",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.shapeSm)
            .clickable(onClick = onClick)
            .alpha(if (habit.scheduledToday) 1f else 0.45f)
            .padding(vertical = Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (habit.type == HabitType.BOOLEAN) {
                TaskCheckbox(
                    checked = habit.completed,
                    onCheckedChange = { onIncrement() },
                    contentDescription = stringResource(
                        if (habit.completed) R.string.habit_mark_incomplete else R.string.habit_mark_complete,
                        habit.name,
                    ),
                )
            } else {
                Spacer(Modifier.width(Spacing.xxs))
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = Spacing.xxs)) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (habit.type != HabitType.BOOLEAN) {
                    Text(
                        text = habit.formatProgress(),
                        style = MaterialTheme.typography.labelMedium.tabularFigures,
                        color = valueColor,
                    )
                }
            }

            if (habit.type != HabitType.BOOLEAN) {
                CadenceIconButton(
                    icon = Icons.Rounded.Remove,
                    contentDescription = stringResource(R.string.habit_decrement, habit.name),
                    onClick = onDecrement,
                    enabled = habit.todayValue > 0.0,
                )
                CadenceIconButton(
                    icon = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.habit_increment, habit.name),
                    onClick = onIncrement,
                    tone = ButtonTone.Primary,
                )
            }
        }

        if (habit.type != HabitType.BOOLEAN) {
            Spacer(Modifier.height(Spacing.xxs))
            CadenceProgressBar(
                progress = habit.progress,
                height = 4.dp,
                // The bar is decoration here: the value text above already carries the number,
                // and announcing both would read the same thing twice.
                contentDescription = null,
            )
        }
    }
}
