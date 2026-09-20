package dev.achyutem.cadence.feature.taskdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.achyutem.cadence.core.common.AppContainer
import dev.achyutem.cadence.core.common.appContainer
import dev.achyutem.cadence.core.database.dao.RecurrenceDao
import dev.achyutem.cadence.core.database.dao.TaskDao
import dev.achyutem.cadence.core.database.entity.RecurrenceRuleEntity
import dev.achyutem.cadence.core.database.entity.ReminderEntity
import dev.achyutem.cadence.core.database.entity.TaskEntity
import dev.achyutem.cadence.core.database.entity.TaskPriority
import dev.achyutem.cadence.core.datastore.SettingsRepository
import dev.achyutem.cadence.core.datastore.UserPreferences
import dev.achyutem.cadence.core.time.CadenceClock
import dev.achyutem.cadence.domain.task.Task
import dev.achyutem.cadence.domain.task.toTask
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class TaskDetailUiState(
    val task: Task? = null,
    val rule: RecurrenceRuleEntity? = null,
    val reminder: ReminderEntity? = null,
    val preferences: UserPreferences = UserPreferences.Default,
    val today: LocalDate = LocalDate.EPOCH,
    val loading: Boolean = true,
    val deleted: Boolean = false,
)

/**
 * Editing one task.
 *
 * Every change writes immediately. There is no draft, no save button and no cancel, for the same
 * reason the note editor has none: a half-finished edit that vanishes because the user pressed
 * back is a worse failure than any of the edits themselves.
 *
 * Every write reschedules reminders and refreshes widgets, because changing a task's time,
 * recurrence or completion all change what should fire and what the home screen should say.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TaskDetailViewModel(
    private val taskId: Long,
    private val tasks: TaskDao,
    private val recurrence: RecurrenceDao,
    private val settings: SettingsRepository,
    private val clock: CadenceClock,
    private val container: AppContainer,
) : ViewModel() {

    private val refreshKey = MutableStateFlow(0)

    val uiState: StateFlow<TaskDetailUiState> = combine(
        tasks.observeById(taskId),
        tasks.observeSubtasks(taskId),
        settings.preferences,
        refreshKey,
    ) { entity, subtasks, preferences, _ ->
        if (entity == null) {
            TaskDetailUiState(loading = false, deleted = true)
        } else {
            TaskDetailUiState(
                task = entity.toTask(subtasks),
                rule = entity.recurrenceRuleId?.let { recurrence.getRule(it) },
                reminder = entity.reminderId?.let { recurrence.getReminder(it) },
                preferences = preferences,
                today = clock.today(),
                loading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailUiState())

    private fun edit(block: suspend (TaskEntity) -> TaskEntity) = viewModelScope.launch {
        val current = tasks.getById(taskId) ?: return@launch
        tasks.update(block(current).copy(updatedAt = clock.now()))
        refreshKey.value++
        afterWrite()
    }

    fun setTitle(value: String) = edit { it.copy(title = value.trim().ifEmpty { it.title }) }

    fun setNotes(value: String) = edit { it.copy(notes = value.ifBlank { null }) }

    fun setDate(date: LocalDate?) = edit { it.copy(scheduledDate = date) }

    fun setTime(time: LocalTime?) = edit {
        it.copy(
            startTime = time,
            // Dropping the time drops the duration with it: a duration with no start is not a
            // block, it is a number nothing can place.
            durationMinutes = if (time == null) null else it.durationMinutes,
        )
    }

    fun setDuration(minutes: Int?) = edit { it.copy(durationMinutes = minutes) }

    fun setPriority(priority: TaskPriority) = edit { it.copy(priority = priority) }

    fun setCompleted(completed: Boolean) = viewModelScope.launch {
        val at = clock.now()
        tasks.observeSubtasks(taskId).first().forEach { child ->
            tasks.setCompleted(child.id, completed, if (completed) at else null)
        }
        tasks.setCompleted(taskId, completed, if (completed) at else null)
        refreshKey.value++
        afterWrite()
    }

    /**
     * Attach or replace the recurrence rule.
     *
     * A new rule row is inserted rather than the old one edited, so occurrences already recorded
     * against the old phase stay reconstructible. See `docs/RECURRENCE.md`.
     */
    fun setRecurrence(rule: RecurrenceRuleEntity?) = viewModelScope.launch {
        val current = tasks.getById(taskId) ?: return@launch
        val newId = rule?.let { recurrence.insertRule(it) }
        tasks.update(current.copy(recurrenceRuleId = newId, updatedAt = clock.now()))
        current.recurrenceRuleId?.let { old ->
            recurrence.getRule(old)?.let { recurrence.deleteRule(it) }
        }
        refreshKey.value++
        afterWrite()
    }

    fun setReminder(reminder: ReminderEntity?) = viewModelScope.launch {
        val current = tasks.getById(taskId) ?: return@launch
        val newId = reminder?.let { recurrence.insertReminder(it) }
        tasks.update(current.copy(reminderId = newId, updatedAt = clock.now()))
        current.reminderId?.let { old ->
            recurrence.getReminder(old)?.let { recurrence.deleteReminder(it) }
        }
        refreshKey.value++
        afterWrite()
    }

    fun addSubtask(title: String) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        val now = clock.now()
        val parent = tasks.getById(taskId) ?: return@launch
        tasks.insert(
            TaskEntity(
                title = title.trim(),
                parentTaskId = taskId,
                scheduledDate = parent.scheduledDate,
                sortOrder = (tasks.observeSubtasks(taskId).first().maxOfOrNull { it.sortOrder } ?: 0) + 100,
                createdAt = now,
                updatedAt = now,
            )
        )
        afterWrite()
    }

    fun setSubtaskCompleted(subtask: Task, completed: Boolean) = viewModelScope.launch {
        tasks.setCompleted(subtask.id, completed, if (completed) clock.now() else null)
        afterWrite()
    }

    fun deleteSubtask(subtask: Task) = viewModelScope.launch {
        tasks.deleteById(subtask.id)
        afterWrite()
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        tasks.deleteById(taskId)
        afterWrite()
        onDeleted()
    }

    private suspend fun afterWrite() {
        container.reminderScheduler.rescheduleAll()
        container.refreshWidgets()
    }

    companion object {
        fun factory(taskId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this as CreationExtras).appContainer
                TaskDetailViewModel(
                    taskId,
                    container.taskDao,
                    container.recurrenceDao,
                    container.settingsRepository,
                    container.clock,
                    container,
                )
            }
        }
    }
}
