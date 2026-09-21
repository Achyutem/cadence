package dev.achyutem.cadence.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import dev.achyutem.cadence.core.database.entity.TaskEntity
import dev.achyutem.cadence.core.database.entity.TaskOccurrenceEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * Every read is range-bounded or id-bounded. There is intentionally no `SELECT * FROM tasks`:
 * the UI never needs the whole table, and years of history must not be paid for to draw one day.
 */
@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    /** Top-level tasks scheduled on a single date. Subtasks are fetched via [observeSubtasks]. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE archived = 0
          AND parentTaskId IS NULL
          AND scheduledDate = :date
        ORDER BY startTime IS NULL, startTime ASC, sortOrder ASC, id ASC
        """
    )
    fun observeScheduledOn(date: LocalDate): Flow<List<TaskEntity>>

    /** Inclusive date range, the query behind the week and month calendar views. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE archived = 0
          AND parentTaskId IS NULL
          AND scheduledDate BETWEEN :start AND :end
        ORDER BY scheduledDate ASC, startTime IS NULL, startTime ASC, sortOrder ASC, id ASC
        """
    )
    fun observeScheduledBetween(start: LocalDate, end: LocalDate): Flow<List<TaskEntity>>

    /** Unscheduled backlog. */
    @Query(
        """
        SELECT * FROM tasks
        WHERE archived = 0
          AND parentTaskId IS NULL
          AND scheduledDate IS NULL
        ORDER BY sortOrder ASC, id ASC
        """
    )
    fun observeBacklog(): Flow<List<TaskEntity>>

    /** Incomplete tasks whose date has passed, surfaced on Today as "overdue". */
    @Query(
        """
        SELECT * FROM tasks
        WHERE archived = 0
          AND parentTaskId IS NULL
          AND completed = 0
          AND recurrenceRuleId IS NULL
          AND scheduledDate IS NOT NULL
          AND scheduledDate < :today
        ORDER BY scheduledDate ASC, startTime IS NULL, startTime ASC
        """
    )
    fun observeOverdue(today: LocalDate): Flow<List<TaskEntity>>

    /** All recurring templates. Small set; the engine expands them per visible range. */
    @Query("SELECT * FROM tasks WHERE archived = 0 AND recurrenceRuleId IS NOT NULL")
    fun observeRecurringTemplates(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId ORDER BY sortOrder ASC, id ASC")
    fun observeSubtasks(parentId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE parentTaskId IN (:parentIds) ORDER BY sortOrder ASC, id ASC")
    fun observeSubtasksFor(parentIds: List<Long>): Flow<List<TaskEntity>>

    /**
     * Every subtask in one query, to be grouped by parent in memory.
     *
     * This looks like it breaks the range-bounded rule, and does not: subtasks are a small,
     * bounded set in practice (they belong to tasks a person is actively working on), and the
     * alternative, an `IN (:ids)` query re-issued whenever the visible parent list changes,
     * re-runs on every scroll and every completion. One indexed scan beats N invalidations.
     */
    @Query("SELECT * FROM tasks WHERE parentTaskId IS NOT NULL AND archived = 0 ORDER BY sortOrder ASC, id ASC")
    fun observeAllSubtasks(): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM tasks
        WHERE archived = 0 AND parentTaskId IS NULL AND scheduledDate = :date AND completed = 1
        """
    )
    fun observeCompletedCountOn(date: LocalDate): Flow<Int>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>): List<Long>

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Tick or untick a task.
     *
     * Two timestamps, not one. `completedAt` is nullable and is cleared when a task is unticked;
     * `updatedAt` is NOT NULL and is *always* now. Sharing one parameter between them meant
     * unticking passed null to both and the write died on the constraint, which crashed the app
     * on the second tap of any checkbox.
     */
    @Query(
        """
        UPDATE tasks
        SET completed = :completed, completedAt = :completedAt, updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun setCompleted(
        id: Long,
        completed: Boolean,
        completedAt: Instant?,
        updatedAt: Instant,
    )

    @Query("UPDATE tasks SET archived = :archived, updatedAt = :at WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, at: Instant)

    // --- Occurrences of recurring tasks ---

    @Query("SELECT * FROM task_occurrences WHERE taskId = :taskId AND date = :date")
    suspend fun getOccurrence(taskId: Long, date: LocalDate): TaskOccurrenceEntity?

    @Query("SELECT * FROM task_occurrences WHERE date BETWEEN :start AND :end")
    fun observeOccurrencesBetween(start: LocalDate, end: LocalDate): Flow<List<TaskOccurrenceEntity>>

    @Query("SELECT * FROM task_occurrences WHERE taskId = :taskId AND date BETWEEN :start AND :end")
    fun observeOccurrencesFor(taskId: Long, start: LocalDate, end: LocalDate): Flow<List<TaskOccurrenceEntity>>

    @Upsert
    suspend fun upsertOccurrence(occurrence: TaskOccurrenceEntity)

    @Transaction
    suspend fun setOccurrenceCompleted(taskId: Long, date: LocalDate, completed: Boolean, at: Instant) {
        val existing = getOccurrence(taskId, date)
        val updated = existing?.copy(
            completed = completed,
            completedAt = if (completed) at else null,
            updatedAt = at,
        ) ?: TaskOccurrenceEntity(
            taskId = taskId,
            date = date,
            completed = completed,
            completedAt = if (completed) at else null,
            createdAt = at,
            updatedAt = at,
        )
        upsertOccurrence(updated)
    }

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM tasks WHERE scheduledDate IS :date")
    suspend fun maxSortOrderOn(date: LocalDate?): Int

    // --- Backup ---
    //
    // The only unbounded reads in the DAO layer, and the only place they are correct: an export
    // is by definition "all of it". They are named so that nobody reaches for them to draw a
    // screen.

    @Query("SELECT * FROM tasks ORDER BY id ASC")
    suspend fun getAllForBackup(): List<TaskEntity>

    @Query("SELECT * FROM task_occurrences ORDER BY id ASC")
    suspend fun getAllOccurrencesForBackup(): List<TaskOccurrenceEntity>

    @Insert
    suspend fun insertOccurrences(occurrences: List<TaskOccurrenceEntity>)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Query("DELETE FROM task_occurrences")
    suspend fun deleteAllOccurrences()
}
