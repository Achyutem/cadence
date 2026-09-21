package dev.achyutem.cadence.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.achyutem.cadence.core.time.UNSET_DATE
import dev.achyutem.cadence.core.common.cadenceViewModelFactory
import dev.achyutem.cadence.core.database.dao.HabitDao
import dev.achyutem.cadence.core.database.dao.RecurrenceDao
import dev.achyutem.cadence.core.database.dao.TaskDao
import dev.achyutem.cadence.core.database.entity.TaskEntity
import dev.achyutem.cadence.core.datastore.SettingsRepository
import dev.achyutem.cadence.core.datastore.UserPreferences
import dev.achyutem.cadence.core.time.CadenceClock
import dev.achyutem.cadence.core.time.endOfWeek
import dev.achyutem.cadence.core.time.startOfWeek
import dev.achyutem.cadence.domain.recurrence.RecurrenceEngine
import dev.achyutem.cadence.domain.task.Task
import dev.achyutem.cadence.domain.task.orderedForDay
import dev.achyutem.cadence.domain.task.toTask
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class CalendarView { MONTH, WEEK, DAY }

data class CalendarUiState(
    val view: CalendarView = CalendarView.MONTH,
    val anchor: LocalDate = UNSET_DATE,
    val today: LocalDate = UNSET_DATE,
    /** Tasks in the visible range, grouped by the date they fall on. */
    val tasksByDate: Map<LocalDate, List<Task>> = emptyMap(),
    /** Dates in the visible range that have at least one habit due. */
    val habitDates: Set<LocalDate> = emptySet(),
    val preferences: UserPreferences = UserPreferences.Default,
    val loading: Boolean = true,
) {
    val selectedTasks: List<Task> get() = tasksByDate[anchor].orEmpty()
}

