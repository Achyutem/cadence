package dev.achyutem.cadence.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.flow.first
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.datastore.preferences.core.longPreferencesKey
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.database.entity.HabitType

/**
 * One habit, chosen by the user, with its streak and a stepper.
 *
 * ### Configuration
 *
 * The habit id lives in the widget's own Glance state, keyed per widget instance, so several of
 * these can sit on the home screen pointing at different habits. Until one is chosen the widget
 * falls back to the **first habit due today**, which means it is useful the moment it is placed
 * rather than showing a "not configured" placeholder, and it still has a configuration activity
 * behind the launcher's resize/configure affordance.
 */
class SingleHabitWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer()

        // Which habit is a one-shot read: it changes only when the user reconfigures the widget,
        // which restarts the session anyway.
        val configured = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[HABIT_ID_KEY]
        val habitId = configured
            ?: container.habitDao.observeActive().first().firstOrNull()?.id

        // Its contents are not. Collected inside the composition so the session follows the
        // database; see `widgetSnapshotFlow` for why that matters.
        val initial = habitId?.let { container.loadHabitSnapshot(it, HEATMAP_DAYS) }
        provideContent {
            val flow = remember(habitId) {
                habitId?.let { container.habitSnapshotFlow(it, HEATMAP_DAYS) }
            }
            val snapshot = flow?.collectAsState(initial)?.value ?: initial
            Content(snapshot)
        }
    }

    @Composable
    private fun Content(snapshot: HabitWidgetSnapshot?) {
        val colors = (snapshot?.preferences ?: dev.achyutem.cadence.core.datastore.UserPreferences.Default)
            .widgetColors()
        val habit = snapshot?.habit

        WidgetSurface(colors = colors) {
            if (habit == null) {
                WidgetEmpty("No habit yet", colors)
                return@WidgetSurface
            }

            Text(
                text = habit.name,
                maxLines = 1,
                style = TextStyle(
                    color = colors.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            if (snapshot.currentStreak > 0) {
                Text(
                    text = "${snapshot.currentStreak} day streak",
                    style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp),
                )
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = if (habit.type == HabitType.BOOLEAN) {
                            if (habit.completed) "Done" else "Not yet"
                        } else {
                            habit.formatProgress()
                        },
                        style = TextStyle(
                            color = if (habit.completed) colors.accent else colors.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
                if (habit.type != HabitType.BOOLEAN) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_minus),
                        contentDescription = "Subtract from ${habit.name}",
                        colorFilter = androidx.glance.ColorFilter.tint(colors.onSurfaceVariant),
                        modifier = GlanceModifier
                            .size(30.dp)
                            .clickable(
                                actionRunCallback<StepHabitAction>(habitParams(habit.id, -1f)),
                            ),
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                }
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
                        .size(30.dp)
                        .clickable(actionRunCallback<StepHabitAction>(habitParams(habit.id))),
                )
            }

            if (habit.type != HabitType.BOOLEAN) {
                Spacer(modifier = GlanceModifier.height(8.dp))
                WidgetProgressBar(progress = habit.progress, colors = colors, height = 5)
            }
        }
    }

    companion object {
        val HABIT_ID_KEY = longPreferencesKey("widget_habit_id")
        const val HEATMAP_DAYS = 1L

        /** Point an existing widget instance at a habit. */
        suspend fun configure(context: Context, glanceId: GlanceId, habitId: Long) {
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[HABIT_ID_KEY] = habitId
            }
            SingleHabitWidget().update(context, glanceId)
        }
    }
}

class SingleHabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SingleHabitWidget()
}
