package dev.achyutem.cadence.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.achyutem.cadence.core.common.cadenceViewModelFactory
import dev.achyutem.cadence.core.database.dao.TaskDao
import dev.achyutem.cadence.core.database.entity.TaskEntity
import dev.achyutem.cadence.core.database.entity.TaskPriority
import dev.achyutem.cadence.core.datastore.CompletedTaskBehavior
import dev.achyutem.cadence.core.datastore.SettingsRepository
import dev.achyutem.cadence.core.datastore.UserPreferences
import dev.achyutem.cadence.core.time.CadenceClock
import dev.achyutem.cadence.core.time.DayPart
import dev.achyutem.cadence.core.time.dayPart
import dev.achyutem.cadence.domain.task.Task
import dev.achyutem.cadence.domain.task.completionProgress
import dev.achyutem.cadence.domain.task.orderedForDay
import dev.achyutem.cadence.domain.task.toTask
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class TodayUiState(
    val date: LocalDate = LocalDate.EPOCH,
    val time: LocalTime = LocalTime.MIDNIGHT,
    val displayName: String = "",
    val dayPart: DayPart = DayPart.MORNING,
    /** Every task scheduled for the day. Progress is measured over this, always. */
    val tasks: List<Task> = emptyList(),
    /** Hide-completed preference applied. This is what the list renders. */
    val visibleTasks: List<Task> = emptyList(),
    val overdue: List<Task> = emptyList(),
    val preferences: UserPreferences = UserPreferences.Default,
    val loading: Boolean = true,
) {
    /**
     * 0f..1f, or null when nothing is scheduled.
     *
     * Null is not 0%. An empty day is not a failed day, and rendering it as an empty progress bar
     * would be the first step toward the guilt-driven design the brief rules out.
     */
    val progress: Float? get() = tasks.completionProgress()

    val completedCount: Int get() = tasks.count { it.completed }

    val totalCount: Int get() = tasks.size

    /** The next thing due today that is not done yet. */
    val nextTask: Task?
        get() = tasks.firstOrNull { !it.completed && it.startTime != null && it.startTime >= time }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val clock: CadenceClock,
    private val tasks: TaskDao,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val now = MutableStateFlow(clock.dateTimeNow())

    val uiState: StateFlow<TodayUiState> = combine(
        now,
        settings.preferences,
    ) { instant, preferences -> instant to preferences }
        .flatMapLatest { (instant, preferences) ->
            val date = instant.toLocalDate()
            combine(
                tasks.observeScheduledOn(date),
                tasks.observeOverdue(date),
                tasks.observeAllSubtasks(),
            ) { scheduled, overdue, subtasks ->
                val childrenByParent = subtasks.groupBy { it.parentTaskId }
                fun TaskEntity.build(): Task = toTask(childrenByParent[id].orEmpty())

                val hideCompleted =
                    preferences.completedTaskBehavior == CompletedTaskBehavior.HIDE ||
                        !preferences.showCompletedOnToday
                val sink = preferences.completedTaskBehavior != CompletedTaskBehavior.KEEP_IN_PLACE
                val ordered = scheduled.map { it.build() }.orderedForDay(sinkCompleted = sink)

                TodayUiState(
                    date = date,
                    time = instant.toLocalTime(),
                    displayName = preferences.displayName,
                    dayPart = instant.toLocalTime().dayPart(),
                    // Progress counts every task scheduled for the day, including hidden
                    // completed ones — hiding something the user finished must not make the day
                    // look less complete than it is.
                    tasks = ordered,
                    visibleTasks = if (hideCompleted) ordered.filterNot { it.completed } else ordered,
                    overdue = overdue.map { it.build() }.filterNot { it.completed },
                    preferences = preferences,
                    loading = false,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TodayUiState(),
        )

    /**
     * Re-read the clock.
     *
     * Called when the screen resumes, because the app can be left open across midnight — without
     * this, Today would keep showing yesterday, and "overdue" would be computed against the wrong
     * day.
     */
    fun refresh() {
        now.value = clock.dateTimeNow()
    }

    fun setCompleted(task: Task, completed: Boolean) = viewModelScope.launch {
        val at = clock.now()
        if (task.hasSubtasks) {
            task.subtasks.forEach { child ->
                tasks.setCompleted(child.id, completed, if (completed) at else null)
            }
        }
        tasks.setCompleted(task.id, completed, if (completed) at else null)
    }

    fun addTask(
        title: String,
        date: LocalDate?,
        time: LocalTime?,
        priority: TaskPriority,
    ) = viewModelScope.launch {
        val at = clock.now()
        val day = date ?: clock.today()
        tasks.insert(
            TaskEntity(
                title = title,
                scheduledDate = day,
                startTime = time,
                durationMinutes = if (time != null) {
                    settings.preferences.first().defaultTaskDurationMinutes
                } else {
                    null
                },
                priority = priority,
                sortOrder = tasks.maxSortOrderOn(day) + SORT_STEP,
                createdAt = at,
                updatedAt = at,
            )
        )
    }

    companion object {
        private const val SORT_STEP = 100

        val Factory = cadenceViewModelFactory { container ->
            TodayViewModel(container.clock, container.taskDao, container.settingsRepository)
        }
    }
}
