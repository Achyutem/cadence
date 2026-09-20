package dev.achyutem.cadence.feature.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.datastore.TimeFormat
import dev.achyutem.cadence.core.designsystem.component.CadenceDivider
import dev.achyutem.cadence.core.designsystem.component.CadenceIconButton
import dev.achyutem.cadence.core.designsystem.component.EmptyState
import dev.achyutem.cadence.core.designsystem.component.SegmentedControl
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.theme.tabularFigures
import dev.achyutem.cadence.core.designsystem.token.Borders
import dev.achyutem.cadence.core.designsystem.token.Motion
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.core.time.endOfMonth
import dev.achyutem.cadence.core.time.formatDayAndMonth
import dev.achyutem.cadence.core.time.formatWeekdayFull
import dev.achyutem.cadence.core.time.minutesOfDay
import dev.achyutem.cadence.core.time.narrowLabel
import dev.achyutem.cadence.core.time.startOfMonth
import dev.achyutem.cadence.core.time.startOfWeek
import dev.achyutem.cadence.core.time.weekdayOrder
import dev.achyutem.cadence.domain.task.Task
import dev.achyutem.cadence.feature.todos.TaskRow
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * The calendar: month for overview, week for planning, day for a timeline.
 *
 * All three share one state and one query. Switching view changes the range that is loaded, not
 * the code that loads it.
 */
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val use24Hour = state.preferences.timeFormat != TimeFormat.TWELVE_HOUR

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CadenceIconButton(
                icon = Icons.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
            )
            Text(
                text = periodLabel(state),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(start = Spacing.xxs),
            )
            CadenceIconButton(
                icon = Icons.Rounded.ChevronLeft,
                contentDescription = stringResource(R.string.calendar_previous),
                onClick = viewModel::previous,
            )
            CadenceIconButton(
                icon = Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.calendar_next),
                onClick = viewModel::next,
            )
        }

        SegmentedControl(
            options = CalendarView.entries,
            selected = state.view,
            onSelect = viewModel::setView,
            label = { stringResource(it.labelRes()) },
            modifier = Modifier.padding(horizontal = Spacing.screenGutter),
        )
        Spacer(Modifier.height(Spacing.md))

        when (state.view) {
            CalendarView.MONTH -> MonthGrid(state = state, onSelect = viewModel::select)
            CalendarView.WEEK -> WeekStrip(state = state, onSelect = viewModel::select)
            CalendarView.DAY -> Unit
        }

        if (state.view != CalendarView.DAY) {
            Spacer(Modifier.height(Spacing.md))
            CadenceDivider()
        }

        if (state.view == CalendarView.DAY) {
            DayTimeline(
                tasks = state.selectedTasks,
                isToday = state.anchor == state.today,
                use24Hour = use24Hour,
                onOpenTask = onOpenTask,
                onToggle = viewModel::setCompleted,
            )
        } else {
            SelectedDayList(
                state = state,
                use24Hour = use24Hour,
                onOpenTask = onOpenTask,
                onToggle = viewModel::setCompleted,
            )
        }
    }
}

/**
 * The month grid.
 *
 * Each cell shows the date and, under it, a row of up to three dots: one per task, filled when
 * completed. Dots rather than counts because a month grid is scanned, not read, and "three things,
 * two done" is legible at a glance where "3" is not.
 */
@Composable
private fun MonthGrid(state: CalendarUiState, onSelect: (LocalDate) -> Unit) {
    val month = YearMonth.from(state.anchor)
    val weekStart = state.preferences.weekStartsOn
    val gridStart = month.atDay(1).startOfWeek(weekStart)
    val weeks = ((month.atEndOfMonth().toEpochDay() - gridStart.toEpochDay()) / 7 + 1).toInt()

    Column(modifier = Modifier.padding(horizontal = Spacing.screenGutter)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdayOrder(weekStart).forEach { day ->
                Text(
                    text = day.narrowLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(Spacing.xxs))

        repeat(weeks) { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { index ->
                    val date = gridStart.plusDays(week * 7L + index)
                    DayCell(
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        selected = date == state.anchor,
                        isToday = date == state.today,
                        tasks = state.tasksByDate[date].orEmpty(),
                        hasHabits = date in state.habitDates,
                        onClick = { onSelect(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    isToday: Boolean,
    tasks: List<Task>,
    hasHabits: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        },
        animationSpec = tween(CadenceTheme.duration(Motion.QUICK)),
        label = "dayCell",
    )
    val textColor = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier
            .aspectRatio(0.85f)
            .padding(1.dp)
            .clip(Radius.shapeSm)
            .background(background)
            .then(
                if (isToday && !selected) {
                    Modifier.border(Borders.hairline, MaterialTheme.colorScheme.primary, Radius.shapeSm)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium.tabularFigures,
            color = textColor,
        )
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            tasks.take(3).forEach { task ->
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (task.completed) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        ),
                )
            }
            if (hasHabits) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(CadenceTheme.colors.borderStrong),
                )
            }
        }
    }
}

