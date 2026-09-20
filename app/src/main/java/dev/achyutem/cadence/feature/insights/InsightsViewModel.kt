package dev.achyutem.cadence.feature.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.achyutem.cadence.core.common.cadenceViewModelFactory
import dev.achyutem.cadence.core.database.CadenceDatabase
import dev.achyutem.cadence.core.time.CadenceClock
import dev.achyutem.cadence.domain.insights.Insight
import dev.achyutem.cadence.domain.insights.InsightsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class InsightsUiState(
    val insights: List<Insight> = emptyList(),
    val daysAnalysed: Int = 0,
    val from: LocalDate = LocalDate.EPOCH,
    val to: LocalDate = LocalDate.EPOCH,
    val loading: Boolean = true,
)

/**
 * Insights.
 *
 * Computed on demand over a bounded window and held in memory for the session. **Nothing is
 * written back**: an insight is derived data, and storing it would create a second truth that
 * goes stale the moment the user edits a past day.
 *
 * The analysis runs on a background dispatcher because it reads a whole window of tasks at once,
 * which is the one place in the app that deliberately loads more than a screenful.
 */
class InsightsViewModel(
    private val database: CadenceDatabase,
    private val clock: CadenceClock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        val today = clock.today()
        val from = today.minusDays(WINDOW_DAYS)

        val result = withContext(Dispatchers.Default) {
            val tasks = database.taskDao().getAllForBackup()
                .filter { it.scheduledDate != null && it.scheduledDate!! in from..today }
            val habitEntries = database.habitDao().getAllEntriesForBackup()
                .filter { it.date in from..today }
            val insights = InsightsEngine.analyse(
                tasks = tasks,
                habitEntries = habitEntries,
                from = from,
                to = today,
                zone = clock.zone(),
            )
            val days = tasks.mapNotNull { it.scheduledDate }.distinct().size
            Triple(insights, days, from)
        }

        _uiState.value = InsightsUiState(
            insights = result.first,
            daysAnalysed = result.second,
            from = result.third,
            to = today,
            loading = false,
        )
    }

    companion object {
        /** Ninety days: long enough for weekday patterns, recent enough to describe you now. */
        private const val WINDOW_DAYS = 90L

        val Factory = cadenceViewModelFactory { container ->
            InsightsViewModel(container.database, container.clock)
        }
    }
}
