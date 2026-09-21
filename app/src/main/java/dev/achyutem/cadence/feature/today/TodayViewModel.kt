package dev.achyutem.cadence.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.achyutem.cadence.core.time.UNSET_DATE
import dev.achyutem.cadence.core.common.cadenceViewModelFactory
import dev.achyutem.cadence.core.database.dao.HabitDao
import dev.achyutem.cadence.core.database.dao.RecurrenceDao
import dev.achyutem.cadence.core.database.dao.TaskDao
import dev.achyutem.cadence.core.database.entity.HabitEntity
import dev.achyutem.cadence.core.database.entity.HabitEntryEntity
import dev.achyutem.cadence.core.database.entity.HabitType
import dev.achyutem.cadence.core.database.entity.RecurrenceRuleEntity
import dev.achyutem.cadence.core.database.entity.TaskEntity
import dev.achyutem.cadence.core.database.entity.TaskPriority
import dev.achyutem.cadence.core.datastore.CompletedTaskBehavior
import dev.achyutem.cadence.core.datastore.SettingsRepository
import dev.achyutem.cadence.core.datastore.UserPreferences
import dev.achyutem.cadence.core.time.CadenceClock
import dev.achyutem.cadence.core.time.DayPart
import dev.achyutem.cadence.core.time.dayPart
import dev.achyutem.cadence.domain.habit.Habit
import dev.achyutem.cadence.domain.habit.isValueComplete
import dev.achyutem.cadence.domain.habit.toHabit
import dev.achyutem.cadence.domain.recurrence.RecurrenceEngine
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
    val date: LocalDate = UNSET_DATE,
    val time: LocalTime = LocalTime.MIDNIGHT,
    val displayName: String = "",
    val dayPart: DayPart = DayPart.MORNING,
    /** Every task scheduled for the day. Progress is measured over this, always. */
    val tasks: List<Task> = emptyList(),
    /** Hide-completed preference applied. This is what the list renders. */
    val visibleTasks: List<Task> = emptyList(),
    val overdue: List<Task> = emptyList(),
    val habits: List<Habit> = emptyList(),
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
    private val habitDao: HabitDao,
    private val recurrence: RecurrenceDao,
    private val settings: SettingsRepository,
    private val onDataChanged: suspend () -> Unit,
) : ViewModel() {

    /**
     * Recurrence rules are cached rather than re-read per habit.
     *
     * There are only ever a handful, and they change almost never, but every habit on Today
     * needs one to decide whether it is due, so loading them once and looking up by id keeps the
     * screen at two queries instead of one per habit.
     */
    private var cachedRules: Map<Long, RecurrenceRuleEntity> = emptyMap()

    init {
        viewModelScope.launch {
            recurrence.observeAllRules().collect { rules ->
                cachedRules = rules.associateBy { it.id }
            }
        }
    }

    private fun buildHabits(
        entities: List<HabitEntity>,
        entries: List<HabitEntryEntity>,
        date: LocalDate,
    ): List<Habit> {
        val entriesByHabit = entries.associateBy { it.habitId }
        return entities
            .map { habit ->
                val rule = habit.recurrenceRuleId?.let(cachedRules::get)
                habit.toHabit(
                    entry = entriesByHabit[habit.id],
                    scheduledToday = rule == null || RecurrenceEngine.occursOn(rule, date),
                )
            }
            // Today shows only what is actually due today; the full list lives on Habits.
            .filter { it.scheduledToday }
    }

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
                habitDao.observeActive(),
                habitDao.observeEntriesOn(date),
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val scheduled = values[0] as List<TaskEntity>
                @Suppress("UNCHECKED_CAST")
                val overdue = values[1] as List<TaskEntity>
                @Suppress("UNCHECKED_CAST")
                val subtasks = values[2] as List<TaskEntity>
                @Suppress("UNCHECKED_CAST")
                val activeHabits = values[3] as List<HabitEntity>
                @Suppress("UNCHECKED_CAST")
                val habitEntries = values[4] as List<HabitEntryEntity>
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
                    // completed ones, hiding something the user finished must not make the day
                    // look less complete than it is.
                    tasks = ordered,
                    visibleTasks = if (hideCompleted) ordered.filterNot { it.completed } else ordered,
                    overdue = overdue.map { it.build() }.filterNot { it.completed },
                    habits = buildHabits(activeHabits, habitEntries, date),
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
     * Called when the screen resumes, because the app can be left open across midnight, without
     * this, Today would keep showing yesterday, and "overdue" would be computed against the wrong
     * day.
     */
    fun refresh() {
        now.value = clock.dateTimeNow()
    }

    /** One tap from Today: booleans flip, metered habits step up. */
    fun incrementHabit(habit: Habit) = viewModelScope.launch {
        val entity = habitDao.getById(habit.id) ?: return@launch
        val date = clock.today()
        val at = clock.now()
        val next = when (habit.type) {
            HabitType.BOOLEAN -> if (habit.completed) 0.0 else habit.targetValue.coerceAtLeast(1.0)
            else -> habit.todayValue + habit.incrementStep
        }
        val existing = habitDao.getEntry(habit.id, date)
        habitDao.upsertEntry(
            existing?.copy(value = next, completed = entity.isValueComplete(next), updatedAt = at)
                ?: HabitEntryEntity(
                    habitId = habit.id,
                    date = date,
                    value = next,
                    completed = entity.isValueComplete(next),
                    createdAt = at,
                    updatedAt = at,
                )
        )
        onDataChanged()
    }

    fun decrementHabit(habit: Habit) = viewModelScope.launch {
        val entity = habitDao.getById(habit.id) ?: return@launch
        val date = clock.today()
        val at = clock.now()
        val next = (habit.todayValue - habit.incrementStep).coerceAtLeast(0.0)
        val existing = habitDao.getEntry(habit.id, date) ?: return@launch
        habitDao.upsertEntry(
            existing.copy(value = next, completed = entity.isValueComplete(next), updatedAt = at)
        )
        onDataChanged()
    }

    fun setCompleted(task: Task, completed: Boolean) = viewModelScope.launch {
        val at = clock.now()
        if (task.hasSubtasks) {
            task.subtasks.forEach { child ->
                tasks.setCompleted(child.id, completed, if (completed) at else null, at)
            }
        }
        tasks.setCompleted(task.id, completed, if (completed) at else null, at)
        onDataChanged()
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
        onDataChanged()
    }

    companion object {
        private const val SORT_STEP = 100

        val Factory = cadenceViewModelFactory { container ->
            TodayViewModel(
                container.clock,
                container.taskDao,
                container.habitDao,
                container.recurrenceDao,
                container.settingsRepository,
                container::refreshWidgets,
            )
        }
    }
}
