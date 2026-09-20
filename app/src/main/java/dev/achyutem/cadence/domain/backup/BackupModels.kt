package dev.achyutem.cadence.domain.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The on-disk backup format.
 *
 * ### Why a hand-written DTO layer instead of serialising the Room entities
 *
 * A backup file is a **contract with the user's future self**. Room entities change shape every
 * time the schema does; if the file format were the entity, then renaming a column would silently
 * invalidate every backup ever taken. These types change only when the format is deliberately
 * revised, and [BackupFile.formatVersion] says which revision a file is.
 *
 * Every date and time is written as ISO text rather than a number. A backup should be readable
 * and repairable in a text editor years from now, when the reader may not be this app, an epoch
 * millisecond is not something a person can check.
 *
 * Unknown keys are ignored on read, so a file written by a *newer* version still restores
 * whatever this version understands instead of failing outright.
 */
@Serializable
data class BackupFile(
    @SerialName("format_version") val formatVersion: Int = FORMAT_VERSION,
    @SerialName("app_version") val appVersion: String = "",
    @SerialName("exported_at") val exportedAt: String,
    val preferences: BackupPreferences? = null,
    val tasks: List<BackupTask> = emptyList(),
    @SerialName("task_occurrences") val taskOccurrences: List<BackupTaskOccurrence> = emptyList(),
    val habits: List<BackupHabit> = emptyList(),
    @SerialName("habit_entries") val habitEntries: List<BackupHabitEntry> = emptyList(),
    @SerialName("check_ins") val checkIns: List<BackupCheckIn> = emptyList(),
    @SerialName("recurrence_rules") val recurrenceRules: List<BackupRecurrenceRule> = emptyList(),
    val reminders: List<BackupReminder> = emptyList(),
    val notes: List<BackupNote> = emptyList(),
    @SerialName("breathing_sessions") val breathingSessions: List<BackupBreathingSession> = emptyList(),
    val tags: List<BackupTag> = emptyList(),
    @SerialName("task_tags") val taskTags: List<BackupTaskTag> = emptyList(),
) {
    companion object {
        /** Bump only when the format changes incompatibly. */
        const val FORMAT_VERSION = 1
    }

    val totalRecords: Int
        get() = tasks.size + taskOccurrences.size + habits.size + habitEntries.size +
            checkIns.size + recurrenceRules.size + reminders.size + notes.size +
            breathingSessions.size + tags.size + taskTags.size
}

@Serializable
data class BackupPreferences(
    @SerialName("display_name") val displayName: String = "",
    @SerialName("theme_mode") val themeMode: String = "SYSTEM",
    @SerialName("accent_color") val accentColor: String = "BLUE",
    @SerialName("use_dynamic_color") val useDynamicColor: Boolean = false,
    @SerialName("reduced_motion") val reducedMotion: Boolean = false,
    @SerialName("week_starts_on") val weekStartsOn: Int = 1,
    @SerialName("completed_task_behavior") val completedTaskBehavior: String = "MOVE_TO_BOTTOM",
    @SerialName("default_task_duration_minutes") val defaultTaskDurationMinutes: Int = 30,
    @SerialName("time_format") val timeFormat: String = "SYSTEM",
    @SerialName("default_reminder_lead_minutes") val defaultReminderLeadMinutes: Int = 0,
    @SerialName("quiet_hours_enabled") val quietHoursEnabled: Boolean = false,
    @SerialName("quiet_hours_start") val quietHoursStart: String = "22:00",
    @SerialName("quiet_hours_end") val quietHoursEnd: String = "07:00",
    @SerialName("show_completed_on_today") val showCompletedOnToday: Boolean = true,
    @SerialName("check_in_prompt_enabled") val checkInPromptEnabled: Boolean = true,
)

@Serializable
data class BackupTask(
    val id: Long,
    val title: String,
    val notes: String? = null,
    @SerialName("scheduled_date") val scheduledDate: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    val priority: String = "NONE",
    val completed: Boolean = false,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("parent_task_id") val parentTaskId: Long? = null,
    @SerialName("recurrence_rule_id") val recurrenceRuleId: Long? = null,
    @SerialName("reminder_id") val reminderId: Long? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val archived: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class BackupTaskOccurrence(
    val id: Long,
    @SerialName("task_id") val taskId: Long,
    val date: String,
    val completed: Boolean = false,
    @SerialName("completed_at") val completedAt: String? = null,
    val skipped: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class BackupHabit(
    val id: Long,
    val name: String,
    val description: String? = null,
    val type: String,
    @SerialName("target_value") val targetValue: Double = 1.0,
    @SerialName("goal_direction") val goalDirection: String = "AT_LEAST",
    val unit: String? = null,
    @SerialName("recurrence_rule_id") val recurrenceRuleId: Long? = null,
    @SerialName("reminder_id") val reminderId: Long? = null,
    @SerialName("start_date") val startDate: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val archived: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class BackupHabitEntry(
    val id: Long,
    @SerialName("habit_id") val habitId: Long,
    val date: String,
    val value: Double,
    val completed: Boolean,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class BackupCheckIn(
    val id: Long,
    val date: String,
    val mood: Int,
    val energy: Int,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class BackupRecurrenceRule(
    val id: Long,
    val frequency: String,
    val interval: Int = 1,
    @SerialName("days_of_week") val daysOfWeek: List<Int>? = null,
    @SerialName("day_of_month") val dayOfMonth: Int? = null,
    @SerialName("week_of_month") val weekOfMonth: Int? = null,
    @SerialName("weekday_of_month") val weekdayOfMonth: Int? = null,
    @SerialName("month_of_year") val monthOfYear: Int? = null,
    @SerialName("anchor_date") val anchorDate: String,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("occurrence_limit") val occurrenceLimit: Int? = null,
)

@Serializable
data class BackupReminder(
    val id: Long,
    @SerialName("time_of_day") val timeOfDay: String,
    @SerialName("lead_minutes") val leadMinutes: Int = 0,
    val enabled: Boolean = true,
)

@Serializable
data class BackupNote(
    val id: Long,
    val title: String,
    val content: String = "",
    val pinned: Boolean = false,
    val archived: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class BackupBreathingSession(
    val id: Long,
    val date: String,
    val kind: String,
    @SerialName("duration_seconds") val durationSeconds: Int,
    @SerialName("rounds_completed") val roundsCompleted: Int,
    @SerialName("rounds_planned") val roundsPlanned: Int,
    @SerialName("longest_hold_seconds") val longestHoldSeconds: Int,
    val completed: Boolean,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class BackupTag(val id: Long, val name: String)

@Serializable
data class BackupTaskTag(
    @SerialName("task_id") val taskId: Long,
    @SerialName("tag_id") val tagId: Long,
)
