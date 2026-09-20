package dev.achyutem.cadence.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class TaskPriority { NONE, LOW, MEDIUM, HIGH }

/**
 * A task.
 *
 * Three shapes are all first-class:
 *  - date only            — "sometime Tuesday"
 *  - date + [startTime]   — appears on the calendar timeline
 *  - date + time + [durationMinutes] — occupies a block, feeds the day-planning maths
 *
 * A task with no [scheduledDate] is a backlog item; it is still a real task and appears in Todos.
 *
 * ### Recurring tasks
 * A task with a [recurrenceRuleId] is a *template*, not a row per day. Its per-date state lives
 * in [TaskOccurrenceEntity]. Nothing materialises future occurrences into the database: a
 * five-year daily task is one row, and the calendar generates dates on demand from the rule.
 *
 * ### Subtasks
 * [parentTaskId] is a single level of nesting by design. Parent completion is *derived* from
 * children in the domain layer, never written back here, so the two can never disagree.
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentTaskId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RecurrenceRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurrenceRuleId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["reminderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("scheduledDate"),
        Index("parentTaskId"),
        Index("recurrenceRuleId"),
        Index("reminderId"),
        Index(value = ["archived", "completed"]),
    ],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String? = null,
    val scheduledDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val durationMinutes: Int? = null,
    val priority: TaskPriority = TaskPriority.NONE,
    /** Only meaningful for non-recurring tasks; recurring state lives in task_occurrences. */
    val completed: Boolean = false,
    val completedAt: Instant? = null,
    val parentTaskId: Long? = null,
    val recurrenceRuleId: Long? = null,
    val reminderId: Long? = null,
    /** Manual ordering within a day / within a parent. Sparse so reorders rarely rewrite rows. */
    val sortOrder: Int = 0,
    /** Hidden from active lists but kept for history and statistics. Never hard-deleted by the UI. */
    val archived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * The completion record for one date of a recurring task. Rows exist only for dates the user
 * actually touched — an untouched future date has no row and is simply "not done yet".
 *
 * [skipped] is distinct from "not completed": a skipped occurrence is intentionally excused and
 * does not count against completion rate, whereas a missed one does.
 */
@Entity(
    tableName = "task_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["taskId", "date"], unique = true),
        Index("date"),
    ],
)
data class TaskOccurrenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val date: LocalDate,
    val completed: Boolean = false,
    val completedAt: Instant? = null,
    val skipped: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "task_tags",
    primaryKeys = ["taskId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class TaskTagCrossRef(
    val taskId: Long,
    val tagId: Long,
)
