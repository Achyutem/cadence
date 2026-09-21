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
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
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
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.database.entity.HabitType
import dev.achyutem.cadence.core.datastore.UserPreferences
import dev.achyutem.cadence.core.time.narrowLabel
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** How much history the calendar widget shows. */
enum class HabitCalendarPeriod { WEEK, MONTH }

/**
 * A habit as a calendar, in week or month mode.
 *
 * ### Why this exists alongside the heatmap widget
 *
 * The heatmap widget shows a rolling thirteen weeks, which answers "how consistent have I been"
 * but not "how is *this month* going". A month grid aligned to real week columns answers the
 * second question, and it is the view people actually check against a monthly goal.
 *
 * ### Sizing
 *
 * Cells are sized from the widget's own width rather than being fixed, so a wider widget draws a
 * bigger grid instead of the same small one floating in empty space. That was the concrete
 * complaint about the first heatmap widget: it rendered a fixed small grid at every size.
 *
 * Month mode draws a real calendar: weekday columns, leading blanks for the first week, and one
 * cell per day of the month. Week mode draws a single row of seven, large enough to tap-read from
 * across a desk.
 */
class HabitCalendarWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    /**
     * Exact, not responsive.
     *
     * `SizeMode.Responsive` hands `LocalSize` the nearest *declared* bucket rather than the real
     * widget size, so a grid sized from it draws for 250dp while sitting in a 360dp widget. This
     * one scales continuously, so it needs the truth.
     */
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = context.appContainer()
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)

        val habitId = prefs[HABIT_ID_KEY]
            ?: container.habitDao.observeActive().first().firstOrNull()?.id
        val period = prefs[PERIOD_KEY]
            ?.let { runCatching { HabitCalendarPeriod.valueOf(it) }.getOrNull() }
            ?: HabitCalendarPeriod.MONTH

        // A month grid needs the whole month plus the leading partial week.
        val days = if (period == HabitCalendarPeriod.MONTH) 40L else 14L
        val initial = habitId?.let { container.loadHabitSnapshot(it, days) }

        provideContent {
            val flow = remember(habitId, days) {
                habitId?.let { container.habitSnapshotFlow(it, days) }
            }
            Content(flow?.collectAsState(initial)?.value ?: initial, period)
        }
    }

    @Composable
    private fun Content(snapshot: HabitWidgetSnapshot?, period: HabitCalendarPeriod) {
        val colors = (snapshot?.preferences ?: UserPreferences.Default).widgetColors()
        val habit = snapshot?.habit
        val size = LocalSize.current

        WidgetSurface(colors = colors) {
            if (habit == null) {
                WidgetEmpty("No habits yet", colors)
                return@WidgetSurface
            }

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = habit.name,
                        maxLines = 1,
                        style = TextStyle(
                            color = colors.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Text(
                        text = if (period == HabitCalendarPeriod.MONTH) {
                            snapshot.today.month.getDisplayName(
                                java.time.format.TextStyle.FULL,
                                java.util.Locale.getDefault(),
                            )
                        } else {
                            "This week"
                        },
                        style = TextStyle(color = colors.onSurfaceVariant, fontSize = 11.sp),
                    )
                }
                if (snapshot.currentStreak > 0) {
                    Text(
                        text = "${snapshot.currentStreak}d",
                        style = TextStyle(
                            color = colors.accent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
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
                        .size(26.dp)
                        .clickable(actionRunCallback<StepHabitAction>(habitParams(habit.id))),
                )
            }

            Spacer(modifier = GlanceModifier.height(8.dp))

            when (period) {
                HabitCalendarPeriod.WEEK -> WeekRow(snapshot, colors, rowHeight(size.height.value, 1))
                HabitCalendarPeriod.MONTH -> MonthGrid(snapshot, colors, size)
            }
        }
    }

    @Composable
    private fun WeekRow(snapshot: HabitWidgetSnapshot, colors: WidgetColors, cell: Float) {
        val weekStart = snapshot.preferences.weekStartsOn
        val start = snapshot.today.startOfWeekCompat(weekStart)

        Column {
            WeekdayHeader(weekStart, colors)
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                repeat(7) { index ->
                    DayCell(start.plusDays(index.toLong()), snapshot, colors, cell)
                }
            }
        }
    }

    @Composable
    private fun MonthGrid(
        snapshot: HabitWidgetSnapshot,
        colors: WidgetColors,
        size: DpSize,
    ) {
        val weekStart = snapshot.preferences.weekStartsOn
        val month = YearMonth.from(snapshot.today)
        val first = month.atDay(1)
        val leading = (first.dayOfWeek.value - weekStart.value + 7) % 7
        val gridStart = first.minusDays(leading.toLong())
        val weeks = (leading + month.lengthOfMonth() + 6) / 7

        Column {
            WeekdayHeader(weekStart, colors)
            // At most six week rows plus the weekday header: seven children, inside the limit.
            repeat(weeks) { week ->
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    repeat(7) { index ->
                        val date = gridStart.plusDays(week * 7L + index)
                        if (YearMonth.from(date) == month) {
                            DayCell(date, snapshot, colors, rowHeight(size.height.value, weeks))
                        } else {
                            // Days belonging to the neighbouring month are left blank rather
                            // than dimmed: this is a view of one month, not a calendar page.
                            Box(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .height(rowHeight(size.height.value, weeks).dp),
                            ) {}
                        }
                    }
                }
            }
        }
    }

    /**
     * The weekday gutter.
     *
     * Seven weighted cells rather than seven fixed cells with spacers between them. Glance turns
     * a container into one of its generated layouts, and those top out at **ten children**;
     * anything past the tenth is dropped with no error. Seven cells plus six spacers is thirteen,
     * which is why the first version of this grid rendered Monday to Friday and stopped.
     */
    @Composable
    private fun WeekdayHeader(weekStart: DayOfWeek, colors: WidgetColors) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            repeat(7) { index ->
                val day = DayOfWeek.of((weekStart.value - 1 + index) % 7 + 1)
                Box(
                    modifier = GlanceModifier.defaultWeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.narrowLabel(),
                        style = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp),
                    )
                }
            }
        }
        Spacer(modifier = GlanceModifier.height(3.dp))
    }

    /** A [RowScope] receiver so the cell can claim an equal share of the row's width. */
    @Composable
    private fun RowScope.DayCell(
        date: LocalDate,
        snapshot: HabitWidgetSnapshot,
        colors: WidgetColors,
        cell: Float,
    ) {
        val future = date > snapshot.today
        val level = snapshot.heatmapLevels[date] ?: 0
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .height(cell.dp)
                .padding(horizontal = (GAP / 2).dp, vertical = (GAP / 2).dp),
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(4.dp)
                    .background(
                        when {
                            future -> colors.background
                            else -> colors.heatmap[level.coerceIn(0, colors.heatmap.lastIndex)]
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // The date number only fits once the cell is reasonably tall; below that the
                // colour alone carries the information, which is all a heat grid needs.
                if (cell >= 22f) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = TextStyle(
                            color = if (level >= 3) colors.onAccent else colors.onSurfaceVariant,
                            fontSize = 10.sp,
                        ),
                    )
                }
            }
        }
    }

    companion object {
        val HABIT_ID_KEY = longPreferencesKey("calendar_habit_id")
        val PERIOD_KEY = stringPreferencesKey("calendar_period")

        const val GAP = 4f

        /** Header, weekday gutter and the surface's own vertical padding. */
        private const val CHROME_HEIGHT = 74f

        /**
         * How tall one week row should be so that [weeks] of them fill the widget.
         *
         * Derived from height rather than width because the grid is already seven weighted
         * columns wide; height is the axis that would otherwise leave the bottom half of a tall
         * widget empty, which is what a 5x5 grid sitting in a 4x3 widget looks like.
         */
        fun rowHeight(widgetHeight: Float, weeks: Int): Float =
            ((widgetHeight - CHROME_HEIGHT) / weeks.coerceAtLeast(1)).coerceIn(14f, 56f)

        suspend fun configure(
            context: Context,
            glanceId: GlanceId,
            habitId: Long,
            period: HabitCalendarPeriod,
        ) {
            updateAppWidgetState(context, glanceId) { prefs ->
                prefs[HABIT_ID_KEY] = habitId
                prefs[PERIOD_KEY] = period.name
            }
            HabitCalendarWidget().update(context, glanceId)
        }
    }
}

class HabitCalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitCalendarWidget()
}

/** Local copy of the app's week-start helper; Glance code cannot reach Compose extensions. */
private fun LocalDate.startOfWeekCompat(weekStart: DayOfWeek): LocalDate {
    val delta = (dayOfWeek.value - weekStart.value + 7) % 7
    return minusDays(delta.toLong())
}
