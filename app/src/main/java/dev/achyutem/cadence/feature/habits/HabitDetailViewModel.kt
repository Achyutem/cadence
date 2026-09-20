package dev.achyutem.cadence.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val monthRate: CompletionRate = CompletionRate(0, 0),
    val averageValue: Double? = null,
    val heatmapLevels: Map<LocalDate, Int> = emptyMap(),
    val heatmapStart: LocalDate = LocalDate.EPOCH,
    val heatmapEnd: LocalDate = LocalDate.EPOCH,
    val today: LocalDate = LocalDate.EPOCH,
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
) : ViewModel() {

    private val today = MutableStateFlow(clock.today())

    val uiState: StateFlow<HabitDetailUiState> = today.flatMapLatest { date ->
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

    fun archive(onDone: () -> Unit) = viewModelScope.launch {
        habits.setArchived(habitId, archived = true, at = clock.now())
        onDone()
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        habits.getById(habitId)?.let { habits.delete(it) }
        onDone()
    }

    companion object {
        /** Roughly six months — enough to show a pattern, small enough to stay one fast query. */
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
                )
            }
        }
    }
}