/** The week strip: seven days across, with the same dot treatment. */
@Composable
private fun WeekStrip(state: CalendarUiState, onSelect: (LocalDate) -> Unit) {
    val weekStart = state.anchor.startOfWeek(state.preferences.weekStartsOn)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenGutter),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        repeat(7) { index ->
            val date = weekStart.plusDays(index.toLong())
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = date.dayOfWeek.narrowLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                DayCell(
                    date = date,
                    inMonth = true,
                    selected = date == state.anchor,
                    isToday = date == state.today,
                    tasks = state.tasksByDate[date].orEmpty(),
                    hasHabits = date in state.habitDates,
                    onClick = { onSelect(date) },
                )
            }
        }
    }
}

/**
 * The day timeline.
 *
 * An hour axis with timed tasks positioned against it, and untimed ones collected underneath.
 * Hours are a fixed height rather than proportional to content, because the point of a timeline is
 * that an empty afternoon *looks* empty.
 */
@Composable
private fun DayTimeline(
    tasks: List<Task>,
    isToday: Boolean,
    use24Hour: Boolean,
    onOpenTask: (Long) -> Unit,
    onToggle: (Task, Boolean) -> Unit,
) {
    val timed = tasks.filter { it.startTime != null }
    val untimed = tasks.filter { it.startTime == null }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screenGutter,
            end = Spacing.screenGutter,
            bottom = Spacing.dockClearance,
        ),
    ) {
        items((START_HOUR..END_HOUR).toList(), key = { "h$it" }) { hour ->
            val hourTasks = timed.filter { it.startTime!!.hour == hour }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = LocalTime.of(hour, 0).let { time ->
                        if (use24Hour) String.format(Locale.getDefault(), "%02d:00", hour)
                        else time.format(java.time.format.DateTimeFormatter.ofPattern("h a", Locale.getDefault()))
                    },
                    style = MaterialTheme.typography.labelMedium.tabularFigures,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(56.dp).padding(top = 6.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    CadenceDivider()
                    if (hourTasks.isEmpty()) {
                        Spacer(Modifier.height(HOUR_HEIGHT))
                    } else {
                        hourTasks.forEach { task ->
                            TaskRow(
                                task = task,
                                onToggle = { checked -> onToggle(task, checked) },
                                onClick = { onOpenTask(task.id) },
                                use24Hour = use24Hour,
                                showSubtasks = false,
                            )
                        }
                    }
                }
            }
        }

        if (untimed.isNotEmpty()) {
            item(key = "untimed-header") {
                Spacer(Modifier.height(Spacing.md))
                dev.achyutem.cadence.core.designsystem.component.SectionHeader(
                    title = stringResource(R.string.calendar_untimed),
                )
            }
            items(untimed, key = { "u${it.id}" }) { task ->
                TaskRow(
                    task = task,
                    onToggle = { checked -> onToggle(task, checked) },
                    onClick = { onOpenTask(task.id) },
                    use24Hour = use24Hour,
                )
            }
        }
    }
}

@Composable
private fun SelectedDayList(
    state: CalendarUiState,
    use24Hour: Boolean,
    onOpenTask: (Long) -> Unit,
    onToggle: (Task, Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screenGutter,
            end = Spacing.screenGutter,
            top = Spacing.sm,
            bottom = Spacing.dockClearance,
        ),
    ) {
        item(key = "date-header") {
            Text(
                text = "${state.anchor.formatWeekdayFull()}, ${state.anchor.formatDayAndMonth()}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.xs))
        }

        if (state.selectedTasks.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.calendar_empty_title),
                    description = stringResource(R.string.calendar_empty_description),
                )
            }
        } else {
            items(state.selectedTasks, key = { "${it.id}-${it.occurrenceDate ?: ""}" }) { task ->
                TaskRow(
                    task = task,
                    onToggle = { checked -> onToggle(task, checked) },
                    onClick = { onOpenTask(task.id) },
                    use24Hour = use24Hour,
                )
            }
        }
    }
}

@Composable
private fun periodLabel(state: CalendarUiState): String = when (state.view) {
    CalendarView.MONTH -> "${state.anchor.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${state.anchor.year}"
    CalendarView.WEEK -> {
        val start = state.anchor.startOfWeek(state.preferences.weekStartsOn)
        "${start.formatDayAndMonth()} to ${start.plusDays(6).formatDayAndMonth()}"
    }
    CalendarView.DAY -> state.anchor.formatDayAndMonth()
}

private fun CalendarView.labelRes(): Int = when (this) {
    CalendarView.MONTH -> R.string.calendar_month
    CalendarView.WEEK -> R.string.calendar_week
    CalendarView.DAY -> R.string.calendar_day
}

/** The timeline covers waking hours; 3am is not worth 60dp of scroll. */
private const val START_HOUR = 6
private const val END_HOUR = 23
private val HOUR_HEIGHT = 44.dp
