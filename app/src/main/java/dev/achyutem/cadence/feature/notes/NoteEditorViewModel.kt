package dev.achyutem.cadence.feature.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.achyutem.cadence.core.common.appContainer
import dev.achyutem.cadence.core.database.dao.NoteDao
import dev.achyutem.cadence.core.database.entity.NoteEntity
import dev.achyutem.cadence.core.time.CadenceClock
import dev.achyutem.cadence.domain.notes.Markdown
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NoteEditorUiState(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val pinned: Boolean = false,
    /**
     * True when the note is being read rather than written.
     *
     * An existing note with content opens in **view mode**: most of the time a note is opened to
     * be read, and landing in an editor with a cursor blinking in someone's prose invites
     * accidental edits. A new or empty note opens straight into editing, because there is nothing
     * to read.
     */
    val previewMode: Boolean = false,
    val loading: Boolean = true,
    val saved: Boolean = true,
) {
    val isBlank: Boolean get() = title.isBlank() && content.isBlank()
}

/**
 * The note editor.
 *
 * **Autosave, not a save button.** A note is a document; losing one because the user backed out
 * is unacceptable, and a save button makes that the user's problem. Edits are debounced by
 * [AUTOSAVE_DELAY_MS] so typing does not write on every keystroke, and [saveNow] forces a flush
 * when the screen goes away; the debounce is a performance optimisation, never a window in which
 * work can be lost.
 *
 * A note that is still completely blank is never written, so opening the editor and changing your
 * mind does not leave an empty row behind.
 */
class NoteEditorViewModel(
    private val noteId: Long,
    private val notes: NoteDao,
    private val clock: CadenceClock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    private var autosaveJob: Job? = null
    private var persistedId: Long = noteId

    init {
        viewModelScope.launch {
            if (noteId > 0) {
                val note = notes.getById(noteId)
                _uiState.value = if (note == null) {
                    NoteEditorUiState(loading = false)
                } else {
                    NoteEditorUiState(
                        id = note.id,
                        title = note.title,
                        content = note.content,
                        pinned = note.pinned,
                        previewMode = note.content.isNotBlank(),
                        loading = false,
                    )
                }
            } else {
                _uiState.value = NoteEditorUiState(loading = false)
            }
        }
    }

    fun setTitle(value: String) {
        _uiState.update { it.copy(title = value, saved = false) }
        scheduleAutosave()
    }

    fun setContent(value: String) {
        _uiState.update { it.copy(content = value, saved = false) }
        scheduleAutosave()
    }

    /**
     * Handle Enter inside the body, continuing a list if the caret is in one.
     *
     * Returns the rewritten text and the new caret position, or null when this was an ordinary
     * newline the text field should handle itself. Both are returned together so the screen never
     * recomputes the continuation to find out what the text became.
     */
    fun continueList(caret: Int): Pair<String, Int>? {
        val result = Markdown.continueListOnNewline(_uiState.value.content, caret) ?: return null
        _uiState.update { it.copy(content = result.first, saved = false) }
        scheduleAutosave()
        return result
    }

    fun togglePreview() = _uiState.update { it.copy(previewMode = !it.previewMode) }

    /**
     * Switch to editing because the user touched the body.
     *
     * Separate from [togglePreview] so that tapping text is a one-way move into editing; a tap
     * that could also flip you *back* into reading would make the body feel like a switch.
     */
    fun beginEditing() = _uiState.update { if (it.previewMode) it.copy(previewMode = false) else it }

    fun togglePinned() {
        _uiState.update { it.copy(pinned = !it.pinned, saved = false) }
        scheduleAutosave()
    }

    /**
     * Flip a checkbox in the rendered preview by rewriting the underlying Markdown line.
     *
     * Note that this does **not** leave preview mode. Ticking something off is reading behaviour,
     * not editing behaviour, and dropping the user into a text editor because they checked a box
     * is exactly the wrong response.
     */
    fun toggleTaskAtLine(line: Int) {
        _uiState.update { it.copy(content = Markdown.toggleTaskAtLine(it.content, line), saved = false) }
        scheduleAutosave()
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MS)
            persist()
        }
    }

    /** Called when the editor is leaving the screen; cancels the pending debounce and writes. */
    fun saveNow() {
        autosaveJob?.cancel()
        viewModelScope.launch { persist() }
    }

    private suspend fun persist() {
        val state = _uiState.value
        if (state.loading) return
        if (state.isBlank && persistedId == 0L) return

        val now = clock.now()
        // A note with a body but no heading still deserves a name in the list.
        val title = state.title.ifBlank { state.content.lineSequence().firstOrNull()?.take(60)?.trim().orEmpty() }
        val preview = Markdown.toPlainText(state.content)

        if (persistedId == 0L) {
            persistedId = notes.insert(
                NoteEntity(
                    title = title,
                    content = state.content,
                    preview = preview,
                    pinned = state.pinned,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            _uiState.update { it.copy(id = persistedId, saved = true) }
        } else {
            val existing = notes.getById(persistedId) ?: return
            notes.update(
                existing.copy(
                    title = title,
                    content = state.content,
                    preview = preview,
                    pinned = state.pinned,
                    updatedAt = now,
                )
            )
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        autosaveJob?.cancel()
        if (persistedId != 0L) notes.deleteById(persistedId)
        onDeleted()
    }

    companion object {
        private const val AUTOSAVE_DELAY_MS = 600L

        fun factory(noteId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this as CreationExtras).appContainer
                NoteEditorViewModel(noteId, container.database.noteDao(), container.clock)
            }
        }
    }
}
