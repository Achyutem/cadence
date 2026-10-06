package dev.achyutem.cadence.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
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
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
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
 * Glance has no Canvas at all; a widget is a `RemoteViews` tree, so the grid here is literal
 * boxes, and that puts a hard ceiling on cell count: `RemoteViews` has a real size limit and
 * blowing it makes the launcher silently drop the widget.
 *
 * So this shows **13 weeks**, not a year: 91 boxes, comfortably inside the limit, and about as
 * much as is legible at widget scale anyway. The colours come from the same accent-derived ramp
 * as the app's version, so the two read as the same object at different sizes.
 */
class HeatmapWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition
    /**
     * Exact, not responsive: the grid scales with the widget, and `SizeMode.Responsive` reports
     * the nearest declared bucket rather than the real size. See [HabitCalendarWidget].
     */
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer()
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        // Configured habit first, then the first active one. A widget placed before any habit
        // exists still has something to show once one does.
        val habitId = prefs[HABIT_ID_KEY]
            ?: container.habitDao.observeActive().first().firstOrNull()?.id
        val initial = habitId?.let { container.loadHabitSnapshot(it, WEEKS * 7L) }
        provideContent {
            val flow = remember(habitId) {
                habitId?.let { container.habitSnapshotFlow(it, WEEKS * 7L) }
            }
            Content(flow?.collectAsState(initial)?.value ?: initial)
        }
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

            // Rows are weekdays, columns are weeks, the same orientation as the app.
            //
            // Thirteen week columns cannot be thirteen children of one container: Glance renders
            // a container as one of its generated layouts and those hold at most **ten**
            // children, silently dropping the rest. So the weeks are split into groups and the
            // grid is built column-major, one Column of seven days per week, which also lets the
            // columns share the width by weight and scale with the widget.
            val height = androidx.glance.LocalSize.current.height.value
            val cell = ((height - CHROME_HEIGHT) / 7f - GAP).coerceIn(6f, 22f)

            Row(modifier = GlanceModifier.fillMaxWidth()) {
                for (group in 0 until GROUPS) {
                    Row(modifier = GlanceModifier.defaultWeight()) {
                        for (offset in 0 until WEEKS_PER_GROUP) {
                            val column = group * WEEKS_PER_GROUP + offset
                            if (column >= WEEKS) break
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                for (row in 0..6) {
                                    val date: LocalDate = gridStart.plusDays(column * 7L + row)
                                    val visible =
                                        date >= snapshot.heatmapStart && date <= snapshot.today
                                    val level =
                                        if (visible) snapshot.heatmapLevels[date] ?: 0 else 0
                                    Box(
                                        modifier = GlanceModifier
                                            .fillMaxWidth()
                                            .height(cell.dp)
                                            .padding((GAP / 2).dp),
                                    ) {
                                        Box(
                                            modifier = GlanceModifier
                                                .fillMaxSize()
                                                .cornerRadius(2.dp)
                                                .background(
                                                    if (visible) {
                                                        colors.heatmap[
                                                            level.coerceIn(
                                                                0,
                                                                colors.heatmap.lastIndex,
                                                            ),
                                                        ]
                                                    } else {
                                                        colors.clear
                                                    },
                                                ),
                                        ) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        val HABIT_ID_KEY = longPreferencesKey("heatmap_habit_id")

        /** 13 weeks × 7 = 91 cells. See the class note on the RemoteViews size limit. */
        const val WEEKS = 13
        const val GAP = 3f

        /**
         * Week columns per inner row, and how many inner rows.
         *
         * Glance containers hold ten children. Seven columns per group keeps every container
         * inside that, and two groups cover the thirteen weeks.
         */
        const val WEEKS_PER_GROUP = 7
        const val GROUPS = (WEEKS + WEEKS_PER_GROUP - 1) / WEEKS_PER_GROUP

        /** The title row plus the surface's own vertical padding. */
        private const val CHROME_HEIGHT = 62f

        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 180.dp)
        val WIDE = DpSize(340.dp, 200.dp)

        suspend fun configure(context: Context, glanceId: GlanceId, habitId: Long) {
            updateAppWidgetState(context, glanceId) { prefs -> prefs[HABIT_ID_KEY] = habitId }
            HeatmapWidget().update(context, glanceId)
        }
    }
}

class HeatmapWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HeatmapWidget()
}
