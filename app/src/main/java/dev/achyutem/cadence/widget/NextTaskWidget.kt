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
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.achyutem.cadence.core.datastore.TimeFormat
import dev.achyutem.cadence.core.time.formatHourMinute

/**
 * One task: the next thing due.
 *
 * The smallest useful widget, and the one that answers the most common glance, not "what is on
 * my list" but "what is next". Timed tasks win over untimed ones; overdue tasks win over both.
 */
class NextTaskWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    /**
     * Loads once for the first frame, then follows the database.
     *
     * The snapshot is collected **inside** `provideContent`, not captured outside it. Glance keeps
     * a session alive while the widget is on screen and `update()` recomposes that session rather
     * than re-running this function, so a value read out here is captured once and redrawn
     * forever. See `widgetSnapshotFlow`.
     */
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer()
        val initial = container.loadWidgetSnapshot()
        provideContent {
            val flow = remember { container.widgetSnapshotFlow() }
            val snapshot by flow.collectAsState(initial)
            Content(snapshot)
        }
    }

    @Composable
    private fun Content(snapshot: WidgetSnapshot) {
        val colors = snapshot.preferences.widgetColors()
        val use24Hour = snapshot.preferences.timeFormat != TimeFormat.TWELVE_HOUR
        val task = snapshot.overdue.firstOrNull() ?: snapshot.nextTask
        val overdue = task != null && snapshot.overdue.contains(task)

        WidgetSurface(colors = colors) {
            WidgetHeader(title = if (overdue) "Overdue" else "Next", colors = colors)
            Spacer(modifier = GlanceModifier.height(6.dp))

            if (task == null) {
                WidgetEmpty("Nothing left today", colors)
            } else {
                Column {
                    task.startTime?.let { time ->
                        Text(
                            text = time.formatHourMinute(use24Hour),
                            style = TextStyle(
                                color = colors.accent,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(2.dp))
                    WidgetTaskRow(
                        task = task,
                        colors = colors,
                        use24Hour = use24Hour,
                        showTime = false,
                    )
                }
            }
        }
    }
}

class NextTaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextTaskWidget()
}
