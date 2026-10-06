package dev.achyutem.cadence.feature.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.designsystem.component.ButtonTone
import dev.achyutem.cadence.core.designsystem.component.CadenceAddFab
import dev.achyutem.cadence.core.designsystem.component.CadenceButton
import dev.achyutem.cadence.core.designsystem.component.CadenceIconButton
import dev.achyutem.cadence.core.designsystem.component.FullScreenEmptyState
import dev.achyutem.cadence.core.designsystem.component.SectionHeader
import dev.achyutem.cadence.core.designsystem.component.rememberReorderState
import dev.achyutem.cadence.core.designsystem.component.reorderable
import dev.achyutem.cadence.core.designsystem.component.reorderableItem
import dev.achyutem.cadence.core.designsystem.theme.CadencePreviewTheme
import dev.achyutem.cadence.core.designsystem.theme.tabularFigures
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.domain.habit.Habit

@Composable
fun HabitsScreen(
    onOpenHabit: (Long) -> Unit,
    onOpenInsights: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HabitsViewModel = viewModel(factory = HabitsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editorOpen by viewModel.editorOpen.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }

    Box(modifier = modifier.fillMaxSize()) {
        HabitsContent(
            state = state,
            onIncrement = viewModel::increment,
            onDecrement = viewModel::decrement,
            onOpenHabit = onOpenHabit,
            onOpenInsights = onOpenInsights,
            onMove = viewModel::moveHabit,
            onSettle = viewModel::commitOrder,
            onAddClick = viewModel::openEditor,
        )

        CadenceAddFab(
            contentDescription = stringResource(R.string.habits_add),
            onClick = viewModel::openEditor,
            modifier = Modifier.align(Alignment.BottomEnd),
        )

        if (editorOpen) {
            HabitEditorSheet(
                today = state.date,
                onDismiss = viewModel::closeEditor,
                onCreate = viewModel::createHabit,
            )
        }
    }
}

@Composable
private fun HabitsContent(
    state: HabitsUiState,
    onIncrement: (Habit) -> Unit,
    onDecrement: (Habit) -> Unit,
    onOpenHabit: (Long) -> Unit,
    onOpenInsights: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onSettle: () -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Spacing.screenGutter,
                    end = Spacing.screenGutter,
                    top = Spacing.xl,
                    bottom = Spacing.md,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.habits_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                CadenceIconButton(
                    icon = Icons.Rounded.Insights,
                    contentDescription = stringResource(R.string.insights_open),
                    onClick = onOpenInsights,
                )
            }
        }

        when {
            state.loading -> Unit

            state.isEmpty -> FullScreenEmptyState(
                title = stringResource(R.string.habits_empty_title),
                description = stringResource(R.string.habits_empty_description),
                action = {
                    CadenceButton(
                        text = stringResource(R.string.habits_empty_action),
                        onClick = onAddClick,
                        tone = ButtonTone.Primary,
                    )
                },
            )

            else -> {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val reorder = rememberReorderState(
                listState = listState,
                onMove = onMove,
                onSettle = onSettle,
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().reorderable(reorder),
                contentPadding = PaddingValues(
                    start = Spacing.screenGutter,
                    end = Spacing.screenGutter,
                    bottom = Spacing.fabClearance,
                ),
            ) {
                item(key = "today-header") {
                    SectionHeader(
                        title = stringResource(R.string.habits_today),
                        trailing = {
                            Text(
                                text = "${state.completedCount}/${state.scheduled.size}",
                                style = MaterialTheme.typography.labelMedium.tabularFigures,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                }

                itemsIndexed(state.scheduled, key = { _, habit -> "h${habit.id}" }) { index, habit ->
                    HabitRow(
                        habit = habit,
                        onIncrement = { onIncrement(habit) },
                        onDecrement = { onDecrement(habit) },
                        onClick = { onOpenHabit(habit.id) },
                        // Header and counter occupy the first slot, so row index is offset by one.
                        modifier = Modifier
                            .animateItem()
                            .reorderableItem(reorder, index + 1),
                    )
                }

                if (state.notScheduledToday.isNotEmpty()) {
                    item(key = "not-today-header") {
                        Spacer(Modifier.height(Spacing.lg))
                        SectionHeader(title = stringResource(R.string.habits_not_today))
                        Spacer(Modifier.height(Spacing.xxs))
                    }
                    items(state.notScheduledToday, key = { "n${it.id}" }) { habit ->
                        HabitRow(
                            habit = habit,
                            onIncrement = { onIncrement(habit) },
                            onDecrement = { onDecrement(habit) },
                            onClick = { onOpenHabit(habit.id) },
                        )
                    }
                }
            }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HabitsPreview() = CadencePreviewTheme {
    HabitsContent(
        state = HabitsUiState(loading = false),
        onIncrement = {},
        onDecrement = {},
        onOpenHabit = {},
        onOpenInsights = {},
        onMove = { _, _ -> },
        onSettle = {},
        onAddClick = {},
    )
}
