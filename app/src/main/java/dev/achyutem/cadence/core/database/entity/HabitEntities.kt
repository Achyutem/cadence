package dev.achyutem.cadence.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * Habits are not booleans. One metric model covers all four shapes so that streaks, heatmaps and
 * statistics have exactly one code path.
 */
enum class HabitType {
    /** Done / not done. [HabitEntity.targetValue] is 1.0. */
    BOOLEAN,

    /** A count of repetitions: 75 / 50 push-ups. Exceeding the target is allowed and shown. */
    COUNT,

    /** A quantity with a unit: 6 / 8 glasses. */
    QUANTITY,

    /** Minutes: 42 / 30 min. Stored in minutes, formatted for display. */
    DURATION,
}

/**
 * How to read [HabitEntity.targetValue] — "at least 8 glasses" and "at most 2 coffees" are both
 * useful and need opposite completion tests.
 */
enum class HabitGoalDirection { AT_LEAST, AT_MOST }

@Entity(
    tableName = "habits",
    foreignKeys = [
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
    indices = [Index("recurrenceRuleId"), Index("reminderId"), Index("archived")],
)
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val type: HabitType,
    val targetValue: Double = 1.0,
    val goalDirection: HabitGoalDirection = HabitGoalDirection.AT_LEAST,
    /** Display unit for QUANTITY ("glasses", "pages"). Null for BOOLEAN/COUNT/DURATION. */
    val unit: String? = null,
    /** Null means "every day" — the engine substitutes a daily rule rather than special-casing. */
    val recurrenceRuleId: Long? = null,
    val reminderId: Long? = null,
    /**
     * Statistics never look before this date. Without it, creating a habit today would show a
     * year of "missed" days behind it.
     */
    val startDate: LocalDate,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * One row per habit per date the user recorded something. This is the historical record from
 * which every derived number is computed.
 *
 * Nothing in the database stores a streak, a percentage or an average. Those are functions of
 * these rows plus the habit's recurrence rule, computed in the domain layer — so they can never
 * drift out of sync with the data, and editing a past day immediately corrects all history.
 *
 * [completed] is stored rather than recomputed from [value] because the habit's target can change
 * later, and a day that genuinely met the target it had at the time should stay met.
 */
@Entity(
    tableName = "habit_entries",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["habitId", "date"], unique = true),
        Index("date"),
    ],
)
data class HabitEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val date: LocalDate,
    val value: Double,
    val completed: Boolean,
    val note: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * A day's optional reflection. Never required, never nagged about, never scored.
 *
 * [mood] and [energy] are both 1..5. A coarse scale is deliberate: it is answerable in one tap,
 * and five buckets is about as much resolution as self-report actually carries.
 */
@Entity(
    tableName = "daily_check_ins",
    indices = [Index(value = ["date"], unique = true)],
)
data class DailyCheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val mood: Int,
    val energy: Int,
    val note: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        const val SCALE_MIN = 1
        const val SCALE_MAX = 5
    }
}
