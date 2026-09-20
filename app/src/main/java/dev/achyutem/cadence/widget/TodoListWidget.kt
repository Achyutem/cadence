package dev.achyutem.cadence.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.datastore.TimeFormat
import dev.achyutem.cadence.domain.task.Task

/**
 * Today's tasks, tickable in place.
 *
 * Uses Glance's `LazyColumn`, so a long list actually scrolls on the home screen rather than
 * being silently truncated; the thing that makes a todo widget usable rather than decorative.
 *
 * Overdue tasks are shown above today's, because an overdue task is the only item here that is
 * already a problem.
 */
class TodoListWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = context.appContainer().loadWidgetSnapshot()
        provideContent { Content(snapshot) }
    }

    @Composable
    private fun Content(snapshot: WidgetSnapshot) {
        val colors = snapshot.preferences.widgetColors()
        val use24Hour = snapshot.preferences.timeFormat != TimeFormat.TWELVE_HOUR
        val items: List<Task> = snapshot.overdue + snapshot.tasks

        WidgetSurface(colors = colors) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    WidgetHeader(
                        title = "Today",
                        colors = colors,
                        trailing = if (items.isEmpty()) null else {
                            "${snapshot.completedTasks}/${snapshot.totalTasks}"
                        },
                    )
                }
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_add),
                    contentDescription = "Add task",
                    colorFilter = androidx.glance.ColorFilter.tint(colors.accent),
                    modifier = GlanceModifier.size(20.dp).clickable(openAppAction()),
                )
            }
            Spacer(modifier = GlanceModifier.height(6.dp))

            if (items.isEmpty()) {
                WidgetEmpty("Nothing scheduled", colors)
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(items, itemId = { it.id }) { task ->
                        WidgetTaskRow(task = task, colors = colors, use24Hour = use24Hour)
                    }
                }
            }
        }
    }

    companion object {
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)
    }
}

class TodoListWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoListWidget()
}