/**
 * The calendar.
 *
 * ### Recurring tasks are expanded here, not stored
 *
 * A recurring task is one row. The calendar asks the recurrence engine which dates in the
 * *visible range* it lands on and materialises a [Task] per date in memory, carrying
 * [Task.occurrenceDate] so completion can be recorded against the right day. Nothing is written,
 * and moving a month forward costs one more expansion rather than a migration.
 *
 * ### The range is always bounded
 *
 * Every query is the visible period padded by a week. Month view loads a month, week view a week.
 * There is no path here that reads the whole table, which is what keeps the screen fast with years
 * of history behind it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val tasks: TaskDao,
    private val habits: HabitDao,
    private val recurrence: RecurrenceDao,
    private val settings: SettingsRepository,
    private val clock: CadenceClock,
    private val onDataChanged: suspend () -> Unit,
) : ViewModel() {

    private val view = MutableStateFlow(CalendarView.MONTH)
    private val anchor = MutableStateFlow(clock.today())

    val uiState: StateFlow<CalendarUiState> = combine(
        view, anchor, settings.preferences,
    ) { currentView, currentAnchor, preferences ->
        Triple(currentView, currentAnchor, preferences)
    }.flatMapLatest { (currentView, currentAnchor, preferences) ->
        val (start, end) = visibleRange(currentView, currentAnchor, preferences)

        combine(
            tasks.observeScheduledBetween(start, end),
            tasks.observeRecurringTemplates(),
            tasks.observeAllSubtasks(),
            recurrence.observeAllRules(),
            habits.observeActive(),
        ) { values ->
            @Suppress("UNCHECKED_CAST") val scheduled = values[0] as List<TaskEntity>
            @Suppress("UNCHECKED_CAST") val templates = values[1] as List<TaskEntity>
            @Suppress("UNCHECKED_CAST") val subtasks = values[2] as List<TaskEntity>
            @Suppress("UNCHECKED_CAST")
            val rules = (values[3] as List<dev.achyutem.cadence.core.database.entity.RecurrenceRuleEntity>)
                .associateBy { it.id }
            @Suppress("UNCHECKED_CAST")
            val activeHabits = values[4] as List<dev.achyutem.cadence.core.database.entity.HabitEntity>

            val childrenByParent = subtasks.groupBy { it.parentTaskId }
            fun TaskEntity.build(date: LocalDate? = null): Task =
                toTask(childrenByParent[id].orEmpty()).let {
                    if (date == null) it else it.copy(scheduledDate = date, occurrenceDate = date)
                }

            val byDate = mutableMapOf<LocalDate, MutableList<Task>>()
            scheduled.forEach { entity ->
                entity.scheduledDate?.let { date ->
                    byDate.getOrPut(date) { mutableListOf() } += entity.build()
                }
            }
            // Recurring templates are expanded across the visible range only.
            templates.forEach { template ->
                val rule = template.recurrenceRuleId?.let(rules::get) ?: return@forEach
                RecurrenceEngine.occurrencesBetween(rule, start, end).forEach { date ->
                    byDate.getOrPut(date) { mutableListOf() } += template.build(date)
                }
            }

            val habitDates = buildSet {
                activeHabits.forEach { habit ->
                    val rule = habit.recurrenceRuleId?.let(rules::get)
                    var date = maxOf(start, habit.startDate)
                    while (date <= end) {
                        if (rule == null || RecurrenceEngine.occursOn(rule, date)) add(date)
                        date = date.plusDays(1)
                    }
                }
            }

            CalendarUiState(
                view = currentView,
                anchor = currentAnchor,
                today = clock.today(),
                tasksByDate = byDate.mapValues { (_, list) -> list.orderedForDay() },
                habitDates = habitDates,
                preferences = preferences,
                loading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    private fun visibleRange(
        view: CalendarView,
        anchor: LocalDate,
        preferences: UserPreferences,
    ): Pair<LocalDate, LocalDate> = when (view) {
        // Padded by a week either side so the leading and trailing days of a month grid, which
        // belong to the neighbouring months, still show their indicators.
        CalendarView.MONTH -> {
            val month = YearMonth.from(anchor)
            month.atDay(1).minusWeeks(1) to month.atEndOfMonth().plusWeeks(1)
        }
        CalendarView.WEEK -> {
            val weekStart = anchor.startOfWeek(preferences.weekStartsOn)
            weekStart to weekStart.plusDays(6)
        }
        CalendarView.DAY -> anchor to anchor
    }

    fun setView(value: CalendarView) { view.value = value }

    fun select(date: LocalDate) { anchor.value = date }

    fun goToToday() { anchor.value = clock.today() }

    fun previous() {
        anchor.value = when (view.value) {
            CalendarView.MONTH -> anchor.value.minusMonths(1)
            CalendarView.WEEK -> anchor.value.minusWeeks(1)
            CalendarView.DAY -> anchor.value.minusDays(1)
        }
    }

    fun next() {
        anchor.value = when (view.value) {
            CalendarView.MONTH -> anchor.value.plusMonths(1)
            CalendarView.WEEK -> anchor.value.plusWeeks(1)
            CalendarView.DAY -> anchor.value.plusDays(1)
        }
    }

    /**
     * Complete a task from the calendar.
     *
     * A recurring occurrence is recorded against its own date in `task_occurrences`, never against
     * the template, so completing one Tuesday does not mark every Tuesday done.
     */
    fun setCompleted(task: Task, completed: Boolean) = viewModelScope.launch {
        val at = clock.now()
        val occurrence = task.occurrenceDate
        if (occurrence != null) {
            tasks.setOccurrenceCompleted(task.id, occurrence, completed, at)
        } else {
            task.subtasks.forEach { child ->
                tasks.setCompleted(child.id, completed, if (completed) at else null)
            }
            tasks.setCompleted(task.id, completed, if (completed) at else null)
        }
        onDataChanged()
    }

    companion object {
        val Factory = cadenceViewModelFactory { container ->
            CalendarViewModel(
                container.taskDao,
                container.habitDao,
                container.recurrenceDao,
                container.settingsRepository,
                container.clock,
                container::refreshWidgets,
            )
        }
    }
}
