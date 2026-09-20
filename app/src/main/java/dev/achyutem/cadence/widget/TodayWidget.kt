package dev.achyutem.cadence.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.achyutem.cadence.core.time.formatDayAndMonth
import dev.achyutem.cadence.core.time.formatWeekdayFull
import dev.achyutem.cadence.core.datastore.TimeFormat

/**
 * Today, progress plus what is left of the day.
 *
 * Three sizes from one composition via [SizeMode.Responsive]: a small tile showing only the
 * fraction, a medium one that adds the bar and the next few tasks, and a large one that shows
 * habits too. Writing them as one composable that reads [LocalSize] rather than three widgets
 * keeps the layouts honest with each other; there is no way for the medium version to drift from
 * the large one, because they are the same code.
 */
class TodayWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(SMALL, MEDIUM, LARGE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = context.appContainer().loadWidgetSnapshot()
        provideContent { Content(snapshot) }
    }

    @Composable
    private fun Content(snapshot: WidgetSnapshot) {
        val colors = snapshot.preferences.widgetColors()
        val size = LocalSize.current
        val use24Hour = snapshot.preferences.timeFormat != TimeFormat.TWELVE_HOUR

        WidgetSurface(colors = colors) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = snapshot.today.formatWeekdayFull(),
                            style = TextStyle(
                                color = colors.onSurface,
                                fontSize = if (size.height >= LARGE.height) 20.sp else 16.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        if (size.height >= MEDIUM.height) {
                            Text(
                                text = snapshot.today.formatDayAndMonth(),
                                style = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp),
                            )
                        }
                    }
                    // Nothing scheduled shows no number, matching the app's progress card.
                    snapshot.taskProgress?.let { progress ->
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = TextStyle(
                                color = colors.accent,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(8.dp))
                WidgetProgressBar(
                    progress = snapshot.taskProgress ?: 0f,
                    colors = colors,
                    height = 5,
                )

                if (size.height >= MEDIUM.height) {
                    Spacer(modifier = GlanceModifier.height(4.dp))
                    Text(
                        text = if (snapshot.totalTasks == 0) {
                            "Nothing scheduled"
                        } else {
                            "${snapshot.completedTasks} of ${snapshot.totalTasks} complete"
                        },
                        style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp),
                    )
                }

                if (size.height >= MEDIUM.height) {
                    val limit = if (size.height >= LARGE.height) 5 else 2
                    val remaining = snapshot.remainingTasks.take(limit)
                    if (remaining.isNotEmpty()) {
                        Spacer(modifier = GlanceModifier.height(10.dp))
                        remaining.forEach { task ->
                            WidgetTaskRow(task = task, colors = colors, use24Hour = use24Hour)
                        }
                    }
                }

                if (size.height >= LARGE.height && snapshot.habits.isNotEmpty()) {
                    Spacer(modifier = GlanceModifier.height(10.dp))
                    WidgetHeader(
                        title = "Habits",
                        colors = colors,
                        trailing = "${snapshot.completedHabits}/${snapshot.habits.size}",
                    )
                    snapshot.habits.take(3).forEach { habit ->
                        WidgetHabitRow(habit = habit, colors = colors)
                    }
                }
            }
        }
    }

    companion object {
        // Keyed to the launcher's cell grid rather than to arbitrary numbers: roughly 2x1, 4x2
        // and 4x4 on a typical phone.
        val SMALL = DpSize(140.dp, 60.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/**
 * The shared widget container: rounded, bordered, using the app's surface colour.
 *
 * Every widget uses this so they read as one family on the home screen, and so the background
 * treatment is changed in one place if a launcher turns out to need something different.
 */
@Composable
fun WidgetSurface(
    colors: WidgetColors,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(colors.background)
            .padding(14.dp)
            .clickable(openAppAction()),
    ) {
        content()
    }
}
