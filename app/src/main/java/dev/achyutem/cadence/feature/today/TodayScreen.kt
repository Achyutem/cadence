package dev.achyutem.cadence.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.datastore.TimeFormat
import dev.achyutem.cadence.core.designsystem.component.ButtonTone
import dev.achyutem.cadence.core.designsystem.component.CadenceButton
import dev.achyutem.cadence.core.designsystem.component.CadenceCard
import dev.achyutem.cadence.core.designsystem.component.CadenceProgressBar
import dev.achyutem.cadence.core.designsystem.component.EmptyState
import dev.achyutem.cadence.core.designsystem.component.SectionHeader
import dev.achyutem.cadence.core.designsystem.theme.CadencePreviewTheme
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.theme.tabularFigures
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.core.time.DayPart
import dev.achyutem.cadence.core.time.formatDayAndMonth
import dev.achyutem.cadence.core.time.formatWeekdayFull
import dev.achyutem.cadence.domain.task.Task
import dev.achyutem.cadence.feature.todos.QuickAddBar
import dev.achyutem.cadence.feature.todos.TaskRow
import java.time.LocalDate

/**
 * Today — the most important screen, and the one the app opens to.
 *
 * Order of business, top to bottom: what day it is, how the day is going, what is overdue, what
 * is scheduled, then habits. Overdue sits above today's list because it is the only thing on the
 * screen that is already a problem.
 */
@Composable
fun TodayScreen(
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var quickAddVisible by remember { mutableStateOf(false) }

    // The app can sit open across midnight; re-read the clock whenever the screen comes back.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Box(modifier = modifier.fillMaxSize()) {
        TodayContent(
            state = state,
            onToggle = viewModel::setCompleted,
            onAddClick = { quickAddVisible = true },
        )
        QuickAddBar(
            visible = quickAddVisible,
            today = state.date,
            defaultDate = state.date,
            onDismiss = { quickAddVisible = false },
            onSubmit = viewModel::addTask,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TodayContent(
    state: TodayUiState,
    onToggle: (Task, Boolean) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val use24Hour = state.preferences.timeFormat != TimeFormat.TWELVE_HOUR

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = Spacing.screenGutter,
            end = Spacing.screenGutter,
            top = Spacing.xl,
            bottom = Spacing.dockClearance,
        ),
    ) {
        item(key = "header") {
            DateHeader(date = state.date, dayPart = state.dayPart, name = state.displayName)
            Spacer(Modifier.height(Spacing.lg))
        }

        item(key = "progress") {
            ProgressCard(
                progress = state.progress,
                completed = state.completedCount,
                total = state.totalCount,
            )
            Spacer(Modifier.height(Spacing.lg))
        }

        if (state.overdue.isNotEmpty()) {
            item(key = "overdue-header") {
                SectionHeader(
                    title = stringResource(R.string.today_section_overdue),
                    trailing = {
                        Text(
                            text = state.overdue.size.toString(),
                            style = MaterialTheme.typography.labelMedium.tabularFigures,
                            color = CadenceTheme.colors.danger,
                        )
                    },
                )
                Spacer(Modifier.height(Spacing.xxs))
            }
            items(state.overdue, key = { "o${it.id}" }) { task ->
                TaskRow(
                    task = task,
                    onToggle = { checked -> onToggle(task, checked) },
                    onToggleSubtask = onToggle,
                    onClick = { },
                    use24Hour = use24Hour,
                )
            }
            item(key = "overdue-space") { Spacer(Modifier.height(Spacing.lg)) }
        }

        item(key = "todos-header") {
            SectionHeader(
                title = stringResource(R.string.today_section_todos),
                trailing = {
                    CadenceButton(
                        text = stringResource(R.string.todos_add),
                        onClick = onAddClick,
                        tone = ButtonTone.Ghost,
                        icon = Icons.Rounded.Add,
                        size = dev.achyutem.cadence.core.designsystem.component.ButtonSize.Small,
                    )
                },
            )
            Spacer(Modifier.height(Spacing.xxs))
        }

        if (state.visibleTasks.isEmpty()) {
            item(key = "todos-empty") {
                EmptyState(
                    title = stringResource(R.string.today_empty_tasks_title),
                    description = stringResource(R.string.today_empty_tasks_description),
                )
            }
        } else {
            items(state.visibleTasks, key = { "t${it.id}" }) { task ->
                TaskRow(
                    task = task,
                    onToggle = { checked -> onToggle(task, checked) },
                    onToggleSubtask = onToggle,
                    onClick = { },
                    use24Hour = use24Hour,
                )
            }
        }

        item(key = "habits-header") {
            Spacer(Modifier.height(Spacing.lg))
            SectionHeader(title = stringResource(R.string.today_section_habits))
            Spacer(Modifier.height(Spacing.xxs))
        }

        item(key = "habits-empty") {
            EmptyState(
                title = stringResource(R.string.today_empty_habits_title),
                description = stringResource(R.string.today_empty_habits_description),
            )
        }
    }
}

/**
 * The date header. Weekday and date sit at different weights so the block reads as one object
 * rather than a sentence: the weekday orients you, the date is the detail.
 */
@Composable
private fun DateHeader(date: LocalDate, dayPart: DayPart, name: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = date.formatWeekdayFull(),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = date.formatDayAndMonth(),
            style = MaterialTheme.typography.titleMedium.tabularFigures,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = greetingText(dayPart, name),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The greeting, with or without a name.
 *
 * A blank name is a first-class case, not a missing value: it produces the unnamed greeting
 * rather than "Good morning, ." — the kind of detail that decides whether an optional field
 * actually feels optional.
 */
@Composable
private fun greetingText(dayPart: DayPart, name: String): String =
    if (name.isBlank()) {
        stringResource(
            when (dayPart) {
                DayPart.MORNING -> R.string.greeting_morning
                DayPart.AFTERNOON -> R.string.greeting_afternoon
                DayPart.EVENING -> R.string.greeting_evening
                DayPart.NIGHT -> R.string.greeting_night
            }
        )
    } else {
        stringResource(
            when (dayPart) {
                DayPart.MORNING -> R.string.greeting_morning_named
                DayPart.AFTERNOON -> R.string.greeting_afternoon_named
                DayPart.EVENING -> R.string.greeting_evening_named
                DayPart.NIGHT -> R.string.greeting_night_named
            },
            name,
        )
    }

@Composable
private fun ProgressCard(progress: Float?, completed: Int, total: Int) {
    CadenceCard {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.today_progress_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (progress == null) {
                        stringResource(R.string.today_progress_none)
                    } else {
                        stringResource(R.string.today_progress_percent, (progress * 100).toInt())
                    },
                    style = MaterialTheme.typography.titleMedium.tabularFigures,
                    color = if (progress == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            CadenceProgressBar(
                progress = progress ?: 0f,
                contentDescription = if (progress == null) {
                    stringResource(R.string.today_progress_empty_description)
                } else {
                    pluralStringResource(R.plurals.today_progress_description, total, completed, total)
                },
            )
            if (progress != null) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.today_progress_summary, completed, total),
                    style = MaterialTheme.typography.bodySmall.tabularFigures,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(name = "Today · light", showBackground = true)
@Composable
private fun TodayPreviewLight() = CadencePreviewTheme {
    TodayContent(
        state = TodayUiState(
            date = LocalDate.of(2026, 9, 21),
            dayPart = DayPart.AFTERNOON,
            displayName = "Achyutem",
            loading = false,
        ),
        onToggle = { _, _ -> },
        onAddClick = {},
    )
}
