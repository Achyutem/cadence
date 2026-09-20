package dev.achyutem.cadence.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.achyutem.cadence.core.common.cadenceViewModelFactory
import dev.achyutem.cadence.core.database.dao.HabitDao
import dev.achyutem.cadence.core.database.dao.RecurrenceDao
import dev.achyutem.cadence.core.database.entity.HabitEntity
import dev.achyutem.cadence.core.database.entity.HabitEntryEntity
import dev.achyutem.cadence.core.database.entity.HabitGoalDirection
import dev.achyutem.cadence.core.database.entity.HabitType
import dev.achyutem.cadence.core.database.entity.RecurrenceRuleEntity
import dev.achyutem.cadence.core.datastore.SettingsRepository
import dev.achyutem.cadence.core.datastore.UserPreferences
import dev.achyutem.cadence.core.time.CadenceClock
import dev.achyutem.cadence.domain.habit.Habit
import dev.achyutem.cadence.domain.habit.isValueComplete
import dev.achyutem.cadence.domain.habit.toHabit
import dev.achyutem.cadence.domain.recurrence.RecurrenceEngine
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

data class HabitsUiState(
    val date: LocalDate = LocalDate.EPOCH,
    val scheduled: List<Habit> = emptyList(),
    val notScheduledToday: List<Habit> = emptyList(),
    val preferences: UserPreferences = UserPreferences.Default,
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = scheduled.isEmpty() && notScheduledToday.isEmpty()
    val completedCount: Int get() = scheduled.count { it.completed }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HabitsViewModel(
    private val habits: HabitDao,
    private val recurrence: RecurrenceDao,
    private val settings: SettingsRepository,
    private val clock: CadenceClock,
) : ViewModel() {

    private val today = MutableStateFlow(clock.today())

    val uiState: StateFlow<HabitsUiState> = today.flatMapLatest { date ->
        combine(
            habits.observeActive(),
            habits.observeEntriesOn(date),
            recurrence.observeAllRules(),
            settings.preferences,
        ) { activeHabits, entries, rules, preferences ->
            val entriesByHabit = entries.associateBy { it.habitId }
            val rulesById = rules.associateBy { it.id }

            val built = activeHabits.map { habit ->
                val rule = habit.recurrenceRuleId?.let(rulesById::get)
                habit.toHabit(
                    entry = entriesByHabit[habit.id],
                    // A null rule means "every day", substituted rather than special-cased.
                    scheduledToday = rule == null || RecurrenceEngine.occursOn(rule, date),
                )
            }

            HabitsUiState(
                date = date,
                scheduled = built.filter { it.scheduledToday },
                // Habits not due today are still shown, below and dimmed. Hiding them entirely
                // makes the list look like it lost something; showing them as due is a lie.
                notScheduledToday = built.filterNot { it.scheduledToday },
                preferences = preferences,
                loading = false,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HabitsUiState(),
    )

    private val _editorOpen = MutableStateFlow(false)
    val editorOpen: StateFlow<Boolean> = _editorOpen.asStateFlow()

    fun openEditor() { _editorOpen.value = true }

    fun closeEditor() { _editorOpen.value = false }

    fun refreshToday() { today.value = clock.today() }

    /**
     * Record a value for a habit on the displayed day.
     *
     * `completed` is evaluated against the habit's target **as it is right now** and stored, so
     * raising a target later cannot retroactively un-complete days that genuinely met the old one.
     */
    fun setValue(habit: Habit, value: Double) = viewModelScope.launch {
        val entity = habits.getById(habit.id) ?: return@launch
        val date = today.value
        val now = clock.now()
        val clamped = value.coerceAtLeast(0.0)

        val existing = habits.getEntry(habit.id, date)
        habits.upsertEntry(
            existing?.copy(
                value = clamped,
                completed = entity.isValueComplete(clamped),
                updatedAt = now,
            ) ?: HabitEntryEntity(
                habitId = habit.id,
                date = date,
                value = clamped,
                completed = entity.isValueComplete(clamped),
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    /** One tap: booleans flip, everything else steps up by the habit's increment. */
    fun increment(habit: Habit) {
        val next = when (habit.type) {
            HabitType.BOOLEAN -> if (habit.completed) 0.0 else habit.targetValue.coerceAtLeast(1.0)
            else -> habit.todayValue + habit.incrementStep
        }
        setValue(habit, next)
    }

    fun decrement(habit: Habit) {
        setValue(habit, (habit.todayValue - habit.incrementStep).coerceAtLeast(0.0))
    }

    fun archive(habit: Habit) = viewModelScope.launch {
        habits.setArchived(habit.id, archived = true, at = clock.now())
    }

    /**
     * Create a habit.
     *
     * The recurrence rule is inserted first when there is one, because the habit row's foreign
     * key points at it.
     */
    fun createHabit(
        name: String,
        description: String?,
        type: HabitType,
        target: Double,
        unit: String?,
        goalDirection: HabitGoalDirection,
        rule: RecurrenceRuleEntity?,
    ) = viewModelScope.launch {
        val now = clock.now()
        val ruleId = rule?.let { recurrence.insertRule(it) }
        habits.insert(
            HabitEntity(
                name = name.trim(),
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                type = type,
                targetValue = if (type == HabitType.BOOLEAN) 1.0 else target.coerceAtLeast(1.0),
                goalDirection = goalDirection,
                unit = unit?.trim()?.takeIf { it.isNotEmpty() },
                recurrenceRuleId = ruleId,
                startDate = clock.today(),
                sortOrder = habits.maxSortOrder() + SORT_STEP,
                createdAt = now,
                updatedAt = now,
            )
        )
        _editorOpen.value = false
    }

    companion object {
        private const val SORT_STEP = 100

        val Factory = cadenceViewModelFactory { container ->
            HabitsViewModel(
                container.habitDao,
                container.recurrenceDao,
                container.settingsRepository,
                container.clock,
            )
        }
    }
}
