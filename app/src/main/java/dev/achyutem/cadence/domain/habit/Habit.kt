package dev.achyutem.cadence.domain.habit

import dev.achyutem.cadence.core.database.entity.HabitEntity
import dev.achyutem.cadence.core.database.entity.HabitEntryEntity
import dev.achyutem.cadence.core.database.entity.HabitGoalDirection
import dev.achyutem.cadence.core.database.entity.HabitType
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * A habit and its state for one particular day.
 *
 * The four metric types share one model, so streaks, heatmaps and statistics have exactly one
 * code path. The only thing that differs between "meditate" and "drink 8 glasses" is how a value
 * is compared against a target and how it is formatted — both of which live here.
 */
data class Habit(
    val id: Long,
    val name: String,
    val description: String?,
    val type: HabitType,
    val targetValue: Double,
    val goalDirection: HabitGoalDirection,
    val unit: String?,
    val recurrenceRuleId: Long?,
    val reminderId: Long?,
    val startDate: LocalDate,
    val sortOrder: Int,
    val archived: Boolean,
    /** The value recorded for the day being displayed. 0 when nothing has been recorded. */
    val todayValue: Double = 0.0,
    /** True when the day being displayed is one this habit is scheduled for. */
    val scheduledToday: Boolean = true,
) {
    /**
     * Is the target met?
     *
     * [HabitGoalDirection.AT_MOST] inverts the test, which is what makes "at most two coffees"
     * work: it is satisfied by *staying under*, so a day with no entry at all is a success rather
     * than a failure.
     */
    val completed: Boolean
        get() = when (goalDirection) {
            HabitGoalDirection.AT_LEAST -> todayValue >= targetValue
            HabitGoalDirection.AT_MOST -> todayValue <= targetValue
        }

    /** 0f..1f. Capped for the progress bar; [todayValue] itself is never capped. */
    val progress: Float
        get() = when {
            targetValue <= 0.0 -> if (completed) 1f else 0f
            goalDirection == HabitGoalDirection.AT_MOST ->
                (todayValue / targetValue).coerceIn(0.0, 1.0).toFloat()
            else -> (todayValue / targetValue).coerceIn(0.0, 1.0).toFloat()
        }

    /** How much one tap adds. A count goes up by one; a duration by a useful chunk. */
    val incrementStep: Double
        get() = when (type) {
            HabitType.BOOLEAN -> targetValue.coerceAtLeast(1.0)
            HabitType.COUNT -> 1.0
            HabitType.QUANTITY -> 1.0
            HabitType.DURATION -> DEFAULT_DURATION_STEP
        }

    /** "6 / 8 glasses", "42 / 30 min", "75 / 50". Booleans show no numbers at all. */
    fun formatProgress(): String = when (type) {
        HabitType.BOOLEAN -> ""
        HabitType.DURATION -> "${todayValue.trim()} / ${targetValue.trim()} min"
        HabitType.QUANTITY -> buildString {
            append("${todayValue.trim()} / ${targetValue.trim()}")
            unit?.takeIf { it.isNotBlank() }?.let { append(" $it") }
        }
        HabitType.COUNT -> "${todayValue.trim()} / ${targetValue.trim()}"
    }

    companion object {
        /** Five minutes: small enough to be accurate, large enough that logging is not tedious. */
        const val DEFAULT_DURATION_STEP = 5.0
    }
}

/**
 * Whole numbers print without a decimal point.
 *
 * "6 / 8 glasses" rather than "6.0 / 8.0 glasses". The value is stored as a Double because
 * quantities are not always whole, but almost all of them are, and the decimal is noise.
 */
internal fun Double.trim(): String =
    if (this == this.roundToInt().toDouble()) roundToInt().toString() else "%.1f".format(this)

fun HabitEntity.toHabit(
    entry: HabitEntryEntity? = null,
    scheduledToday: Boolean = true,
): Habit = Habit(
    id = id,
    name = name,
    description = description,
    type = type,
    targetValue = targetValue,
    goalDirection = goalDirection,
    unit = unit,
    recurrenceRuleId = recurrenceRuleId,
    reminderId = reminderId,
    startDate = startDate,
    sortOrder = sortOrder,
    archived = archived,
    todayValue = entry?.value ?: 0.0,
    scheduledToday = scheduledToday,
)

/**
 * Does a recorded value meet the habit's target?
 *
 * Used when writing an entry, so that `HabitEntryEntity.completed` records whether the target
 * *as it was at the time* was met — a habit whose target is raised later must not retroactively
 * un-complete days that genuinely met the old one.
 */
fun HabitEntity.isValueComplete(value: Double): Boolean = when (goalDirection) {
    HabitGoalDirection.AT_LEAST -> value >= targetValue
    HabitGoalDirection.AT_MOST -> value <= targetValue
}
