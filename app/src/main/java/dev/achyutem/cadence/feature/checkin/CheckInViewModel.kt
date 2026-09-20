package dev.achyutem.cadence.feature.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.achyutem.cadence.core.common.cadenceViewModelFactory
import dev.achyutem.cadence.core.database.dao.CheckInDao
import dev.achyutem.cadence.core.database.entity.DailyCheckInEntity
import dev.achyutem.cadence.core.time.CadenceClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CheckInUiState(
    val today: LocalDate = LocalDate.EPOCH,
    val todayEntry: DailyCheckInEntity? = null,
    val history: List<DailyCheckInEntity> = emptyList(),
    val loading: Boolean = true,
) {
    val recorded: Boolean get() = todayEntry != null
}

/**
 * The daily check-in.
 *
 * Mood and energy on a 1..5 scale, plus an optional note. Deliberately the least demanding thing
 * in the app: one tap records a mood, and nothing else is required.
 *
 * Writing happens on every tap rather than behind a Save, so a half-finished check-in still counts
 * as a check-in. Someone who taps a mood and puts the phone down has told us something true.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckInViewModel(
    private val checkIns: CheckInDao,
    private val clock: CadenceClock,
    private val onDataChanged: suspend () -> Unit,
) : ViewModel() {

    private val today = MutableStateFlow(clock.today())

    val uiState: StateFlow<CheckInUiState> = today.flatMapLatest { date ->
        combine(
            checkIns.observeOn(date),
            checkIns.observeBetween(date.minusDays(HISTORY_DAYS), date),
        ) { entry, history ->
            CheckInUiState(
                today = date,
                todayEntry = entry,
                history = history.sortedByDescending { it.date },
                loading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckInUiState())

    fun refreshToday() { today.value = clock.today() }

    fun setMood(mood: Int) = upsert { it.copy(mood = mood.coerceIn(SCALE)) }

    fun setEnergy(energy: Int) = upsert { it.copy(energy = energy.coerceIn(SCALE)) }

    fun setNote(note: String) = upsert { it.copy(note = note.ifBlank { null }) }

    fun clearToday() = viewModelScope.launch {
        checkIns.deleteOn(today.value)
        onDataChanged()
    }

    private fun upsert(block: (DailyCheckInEntity) -> DailyCheckInEntity) = viewModelScope.launch {
        val date = today.value
        val now = clock.now()
        val existing = checkIns.getOn(date)
        val base = existing ?: DailyCheckInEntity(
            date = date,
            // A check-in created by tapping energy alone still needs a mood; the midpoint is the
            // honest default for "not stated" on a five-point scale.
            mood = MIDPOINT,
            energy = MIDPOINT,
            createdAt = now,
            updatedAt = now,
        )
        checkIns.upsert(block(base).copy(updatedAt = now))
        onDataChanged()
    }

    companion object {
        private val SCALE = DailyCheckInEntity.SCALE_MIN..DailyCheckInEntity.SCALE_MAX
        private const val MIDPOINT = 3
        private const val HISTORY_DAYS = 29L

        val Factory = cadenceViewModelFactory { container ->
            CheckInViewModel(container.checkInDao, container.clock, container::refreshWidgets)
        }
    }
}
