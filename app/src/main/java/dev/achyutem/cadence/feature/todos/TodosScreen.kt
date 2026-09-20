package dev.achyutem.cadence.feature.todos

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.datastore.TimeFormat
import dev.achyutem.cadence.core.designsystem.component.ButtonTone
import dev.achyutem.cadence.core.designsystem.component.CadenceButton
import dev.achyutem.cadence.core.designsystem.component.FullScreenEmptyState
import dev.achyutem.cadence.core.designsystem.component.SectionHeader
import dev.achyutem.cadence.core.designsystem.component.SegmentedControl
import dev.achyutem.cadence.core.designsystem.theme.CadencePreviewTheme
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.domain.task.Task

@Composable
fun TodosScreen(
    onOpenTask: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodosViewModel = viewModel(factory = TodosViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val quickAddVisible by viewModel.quickAddVisible.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        TodosContent(
            state = state,
            onFilter = viewModel::setFilter,
            onToggle = viewModel::setCompleted,
            onToggleSubtask = viewModel::setCompleted,
            onOpenTask = onOpenTask,
            onAddClick = viewModel::showQuickAdd,
        )

        QuickAddBar(
            visible = quickAddVisible,
            today = state.today,
            onDismiss = viewModel::hideQuickAdd,
            onSubmit = viewModel::addTask,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun TodosContent(
    state: TodosUiState,
    onFilter: (TaskFilter) -> Unit,
    onToggle: (Task, Boolean) -> Unit,
    onToggleSubtask: (Task, Boolean) -> Unit,
    onOpenTask: (Long) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val use24Hour = state.preferences.timeFormat != TimeFormat.TWELVE_HOUR

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
                text = stringResource(R.string.todos_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            CadenceButton(
                text = stringResource(R.string.todos_add),
                onClick = onAddClick,
                tone = ButtonTone.Primary,
                icon = Icons.Rounded.Add,
            )
        }

        SegmentedControl(
            options = TaskFilter.entries,
            selected = state.filter,
            onSelect = onFilter,
            label = { stringResource(it.labelRes()) },
            modifier = Modifier.padding(horizontal = Spacing.screenGutter),
        )
        Spacer(Modifier.height(Spacing.md))

        when {
            state.loading -> Unit

            state.isEmpty -> FullScreenEmptyState(
                title = stringResource(R.string.todos_empty_title),
                description = stringResource(R.string.todos_empty_description),
                action = {
                    CadenceButton(
                        text = stringResource(R.string.todos_empty_action),
                        onClick = onAddClick,
                        tone = ButtonTone.Primary,
                    )
                },
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.screenGutter,
                    end = Spacing.screenGutter,
                    bottom = Spacing.dockClearance,
                ),
            ) {
                taskSection(
                    titleRes = R.string.todos_overdue,
                    tasks = state.overdue,
                    use24Hour = use24Hour,
                    onToggle = onToggle,
                    onToggleSubtask = onToggleSubtask,
                    onOpenTask = onOpenTask,
                )
                taskSection(
                    titleRes = R.string.todos_scheduled,
                    tasks = state.scheduled,
                    use24Hour = use24Hour,
                    onToggle = onToggle,
                    onToggleSubtask = onToggleSubtask,
                    onOpenTask = onOpenTask,
                )
                taskSection(
                    titleRes = R.string.todos_backlog,
                    tasks = state.backlog,
                    use24Hour = use24Hour,
                    onToggle = onToggle,
                    onToggleSubtask = onToggleSubtask,
                    onOpenTask = onOpenTask,
                )
            }
        }
    }
}

/**
 * A titled group of tasks, which draws nothing at all when empty.
 *
 * Written as a `LazyListScope` extension rather than a composable so the rows stay direct
 * children of the one `LazyColumn`, nesting scrollables to group things would defeat lazy
 * loading and make the whole list measure eagerly.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.taskSection(
    titleRes: Int,
    tasks: List<Task>,
    use24Hour: Boolean,
    onToggle: (Task, Boolean) -> Unit,
    onToggleSubtask: (Task, Boolean) -> Unit,
    onOpenTask: (Long) -> Unit,
) {
    if (tasks.isEmpty()) return

    item(key = "header-$titleRes") {
        Spacer(Modifier.height(Spacing.sm))
        SectionHeader(title = stringResource(titleRes))
        Spacer(Modifier.height(Spacing.xxs))
    }
    items(tasks, key = { "t${it.id}" }) { task ->
        TaskRow(
            task = task,
            onToggle = { checked -> onToggle(task, checked) },
            onToggleSubtask = onToggleSubtask,
            onClick = { onOpenTask(task.id) },
            use24Hour = use24Hour,
        )
    }
}

private fun TaskFilter.labelRes(): Int = when (this) {
    TaskFilter.TODAY -> R.string.filter_today
    TaskFilter.UPCOMING -> R.string.filter_upcoming
    TaskFilter.ALL -> R.string.filter_all
}

@Preview(showBackground = true)
@Composable
private fun TodosPreview() = CadencePreviewTheme {
    TodosContent(
        state = TodosUiState(loading = false),
        onFilter = {},
        onToggle = { _, _ -> },
        onToggleSubtask = { _, _ -> },
        onOpenTask = {},
        onAddClick = {},
    )
}
