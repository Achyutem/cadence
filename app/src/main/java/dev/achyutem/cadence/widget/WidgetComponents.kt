package dev.achyutem.cadence.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.achyutem.cadence.R
import dev.achyutem.cadence.domain.habit.Habit
import dev.achyutem.cadence.domain.task.Task
import dev.achyutem.cadence.core.database.entity.HabitType
import dev.achyutem.cadence.core.time.formatHourMinute

/**
 * The widget design system.
 *
 * Deliberately parallel to the app's components rather than shared with them: Glance has its own
 * `GlanceModifier`, its own layout primitives and no `CompositionLocal`, so nothing from
 * `designsystem/component` can be reused directly. What *is* shared is the thing that matters,
 * the colours in [WidgetColors] and the domain types, so a widget looks and reads like the app
 * without pretending the two toolkits are one.
 */

/** Small uppercase heading, matching `SectionHeader` in the app. */
@Composable
fun WidgetHeader(
    title: String,
    colors: WidgetColors,
    trailing: String? = null,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = TextStyle(
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = TextStyle(
                    color = colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

/**
 * A progress bar.
 *
 * Uses Glance's own `LinearProgressIndicator`, which is the only primitive here that can express
 * a fraction.
 *
 * An earlier version tried to build this from two weighted boxes in a row. That cannot work:
 * Glance's `defaultWeight()` takes no weight *value*, so two weighted cells always split 50/50
 * regardless of the progress passed in; a bar that looked plausible in code and would have
 * rendered every value as half full.
 */
@Composable
fun WidgetProgressBar(
    progress: Float,
    colors: WidgetColors,
    modifier: GlanceModifier = GlanceModifier,
    height: Int = 6,
) {
    LinearProgressIndicator(
        progress = progress.coerceIn(0f, 1f),
        modifier = modifier.fillMaxWidth().height(height.dp),
        color = colors.accent,
        backgroundColor = colors.progressTrack,
    )
}

/** A task row with a tappable checkbox that never opens the app. */
@Composable
fun WidgetTaskRow(
    task: Task,
    colors: WidgetColors,
    use24Hour: Boolean,
    showTime: Boolean = true,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(
                if (task.completed) R.drawable.ic_widget_check_filled else R.drawable.ic_widget_check_empty,
            ),
            contentDescription = task.title,
            colorFilter = androidx.glance.ColorFilter.tint(
                if (task.completed) colors.accent else colors.onSurfaceVariant,
            ),
            modifier = GlanceModifier
                .size(20.dp)
                .clickable(
                    actionRunCallback<ToggleTaskAction>(taskParams(task.id, !task.completed)),
                ),
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = task.title,
            maxLines = 1,
            style = TextStyle(
                color = if (task.completed) colors.onSurfaceVariant else colors.onSurface,
                fontSize = 13.sp,
            ),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (showTime && task.startTime != null) {
            Text(
                text = task.startTime!!.formatHourMinute(use24Hour),
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
    }
}

/** A habit row with a tappable stepper. */
@Composable
fun WidgetHabitRow(habit: Habit, colors: WidgetColors) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = habit.name,
                maxLines = 1,
                style = TextStyle(color = colors.onSurface, fontSize = 13.sp),
            )
            if (habit.type != HabitType.BOOLEAN) {
                Text(
                    text = habit.formatProgress(),
                    style = TextStyle(
                        color = if (habit.completed) colors.accent else colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                )
            }
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        Image(
            provider = ImageProvider(
                when {
                    habit.type != HabitType.BOOLEAN -> R.drawable.ic_widget_plus
                    habit.completed -> R.drawable.ic_widget_check_filled
                    else -> R.drawable.ic_widget_check_empty
                },
            ),
            contentDescription = habit.name,
            colorFilter = androidx.glance.ColorFilter.tint(
                if (habit.completed) colors.accent else colors.onSurfaceVariant,
            ),
            modifier = GlanceModifier
                .size(22.dp)
                .clickable(actionRunCallback<StepHabitAction>(habitParams(habit.id))),
        )
    }
}

/** The empty state every widget falls back to, so none of them ever renders a blank rectangle. */
@Composable
fun WidgetEmpty(message: String, colors: WidgetColors) {
    Box(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
        )
    }
}
