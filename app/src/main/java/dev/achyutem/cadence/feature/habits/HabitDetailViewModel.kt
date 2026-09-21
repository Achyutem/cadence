package dev.achyutem.cadence.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.achyutem.cadence.core.time.UNSET_DATE
import dev.achyutem.cadence.core.common.appContainer
import dev.achyutem.cadence.core.database.dao.HabitDao
import dev.achyutem.cadence.core.database.dao.RecurrenceDao
import dev.achyutem.cadence.core.database.entity.HabitEntryEntity
import dev.achyutem.cadence.core.database.entity.RecurrenceRuleEntity
import dev.achyutem.cadence.core.datastore.SettingsRepository
import dev.achyutem.cadence.core.datastore.UserPreferences
import dev.achyutem.cadence.core.time.CadenceClock
import dev.achyutem.cadence.domain.habit.Habit
import dev.achyutem.cadence.domain.habit.toHabit
import dev.achyutem.cadence.domain.statistics.CompletionRate
import dev.achyutem.cadence.domain.statistics.HabitStatistics
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HabitDetailUiState(
    val habit: Habit? = null,
    val rule: RecurrenceRuleEntity? = null,
    val reminder: dev.achyutem.cadence.core.database.entity.ReminderEntity? = null,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val monthRate: CompletionRate = CompletionRate(0, 0),
    val averageValue: Double? = null,
    val heatmapLevels: Map<LocalDate, Int> = emptyMap(),
    val heatmapStart: LocalDate = UNSET_DATE,
    val heatmapEnd: LocalDate = UNSET_DATE,
    val today: LocalDate = UNSET_DATE,
    val preferences: UserPreferences = UserPreferences.Default,
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HabitDetailViewModel(
    private val habitId: Long,
    private val habits: HabitDao,
    private val recurrence: RecurrenceDao,
    private val settings: SettingsRepository,
    private val clock: CadenceClock,
    private val onDataChanged: suspend () -> Unit,
    /**
     * Rebuilds the alarm horizon.
     *
     * Only the edits that change *when* something should fire call this. Rescheduling on every
     * write would mean rebuilding every alarm in the app on each keystroke of a rename.
     */
    private val onScheduleChanged: suspend () -> Unit,
) : ViewModel() {

    private val today = MutableStateFlow(clock.today())

    /** Bumped after a write so the combine below re-reads rules and reminders, which are not Flows. */
    private val refreshKey = MutableStateFlow(0)

    val uiState: StateFlow<HabitDetailUiState> = combine(today, refreshKey) { date, _ -> date }
        .flatMapLatest { date ->
        // One bounded window feeds the heatmap *and* every statistic, so the screen issues one
        // range query rather than one per number on it.
        val windowStart = date.minusDays(HEATMAP_DAYS - 1)

        combine(
            habits.observeById(habitId),
            habits.observeEntriesBetween(habitId, windowStart, date),
            habits.observeEntry(habitId, date),
            recurrence.observeAllRules(),
            settings.preferences,
        ) { entity, entries, todayEntry, rules, preferences ->
            if (entity == null) return@combine HabitDetailUiState(loading = false)

            val rule = entity.recurrenceRuleId?.let { id -> rules.firstOrNull { it.id == id } }
            val monthStart = date.withDayOfMonth(1)

            HabitDetailUiState(
                habit = entity.toHabit(entry = todayEntry),
                rule = rule,
                reminder = entity.reminderId?.let { recurrence.getReminder(it) },
                currentStreak = HabitStatistics.currentStreak(entries, rule, entity.startDate, date),
                bestStreak = HabitStatistics.bestStreak(entries, rule, entity.startDate, date),
                monthRate = HabitStatistics.completionRate(
                    entries, rule, entity.startDate, monthStart, date, date,
                ),
                averageValue = HabitStatistics.averageValue(entries, windowStart, date),
                heatmapLevels = HabitStatistics.heatmapLevels(
                    entries, entity.targetValue, windowStart, date,
                ),
                heatmapStart = windowStart,
                heatmapEnd = date,
                today = date,
                preferences = preferences,
                loading = false,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HabitDetailUiState(),
    )

    private fun edit(block: (dev.achyutem.cadence.core.database.entity.HabitEntity) -> dev.achyutem.cadence.core.database.entity.HabitEntity) =
        viewModelScope.launch {
            val current = habits.getById(habitId) ?: return@launch
            habits.update(block(current).copy(updatedAt = clock.now()))
            bump()
        }

    fun setName(value: String) = edit { it.copy(name = value.trim().ifEmpty { it.name }) }

    /**
     * Change the target.
     *
     * Past entries keep the `completed` they were written with, so raising a target never
     * retroactively un-completes a day that genuinely met the old one. See `HabitEntities.kt`.
     */
    fun setTarget(value: Double) = edit { it.copy(targetValue = value.coerceAtLeast(1.0)) }

    fun setUnit(value: String) = edit { it.copy(unit = value.trim().ifEmpty { null }) }

    /**
     * Apply the whole measurement in one write.
     *
     * Type, target, unit and direction are edited together in one sheet, so they are saved
     * together: four separate `edit` calls would be four database round trips and four widget
     * refreshes for one intention, and any of them could interleave.
     *
     * Past entries are untouched. An entry records the value logged and whether it counted at
     * the time, so raising a target, or switching a habit from Done to Minutes, never rewrites
     * a day that already happened. See `HabitEntities.kt`.
     */
    fun setMeasurement(
        type: dev.achyutem.cadence.core.database.entity.HabitType,
        target: Double,
        unit: String?,
        direction: dev.achyutem.cadence.core.database.entity.HabitGoalDirection,
    ) = edit {
        it.copy(
            type = type,
            targetValue = target.coerceAtLeast(1.0),
            unit = unit?.trim()?.ifEmpty { null },
            goalDirection = direction,
        )
    }

    fun setGoalDirection(direction: dev.achyutem.cadence.core.database.entity.HabitGoalDirection) =
        edit { it.copy(goalDirection = direction) }

    /** A new rule row, for the same reason tasks get one: old occurrences stay reconstructible. */
    fun setRecurrence(rule: dev.achyutem.cadence.core.database.entity.RecurrenceRuleEntity?) =
        viewModelScope.launch {
            val current = habits.getById(habitId) ?: return@launch
            val newId = rule?.let { recurrence.insertRule(it) }
            habits.update(current.copy(recurrenceRuleId = newId, updatedAt = clock.now()))
            current.recurrenceRuleId?.let { old ->
                recurrence.getRule(old)?.let { recurrence.deleteRule(it) }
            }
            // The schedule decides which days a reminder fires on, so the alarms are now wrong.
            bump()
            onScheduleChanged()
        }

    fun setReminder(reminder: dev.achyutem.cadence.core.database.entity.ReminderEntity?) =
        viewModelScope.launch {
            val current = habits.getById(habitId) ?: return@launch
                // `id = 0` so Room assigns a fresh one. Callers legitimately hand us an edited copy
            // of the existing reminder, which still carries its primary key; inserting that is a
            // UNIQUE violation, and it crashed the app on the second tap of the reminder row.
            // The old row is deleted just below, so this is a replace, not a duplicate.
            val newId = reminder?.let { recurrence.insertReminder(it.copy(id = 0)) }
            habits.update(current.copy(reminderId = newId, updatedAt = clock.now()))
            current.reminderId?.let { old ->
                recurrence.getReminder(old)?.let { recurrence.deleteReminder(it) }
            }
            bump()
            // Without this the reminder sits in the database doing nothing until something else
            // rebuilds the horizon, which in practice meant the next app launch. A reminder you
            // set and then watched not arrive is worse than no reminder feature.
            onScheduleChanged()
        }

    private suspend fun bump() {
        refreshKey.value++
        onDataChanged()
    }

    fun archive(onDone: () -> Unit) = viewModelScope.launch {
        habits.setArchived(habitId, archived = true, at = clock.now())
        // An archived habit must stop nudging.
        onScheduleChanged()
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        habits.getById(habitId)?.let { habits.delete(it) }
        onDataChanged()
        onScheduleChanged()
        onDone()
    }

    companion object {
        /** Roughly six months, enough to show a pattern, small enough to stay one fast query. */
        private const val HEATMAP_DAYS = 182L

        fun factory(habitId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this as CreationExtras).appContainer
                HabitDetailViewModel(
                    habitId,
                    container.habitDao,
                    container.recurrenceDao,
                    container.settingsRepository,
                    container.clock,
                    container::refreshWidgets,
                    container.reminderScheduler::rescheduleAll,
                )
            }
        }
    }
}
