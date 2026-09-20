package dev.achyutem.cadence.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
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
import dev.achyutem.cadence.core.datastore.UserPreferences
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * The contribution heatmap, on the home screen.
 *
 * ### Why this one is built from nested rows instead of a Canvas
 *
 * The in-app heatmap is a single `Canvas` because 365 composables would be too many layout nodes.
 * Glance has no Canvas at all — a widget is a `RemoteViews` tree — so the grid here is literal
 * boxes, and that puts a hard ceiling on cell count: `RemoteViews` has a real size limit and
 * blowing it makes the launcher silently drop the widget.
 *
 * So this shows **13 weeks**, not a year: 91 boxes, comfortably inside the limit, and about as
 * much as is legible at widget scale anyway. The colours come from the same accent-derived ramp
 * as the app's version, so the two read as the same object at different sizes.
 */
class HeatmapWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Responsive(setOf(MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer()
        val habitId = container.habitDao.observeActive().first().firstOrNull()?.id
        val snapshot = habitId?.let { container.loadHabitSnapshot(it, WEEKS * 7L) }
        provideContent { Content(snapshot) }
    }

    @Composable
    private fun Content(snapshot: HabitWidgetSnapshot?) {
        val colors = (snapshot?.preferences ?: UserPreferences.Default).widgetColors()
        val habit = snapshot?.habit

        WidgetSurface(colors = colors) {
            if (habit == null) {
                WidgetEmpty("No habits yet", colors)
                return@WidgetSurface
            }

            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = habit.name,
                    maxLines = 1,
                    style = TextStyle(
                        color = colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (snapshot.currentStreak > 0) {
                    Text(
                        text = "${snapshot.currentStreak}d",
                        style = TextStyle(color = colors.accent, fontSize = 13.sp),
                    )
                }
            }
            Spacer(modifier = GlanceModifier.height(8.dp))

            val weekStart = snapshot.preferences.weekStartsOn
            val gridStart = snapshot.heatmapStart.let { start ->
                val delta = (start.dayOfWeek.value - weekStart.value + 7) % 7
                start.minusDays(delta.toLong())
            }

            // Rows are weekdays, columns are weeks — the same orientation as the app.
            Column {
                for (row in 0..6) {
                    Row {
                        for (column in 0 until WEEKS) {
                            val date: LocalDate = gridStart.plusDays(column * 7L + row)
                            val visible = date >= snapshot.heatmapStart && date <= snapshot.today
                            val level = if (visible) snapshot.heatmapLevels[date] ?: 0 else 0
                            Box(
                                modifier = GlanceModifier
                                    .size(CELL.dp)
                                    .cornerRadius(2.dp)
                                    .background(
                                        if (visible) {
                                            colors.heatmap[level.coerceIn(0, colors.heatmap.lastIndex)]
                                        } else {
                                            colors.background
                                        },
                                    ),
                            ) {}
                            Spacer(modifier = GlanceModifier.width(GAP.dp))
                        }
                    }
                    Spacer(modifier = GlanceModifier.height(GAP.dp))
                }
            }
        }
    }

    companion object {
        /** 13 weeks × 7 = 91 cells. See the class note on the RemoteViews size limit. */
        const val WEEKS = 13
        const val CELL = 13
        const val GAP = 3

        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 180.dp)
    }
}

class HeatmapWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HeatmapWidget()
}
