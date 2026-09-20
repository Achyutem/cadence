package dev.achyutem.cadence.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.token.Motion
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.core.time.datesUntilInclusive
import dev.achyutem.cadence.core.time.narrowLabel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.floor

/**
 * The contribution heatmap.
 *
 * ### Why this is drawn, not composed
 *
 * A year is 365 cells. As composables that is 365 layout nodes plus their modifiers, re-measured
 * on every recomposition, which is exactly the thing that makes a heatmap the slowest screen in
 * an app that has one. Here the entire grid is **one `Canvas`**: one layout node, one draw pass,
 * and cell geometry that is pure arithmetic. Adding a second year costs a few hundred more
 * `drawRoundRect` calls and nothing else.
 *
 * The grid is laid out in columns of weeks, the way a calendar reads: each column is one week,
 * each row one weekday, matching the user's "week starts on" preference.
 *
 * Interaction is a single `pointerInput` that converts a tap position back into a date by
 * arithmetic, rather than 365 individual click targets.
 *
 * The component owns its own horizontal scrolling. That is not a convenience: the weekday
 * gutter has to stay **pinned** while the grid scrolls under it, and a caller that wrapped the
 * whole thing in a `horizontalScroll` would drag the labels off the screen along with the cells,
 * leaving a grid nobody can read a row of.
 *
 * @param levels intensity 0..4 per date, from `HabitStatistics.heatmapLevels`.
 */
