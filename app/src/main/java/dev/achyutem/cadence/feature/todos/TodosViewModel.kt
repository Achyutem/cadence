package dev.achyutem.cadence.feature.todos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.achyutem.cadence.core.time.UNSET_DATE
import dev.achyutem.cadence.core.common.cadenceViewModelFactory
import dev.achyutem.cadence.core.database.dao.TaskDao
import dev.achyutem.cadence.core.database.entity.TaskEntity
import dev.achyutem.cadence.core.database.entity.TaskPriority
import dev.achyutem.cadence.core.datastore.CompletedTaskBehavior
import dev.achyutem.cadence.core.datastore.SettingsRepository
import dev.achyutem.cadence.core.datastore.UserPreferences
import dev.achyutem.cadence.core.time.CadenceClock
import dev.achyutem.cadence.domain.task.Task
import dev.achyutem.cadence.domain.task.orderedForDay
import dev.achyutem.cadence.domain.task.toTask
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

/** Which slice of tasks the Todos screen is showing. */
enum class TaskFilter { TODAY, UPCOMING, ALL }

data class TodosUiState(
    val today: LocalDate = UNSET_DATE,
    val filter: TaskFilter = TaskFilter.TODAY,
    val overdue: List<Task> = emptyList(),
    val scheduled: List<Task> = emptyList(),
    val backlog: List<Task> = emptyList(),
    val preferences: UserPreferences = UserPreferences.Default,
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = overdue.isEmpty() && scheduled.isEmpty() && backlog.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodosViewModel(
    private val tasks: TaskDao,
    private val settings: SettingsRepository,
    private val clock: CadenceClock,
    private val onDataChanged: suspend () -> Unit,
) : ViewModel() {

    private val filter = MutableStateFlow(TaskFilter.TODAY)
    private val today = MutableStateFlow(clock.today())

    val uiState: StateFlow<TodosUiState> = combine(
        filter,
        today,
        settings.preferences,
    ) { currentFilter, currentDay, preferences ->
        Triple(currentFilter, currentDay, preferences)
    }.flatMapLatest { (currentFilter, currentDay, preferences) ->
        // Range is chosen per filter so the query stays bounded, "all" still means a window,
        // not the whole table (see CLAUDE.md #22).
        val (start, end) = when (currentFilter) {
            TaskFilter.TODAY -> currentDay to currentDay
            TaskFilter.UPCOMING -> currentDay to currentDay.plusDays(30)
            TaskFilter.ALL -> currentDay.minusMonths(3) to currentDay.plusMonths(12)
        }

        combine(
            tasks.observeScheduledBetween(start, end),
            tasks.observeOverdue(currentDay),
            tasks.observeBacklog(),
            tasks.observeAllSubtasks(),
        ) { scheduled, overdue, backlog, subtasks ->
            val childrenByParent = subtasks.groupBy { it.parentTaskId }
            fun TaskEntity.build(): Task = toTask(childrenByParent[id].orEmpty())

            val sink = preferences.completedTaskBehavior != CompletedTaskBehavior.KEEP_IN_PLACE
            val hideCompleted = preferences.completedTaskBehavior == CompletedTaskBehavior.HIDE

            TodosUiState(
                today = currentDay,
                filter = currentFilter,
                overdue = overdue.map { it.build() }.filterNot { it.completed },
                scheduled = scheduled.map { it.build() }
                    .filterNot { hideCompleted && it.completed }
                    .orderedForDay(sinkCompleted = sink),
                // Same treatment as the scheduled list. The query used to exclude completed
                // tasks outright, so ticking an undated task made it vanish with no way to see
                // or undo it, whatever the user had chosen under "completed tasks".
                backlog = backlog.map { it.build() }
                    .filterNot { hideCompleted && it.completed }
                    .orderedForDay(sinkCompleted = sink),
                preferences = preferences,
                loading = false,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TodosUiState(),
    )

    private val _quickAddVisible = MutableStateFlow(false)
    val quickAddVisible: StateFlow<Boolean> = _quickAddVisible.asStateFlow()

    fun showQuickAdd() { _quickAddVisible.value = true }

    fun hideQuickAdd() { _quickAddVisible.value = false }

    fun setFilter(value: TaskFilter) { filter.value = value }

    /** Re-read the clock; the app can be left open across midnight. */
    fun refreshToday() { today.value = clock.today() }

    fun addTask(
        title: String,
        date: LocalDate?,
        time: LocalTime?,
        priority: TaskPriority,
    ) = viewModelScope.launch {
        val now = clock.now()
        tasks.insert(
            TaskEntity(
                title = title,
                scheduledDate = date,
                startTime = time,
                // A timed task gets the user's default block length; an untimed one has no
                // duration at all, rather than a zero-length one.
                durationMinutes = if (time != null) {
                    settings.preferences.first().defaultTaskDurationMinutes
                } else {
                    null
                },
                priority = priority,
                // Sparse ordering, so inserting between two tasks rarely rewrites other rows.
                sortOrder = tasks.maxSortOrderOn(date) + SORT_STEP,
                createdAt = now,
                updatedAt = now,
            )
        )
        onDataChanged()
    }

    fun setCompleted(task: Task, completed: Boolean) = viewModelScope.launch {
        val now = clock.now()
        if (task.hasSubtasks) {
            // A parent's completion is derived, so "complete the parent" means completing its
            // children, writing the parent's own flag would be ignored by the domain model and
            // would leave the UI and the data saying different things.
            task.subtasks.forEach { child ->
                tasks.setCompleted(child.id, completed, if (completed) now else null, now)
            }
        }
        tasks.setCompleted(task.id, completed, if (completed) now else null, now)
        onDataChanged()
    }

    fun delete(task: Task) = viewModelScope.launch {
        tasks.deleteById(task.id)
        onDataChanged()
    }

    companion object {
        /**
         * Gap between adjacent `sortOrder` values. Leaving room means a drag-and-drop reorder can
         * usually rewrite one row instead of renumbering the list.
         */
        private const val SORT_STEP = 100

        val Factory = cadenceViewModelFactory { container ->
            TodosViewModel(
                container.taskDao,
                container.settingsRepository,
                container.clock,
                container::refreshWidgets,
            )
        }
    }
}
