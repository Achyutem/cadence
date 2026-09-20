package dev.achyutem.cadence.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.achyutem.cadence.core.common.cadenceViewModelFactory
import dev.achyutem.cadence.core.database.dao.NoteDao
import dev.achyutem.cadence.core.database.dao.NoteSummary
import dev.achyutem.cadence.core.time.CadenceClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotesUiState(
    val pinned: List<NoteSummary> = emptyList(),
    val others: List<NoteSummary> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
) {
    val isEmpty: Boolean get() = pinned.isEmpty() && others.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class NotesViewModel(
    private val notes: NoteDao,
    private val clock: CadenceClock,
) : ViewModel() {

    private val query = MutableStateFlow("")
    val queryText: StateFlow<String> = query.asStateFlow()

    val uiState: StateFlow<NotesUiState> = query
        // Debounced so typing does not issue a query per keystroke; `flatMapLatest` then cancels
        // any in-flight query when the term changes again.
        .debounce { if (it.isEmpty()) 0L else 180L }
        .flatMapLatest { term ->
            val source = if (term.isBlank()) notes.observeSummaries() else notes.searchSummaries(term.trim())
            combine(source, query) { list, currentTerm ->
                NotesUiState(
                    pinned = list.filter { it.pinned },
                    others = list.filterNot { it.pinned },
                    query = currentTerm,
                    loading = false,
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotesUiState(),
        )

    fun setQuery(value: String) {
        query.value = value
    }

    fun togglePinned(note: NoteSummary) = viewModelScope.launch {
        notes.setPinned(note.id, !note.pinned, clock.now())
    }

    fun delete(note: NoteSummary) = viewModelScope.launch {
        notes.deleteById(note.id)
    }

    companion object {
        val Factory = cadenceViewModelFactory { container ->
            NotesViewModel(container.database.noteDao(), container.clock)
        }
    }
}