@Composable
fun Heatmap(
    startDate: LocalDate,
    endDate: LocalDate,
    levels: Map<LocalDate, Int>,
    weekStart: DayOfWeek,
    modifier: Modifier = Modifier,
    cellSize: Dp = 13.dp,
    cellGap: Dp = 3.dp,
    onDateClick: ((LocalDate) -> Unit)? = null,
    /** Highlighted with a ring, normally today. */
    markedDate: LocalDate? = null,
    /** Start scrolled to the most recent week, which is what anyone looks at first. */
    startAtEnd: Boolean = true,
) {
    if (endDate < startDate) return

    val palette = CadenceTheme.colors.heatmapLevels
    val density = LocalDensity.current
    val markRing = MaterialTheme.colorScheme.onSurface

    // The first column starts on the week containing startDate, so every column is a full week
    // and weekdays line up horizontally across the whole grid.
    val gridStart = remember(startDate, weekStart) {
        val delta = (startDate.dayOfWeek.value - weekStart.value + 7) % 7
        startDate.minusDays(delta.toLong())
    }
    val weekCount = remember(gridStart, endDate) {
        (java.time.temporal.ChronoUnit.DAYS.between(gridStart, endDate) / 7 + 1).toInt()
    }

    val cellPx = with(density) { cellSize.toPx() }
    val gapPx = with(density) { cellGap.toPx() }
    val stepPx = cellPx + gapPx

    val gridWidth = with(density) { (weekCount * stepPx - gapPx).toDp() }
    val gridHeight = with(density) { (7 * stepPx - gapPx).toDp() }

    val description = remember(levels, startDate, endDate) {
        val active = levels.values.count { it > 0 }
        "Activity heatmap, $active active days between $startDate and $endDate"
    }

    val scrollState = rememberScrollState()
    // Keyed on `maxValue`, not on the data: on the first composition the scroll range is still 0
    // because the row has not been measured, so scrolling then is a no-op. Re-running when the
    // extent becomes known is what actually lands the view on the most recent week.
    LaunchedEffect(scrollState.maxValue, startAtEnd) {
        if (startAtEnd && scrollState.maxValue > 0) scrollState.scrollTo(scrollState.maxValue)
    }

    // The weekday gutter is a sibling of the scrolling area, not inside it.
    Row(modifier = modifier) {
        Column {
            // Blank space matching the month-label row, so the weekday column lines up with the
            // first row of cells rather than with the labels above them.
            Spacer(Modifier.height(MONTH_LABEL_HEIGHT + Spacing.xxs))
            WeekdayLabels(weekStart = weekStart, stepDp = with(density) { stepPx.toDp() })
        }
        Spacer(Modifier.width(Spacing.xxs))

        Column(modifier = Modifier.horizontalScroll(scrollState)) {
            MonthLabels(
                gridStart = gridStart,
                weekCount = weekCount,
                stepDp = with(density) { stepPx.toDp() },
            )
            Spacer(Modifier.height(Spacing.xxs))

            Canvas(
                modifier = Modifier
                    .width(gridWidth)
                    .height(gridHeight)
                    .semantics { contentDescription = description }
                    .then(
                        if (onDateClick == null) {
                            Modifier
                        } else {
                            Modifier.pointerInput(gridStart, weekCount, stepPx) {
                                detectTapGestures { offset ->
                                    // One hit test for the whole grid: position → cell → date.
                                    val column = floor(offset.x / stepPx).toInt()
                                    val row = floor(offset.y / stepPx).toInt()
                                    if (column in 0 until weekCount && row in 0..6) {
                                        val date = gridStart.plusDays((column * 7L) + row)
                                        if (date >= startDate && date <= endDate) onDateClick(date)
                                    }
                                }
                            }
                        }
                    ),
            ) {
                val radius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
                for (column in 0 until weekCount) {
                    for (row in 0..6) {
                        val date = gridStart.plusDays((column * 7L) + row)
                        // Cells outside the requested range are not drawn at all, which is what
                        // gives the grid its ragged first and last columns, same as GitHub.
                        if (date < startDate || date > endDate) continue

                        val level = levels[date] ?: 0
                        drawRoundRect(
                            color = palette[level.coerceIn(0, palette.lastIndex)],
                            topLeft = Offset(column * stepPx, row * stepPx),
                            size = Size(cellPx, cellPx),
                            cornerRadius = radius,
                        )
                        if (date == markedDate) {
                            drawRoundRect(
                                color = markRing,
                                topLeft = Offset(column * stepPx, row * stepPx),
                                size = Size(cellPx, cellPx),
                                cornerRadius = radius,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 1.5.dp.toPx(),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Month names above the grid, positioned over the first week of each month.
 *
 * A label is skipped when it would collide with the previous one, which happens with narrow cells
 * and short months, better a missing label than two overlapping ones.
 */
@Composable
private fun MonthLabels(gridStart: LocalDate, weekCount: Int, stepDp: Dp) {
    val density = LocalDensity.current
    val labels = remember(gridStart, weekCount) {
        buildList {
            var lastMonth = -1
            var lastColumn = -MIN_LABEL_GAP_COLUMNS
            for (column in 0 until weekCount) {
                val date = gridStart.plusDays(column * 7L)
                if (date.monthValue != lastMonth && column - lastColumn >= MIN_LABEL_GAP_COLUMNS) {
                    add(column to date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()))
                    lastColumn = column
                }
                lastMonth = date.monthValue
            }
        }
    }

    Box(modifier = Modifier.height(MONTH_LABEL_HEIGHT)) {
        labels.forEach { (column, name) ->
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = stepDp * column),
            )
        }
    }
}

@Composable
private fun WeekdayLabels(weekStart: DayOfWeek, stepDp: Dp) {
    Column(modifier = Modifier.width(WEEKDAY_GUTTER)) {
        (0..6).forEach { row ->
            val day = DayOfWeek.of((weekStart.value - 1 + row) % 7 + 1)
            Box(
                modifier = Modifier.height(stepDp),
                contentAlignment = Alignment.CenterStart,
            ) {
                // Only alternate rows are labelled; seven stacked labels at this size is noise.
                if (row % 2 == 1) {
                    Text(
                        text = day.narrowLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The "Less … More" key. */
@Composable
fun HeatmapLegend(modifier: Modifier = Modifier) {
    val palette = CadenceTheme.colors.heatmapLevels
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "Less",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        palette.forEach { color ->
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(Radius.shapeXs)
                    .background(color),
            )
        }
        Text(
            text = "More",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Width reserved for the pinned weekday column beside the grid. */
private val WEEKDAY_GUTTER = 16.dp

/** Height of the month-label strip, matched by the weekday column's leading spacer. */
private val MONTH_LABEL_HEIGHT = 14.dp

/** Minimum columns between two month labels before one is dropped. */
private const val MIN_LABEL_GAP_COLUMNS = 3
