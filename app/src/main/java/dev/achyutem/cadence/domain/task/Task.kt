package dev.achyutem.cadence.domain.task

import dev.achyutem.cadence.core.database.entity.TaskEntity
import dev.achyutem.cadence.core.database.entity.TaskPriority
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * A task as the UI thinks about it: the row, plus its children, plus everything derivable from
 * the two.
 *
 * This is not a second copy of [TaskEntity] for its own sake. It exists because **parent
 * completion is derived, never stored**; a parent with children is complete exactly when all its
 * children are, and writing that back to the database would create two truths that drift. Putting
 * the derivation in one place means every screen, widget and statistic agrees by construction.
 */
data class Task(
    val id: Long,
    val title: String,
    val notes: String?,
    val scheduledDate: LocalDate?,
    val startTime: LocalTime?,
    val durationMinutes: Int?,
    val priority: TaskPriority,
    val completedAt: Instant?,
    val recurrenceRuleId: Long?,
    val reminderId: Long?,
    val sortOrder: Int,
    val archived: Boolean,
    val subtasks: List<Task> = emptyList(),
    /** Set only when this task is a recurring template rendered for a particular date. */
    val occurrenceDate: LocalDate? = null,
    private val selfCompleted: Boolean = false,
) {
    val hasSubtasks: Boolean get() = subtasks.isNotEmpty()

    val isRecurring: Boolean get() = recurrenceRuleId != null

    /**
     * Completion.
     *
     * A task with children is complete when they all are. A task without children uses its own
     * flag. The parent's stored `completed` column is therefore *not consulted* whenever children
     * exist, not kept in sync, which is what makes disagreement impossible.
     */
    val completed: Boolean
        get() = if (hasSubtasks) subtasks.all { it.completed } else selfCompleted

    /** 0f..1f across children, or simply done/not-done for a leaf. */
    val progress: Float
        get() = when {
            hasSubtasks -> subtasks.count { it.completed }.toFloat() / subtasks.size
            selfCompleted -> 1f
            else -> 0f
        }

    val completedSubtaskCount: Int get() = subtasks.count { it.completed }

    /** End of the time block, when there is one. Used by the timeline and day planning. */
    val endTime: LocalTime?
        get() = startTime?.plusMinutes((durationMinutes ?: 0).toLong())

    val isScheduled: Boolean get() = scheduledDate != null

    val isTimed: Boolean get() = startTime != null

    fun isOverdue(today: LocalDate, now: LocalTime): Boolean {
        if (completed || archived) return false
        val date = scheduledDate ?: return false
        return when {
            date < today -> true
            date > today -> false
            // Today: only overdue once its start time has actually passed.
            else -> startTime?.let { it < now } ?: false
        }
    }
}

/** Build a [Task] tree from a parent row and the children belonging to it. */
fun TaskEntity.toTask(subtasks: List<TaskEntity> = emptyList()): Task = Task(
    id = id,
    title = title,
    notes = notes,
    scheduledDate = scheduledDate,
    startTime = startTime,
    durationMinutes = durationMinutes,
    priority = priority,
    completedAt = completedAt,
    recurrenceRuleId = recurrenceRuleId,
    reminderId = reminderId,
    sortOrder = sortOrder,
    archived = archived,
    subtasks = subtasks
        .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        .map { it.toTask() },
    selfCompleted = completed,
)

/**
 * Order tasks the way a person reads a day.
 *
 * Timed tasks come first in clock order, because "what is next" is the question the list is
 * answering. Untimed tasks follow in manual order. Completed tasks sink to the bottom rather than
 * vanishing, so a finished day still reads as a record of itself.
 *
 * [sinkCompleted] is false when the caller wants a list that does not reorder under the user's
 * finger the instant they tick something.
 */
fun List<Task>.orderedForDay(sinkCompleted: Boolean = true): List<Task> = sortedWith(
    compareBy<Task> { if (sinkCompleted && it.completed) 1 else 0 }
        .thenBy { it.startTime == null }
        .thenBy { it.startTime }
        .thenByDescending { it.priority.ordinal }
        .thenBy { it.sortOrder }
        .thenBy { it.id },
)

/** Completion across a set of tasks, as a fraction. Null when there is nothing to measure. */
fun List<Task>.completionProgress(): Float? {
    if (isEmpty()) return null
    return count { it.completed }.toFloat() / size
}
