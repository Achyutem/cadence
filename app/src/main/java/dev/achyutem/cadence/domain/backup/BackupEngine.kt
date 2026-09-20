package dev.achyutem.cadence.domain.backup

import androidx.room.withTransaction
import dev.achyutem.cadence.core.database.CadenceDatabase
import dev.achyutem.cadence.core.database.entity.DailyCheckInEntity
import dev.achyutem.cadence.core.database.entity.HabitEntity
import dev.achyutem.cadence.core.database.entity.HabitEntryEntity
import dev.achyutem.cadence.core.database.entity.HabitGoalDirection
import dev.achyutem.cadence.core.database.entity.HabitType
import dev.achyutem.cadence.core.database.entity.NoteEntity
import dev.achyutem.cadence.core.database.entity.RecurrenceFrequency
import dev.achyutem.cadence.core.database.entity.RecurrenceRuleEntity
import dev.achyutem.cadence.core.database.entity.ReminderEntity
import dev.achyutem.cadence.core.database.entity.TagEntity
import dev.achyutem.cadence.core.database.entity.TaskEntity
import dev.achyutem.cadence.core.database.entity.TaskOccurrenceEntity
import dev.achyutem.cadence.core.database.entity.TaskPriority
import dev.achyutem.cadence.core.database.entity.TaskTagCrossRef
import dev.achyutem.cadence.core.datastore.AccentColor
import dev.achyutem.cadence.core.datastore.CompletedTaskBehavior
import dev.achyutem.cadence.core.datastore.SettingsRepository
import dev.achyutem.cadence.core.datastore.ThemeMode
import dev.achyutem.cadence.core.datastore.TimeFormat
import dev.achyutem.cadence.core.datastore.UserPreferences
import dev.achyutem.cadence.core.time.CadenceClock
import dev.achyutem.cadence.domain.notes.Markdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** What an import did, so the UI can report something truthful rather than "Done". */
data class ImportSummary(
    val records: Int,
    val notes: Int,
    val tasks: Int,
    val habits: Int,
    val preferencesRestored: Boolean,
)

sealed interface BackupResult {
    data class ExportSuccess(val records: Int, val bytes: Long) : BackupResult
    data class ImportSuccess(val summary: ImportSummary) : BackupResult
    data class Failure(val reason: String) : BackupResult
}

/**
 * Whole-database export and import as a single JSON document.
 *
 * ### Import replaces; it does not merge
 *
 * Merging two histories requires identity that survives export — which means either stable UUIDs
 * on every row or a conflict-resolution policy the user has to understand. Both are real designs;
 * neither is what "restore my backup" means. So import is a **full replace**, stated plainly in
 * the confirmation, and it is the only destructive action in the app.
 *
 * To make that safe, the restore runs inside one Room transaction: the old data is deleted and
 * the new data inserted atomically, so a malformed file or a crash mid-import leaves the existing
 * database exactly as it was rather than half-erased.
 *
 * Original row ids are preserved so foreign keys (subtask parents, habit entries, recurrence
 * rules) survive the round trip without remapping.
 */
class BackupEngine(
    private val database: CadenceDatabase,
    private val settings: SettingsRepository,
    private val clock: CadenceClock,
    private val appVersion: String,
) {

    private val json = Json {
        prettyPrint = true
        // A file written by a newer version still restores what this version understands.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(output: OutputStream): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val file = buildBackup()
            val bytes = json.encodeToString(BackupFile.serializer(), file).toByteArray()
            output.use { it.write(bytes) }
            BackupResult.ExportSuccess(records = file.totalRecords, bytes = bytes.size.toLong())
        }.getOrElse { BackupResult.Failure(it.readableMessage()) }
    }

    suspend fun import(input: InputStream): BackupResult = withContext(Dispatchers.IO) {
        runCatching {
            val text = input.use { it.readBytes().decodeToString() }
            val file = json.decodeFromString(BackupFile.serializer(), text)

            if (file.formatVersion > BackupFile.FORMAT_VERSION) {
                return@runCatching BackupResult.Failure(
                    "This backup was written by a newer version of Cadence.",
                )
            }

            restore(file)

            file.preferences?.let { settings.replaceAll(it.toUserPreferences()) }

            BackupResult.ImportSuccess(
                ImportSummary(
                    records = file.totalRecords,
                    notes = file.notes.size,
                    tasks = file.tasks.size,
                    habits = file.habits.size,
                    preferencesRestored = file.preferences != null,
                )
            )
        }.getOrElse { BackupResult.Failure(it.readableMessage()) }
    }

    /** A suggested filename. Date-stamped so successive exports do not overwrite each other. */
    fun suggestedFileName(): String = "cadence-backup-${clock.today()}.json"

    private suspend fun buildBackup(): BackupFile {
        val prefs = settings.preferences.first()
        val taskDao = database.taskDao()
        val habitDao = database.habitDao()

        // Deliberately unbounded: this is the one operation that *should* read everything.
        val tasks = taskDao.getAllForBackup()
        val occurrences = taskDao.getAllOccurrencesForBackup()
        val habits = habitDao.getAllForBackup()
        val entries = habitDao.getAllEntriesForBackup()

        return BackupFile(
            appVersion = appVersion,
            exportedAt = clock.now().toString(),
            preferences = prefs.toBackup(),
            tasks = tasks.map { it.toBackup() },
            taskOccurrences = occurrences.map { it.toBackup() },
            habits = habits.map { it.toBackup() },
            habitEntries = entries.map { it.toBackup() },
            checkIns = database.checkInDao().getAllForBackup().map { it.toBackup() },
            recurrenceRules = database.recurrenceDao().getAllRulesForBackup().map { it.toBackup() },
            reminders = database.recurrenceDao().getAllRemindersForBackup().map { it.toBackup() },
            notes = database.noteDao().getAll().map { it.toBackup() },
            tags = database.tagDao().getAllForBackup().map { BackupTag(it.id, it.name) },
            taskTags = database.tagDao().getAllCrossRefsForBackup().map { BackupTaskTag(it.taskId, it.tagId) },
        )
    }

    /**
     * One transaction for the whole restore. If anything in here throws, Room rolls the entire
     * thing back and the user still has the data they had a moment ago.
     */
    private suspend fun restore(file: BackupFile) = database.withTransaction {
        // Insertion order follows the foreign keys: rules and reminders exist before the tasks
        // and habits that point at them, parents before children.
        database.taskDao().deleteAllOccurrences()
        database.tagDao().deleteAllCrossRefs()
        database.taskDao().deleteAllTasks()
        database.habitDao().deleteAllEntries()
        database.habitDao().deleteAllHabits()
        database.checkInDao().deleteAll()
        database.noteDao().deleteAll()
        database.tagDao().deleteAllTags()
        database.recurrenceDao().deleteAllReminders()
        database.recurrenceDao().deleteAllRules()

        database.recurrenceDao().insertRules(file.recurrenceRules.map { it.toEntity() })
        database.recurrenceDao().insertReminders(file.reminders.map { it.toEntity() })

        // Parents first: a subtask's row would violate its foreign key otherwise.
        val (parents, children) = file.tasks.partition { it.parentTaskId == null }
        database.taskDao().insertAll(parents.map { it.toEntity() })
        database.taskDao().insertAll(children.map { it.toEntity() })
        database.taskDao().insertOccurrences(file.taskOccurrences.map { it.toEntity() })

        database.habitDao().insertAll(file.habits.map { it.toEntity() })
        database.habitDao().insertEntries(file.habitEntries.map { it.toEntity() })

        database.checkInDao().insertAll(file.checkIns.map { it.toEntity() })
        database.noteDao().insertAll(file.notes.map { it.toEntity() })
        database.tagDao().insertAll(file.tags.map { TagEntity(it.id, it.name) })
        database.tagDao().insertCrossRefs(file.taskTags.map { TaskTagCrossRef(it.taskId, it.tagId) })
    }
}

/**
 * A failure message a person can act on.
 *
 * Serialization exceptions carry useful detail but also stack-shaped noise; storage failures
 * arrive as bare IO exceptions. Neither should reach the user raw.
 */
private fun Throwable.readableMessage(): String = when (this) {
    is kotlinx.serialization.SerializationException ->
        "That file is not a Cadence backup, or it is damaged."
    is java.io.IOException -> "Could not read or write the file."
    else -> message ?: "Something went wrong."
}

// --- Entity ↔ DTO ---

private fun UserPreferences.toBackup() = BackupPreferences(
    displayName = displayName,
    themeMode = themeMode.name,
    accentColor = accentColor.name,
    useDynamicColor = useDynamicColor,
    reducedMotion = reducedMotion,
    weekStartsOn = weekStartsOn.value,
    completedTaskBehavior = completedTaskBehavior.name,
    defaultTaskDurationMinutes = defaultTaskDurationMinutes,
    timeFormat = timeFormat.name,
    defaultReminderLeadMinutes = defaultReminderLeadMinutes,
    quietHoursEnabled = quietHoursEnabled,
    quietHoursStart = quietHoursStart.toString(),
    quietHoursEnd = quietHoursEnd.toString(),
    showCompletedOnToday = showCompletedOnToday,
    checkInPromptEnabled = checkInPromptEnabled,
)

/** Every field falls back to its default if the file holds something unrecognised. */
private fun BackupPreferences.toUserPreferences() = UserPreferences(
    displayName = displayName.take(UserPreferences.MAX_NAME_LENGTH),
    themeMode = themeMode.toEnumOr(ThemeMode.SYSTEM),
    accentColor = accentColor.toEnumOr(AccentColor.BLUE),
    useDynamicColor = useDynamicColor,
    reducedMotion = reducedMotion,
    weekStartsOn = weekStartsOn.takeIf { it in 1..7 }?.let(DayOfWeek::of) ?: DayOfWeek.MONDAY,
    completedTaskBehavior = completedTaskBehavior.toEnumOr(CompletedTaskBehavior.MOVE_TO_BOTTOM),
    defaultTaskDurationMinutes = defaultTaskDurationMinutes.coerceIn(5, 8 * 60),
    timeFormat = timeFormat.toEnumOr(TimeFormat.SYSTEM),
    defaultReminderLeadMinutes = defaultReminderLeadMinutes.coerceIn(0, 24 * 60),
    quietHoursEnabled = quietHoursEnabled,
    quietHoursStart = quietHoursStart.toLocalTimeOr(LocalTime.of(22, 0)),
    quietHoursEnd = quietHoursEnd.toLocalTimeOr(LocalTime.of(7, 0)),
    showCompletedOnToday = showCompletedOnToday,
    checkInPromptEnabled = checkInPromptEnabled,
)

private inline fun <reified T : Enum<T>> String.toEnumOr(fallback: T): T =
    runCatching { enumValueOf<T>(this) }.getOrDefault(fallback)

private fun String.toLocalTimeOr(fallback: LocalTime): LocalTime =
    runCatching { LocalTime.parse(this) }.getOrDefault(fallback)

private fun TaskEntity.toBackup() = BackupTask(
    id = id, title = title, notes = notes,
    scheduledDate = scheduledDate?.toString(), startTime = startTime?.toString(),
    durationMinutes = durationMinutes, priority = priority.name,
    completed = completed, completedAt = completedAt?.toString(),
    parentTaskId = parentTaskId, recurrenceRuleId = recurrenceRuleId, reminderId = reminderId,
    sortOrder = sortOrder, archived = archived,
    createdAt = createdAt.toString(), updatedAt = updatedAt.toString(),
)

private fun BackupTask.toEntity() = TaskEntity(
    id = id, title = title, notes = notes,
    scheduledDate = scheduledDate?.let(LocalDate::parse),
    startTime = startTime?.let(LocalTime::parse),
    durationMinutes = durationMinutes,
    priority = priority.toEnumOr(TaskPriority.NONE),
    completed = completed, completedAt = completedAt?.let(Instant::parse),
    parentTaskId = parentTaskId, recurrenceRuleId = recurrenceRuleId, reminderId = reminderId,
    sortOrder = sortOrder, archived = archived,
    createdAt = Instant.parse(createdAt), updatedAt = Instant.parse(updatedAt),
)

private fun TaskOccurrenceEntity.toBackup() = BackupTaskOccurrence(
    id = id, taskId = taskId, date = date.toString(), completed = completed,
    completedAt = completedAt?.toString(), skipped = skipped,
    createdAt = createdAt.toString(), updatedAt = updatedAt.toString(),
)

private fun BackupTaskOccurrence.toEntity() = TaskOccurrenceEntity(
    id = id, taskId = taskId, date = LocalDate.parse(date), completed = completed,
    completedAt = completedAt?.let(Instant::parse), skipped = skipped,
    createdAt = Instant.parse(createdAt), updatedAt = Instant.parse(updatedAt),
)

private fun HabitEntity.toBackup() = BackupHabit(
    id = id, name = name, description = description, type = type.name,
    targetValue = targetValue, goalDirection = goalDirection.name, unit = unit,
    recurrenceRuleId = recurrenceRuleId, reminderId = reminderId,
    startDate = startDate.toString(), sortOrder = sortOrder, archived = archived,
    createdAt = createdAt.toString(), updatedAt = updatedAt.toString(),
)

private fun BackupHabit.toEntity() = HabitEntity(
    id = id, name = name, description = description,
    type = type.toEnumOr(HabitType.BOOLEAN),
    targetValue = targetValue,
    goalDirection = goalDirection.toEnumOr(HabitGoalDirection.AT_LEAST),
    unit = unit, recurrenceRuleId = recurrenceRuleId, reminderId = reminderId,
    startDate = LocalDate.parse(startDate), sortOrder = sortOrder, archived = archived,
    createdAt = Instant.parse(createdAt), updatedAt = Instant.parse(updatedAt),
)

private fun HabitEntryEntity.toBackup() = BackupHabitEntry(
    id = id, habitId = habitId, date = date.toString(), value = value,
    completed = completed, note = note,
    createdAt = createdAt.toString(), updatedAt = updatedAt.toString(),
)

private fun BackupHabitEntry.toEntity() = HabitEntryEntity(
    id = id, habitId = habitId, date = LocalDate.parse(date), value = value,
    completed = completed, note = note,
    createdAt = Instant.parse(createdAt), updatedAt = Instant.parse(updatedAt),
)

private fun DailyCheckInEntity.toBackup() = BackupCheckIn(
    id = id, date = date.toString(), mood = mood, energy = energy, note = note,
    createdAt = createdAt.toString(), updatedAt = updatedAt.toString(),
)

private fun BackupCheckIn.toEntity() = DailyCheckInEntity(
    id = id, date = LocalDate.parse(date),
    mood = mood.coerceIn(DailyCheckInEntity.SCALE_MIN, DailyCheckInEntity.SCALE_MAX),
    energy = energy.coerceIn(DailyCheckInEntity.SCALE_MIN, DailyCheckInEntity.SCALE_MAX),
    note = note, createdAt = Instant.parse(createdAt), updatedAt = Instant.parse(updatedAt),
)

private fun RecurrenceRuleEntity.toBackup() = BackupRecurrenceRule(
    id = id, frequency = frequency.name, interval = interval,
    daysOfWeek = daysOfWeek?.map { it.value }?.sorted(),
    dayOfMonth = dayOfMonth, weekOfMonth = weekOfMonth,
    weekdayOfMonth = weekdayOfMonth?.value, monthOfYear = monthOfYear,
    anchorDate = anchorDate.toString(), endDate = endDate?.toString(),
    occurrenceLimit = occurrenceLimit,
)

private fun BackupRecurrenceRule.toEntity() = RecurrenceRuleEntity(
    id = id,
    frequency = frequency.toEnumOr(RecurrenceFrequency.DAILY),
    interval = interval.coerceAtLeast(1),
    daysOfWeek = daysOfWeek?.filter { it in 1..7 }?.map(DayOfWeek::of)?.toSet(),
    dayOfMonth = dayOfMonth, weekOfMonth = weekOfMonth,
    weekdayOfMonth = weekdayOfMonth?.takeIf { it in 1..7 }?.let(DayOfWeek::of),
    monthOfYear = monthOfYear, anchorDate = LocalDate.parse(anchorDate),
    endDate = endDate?.let(LocalDate::parse), occurrenceLimit = occurrenceLimit,
)

private fun ReminderEntity.toBackup() = BackupReminder(
    id = id, timeOfDay = timeOfDay.toString(), leadMinutes = leadMinutes, enabled = enabled,
)

private fun BackupReminder.toEntity() = ReminderEntity(
    id = id, timeOfDay = LocalTime.parse(timeOfDay), leadMinutes = leadMinutes, enabled = enabled,
)

private fun NoteEntity.toBackup() = BackupNote(
    id = id, title = title, content = content, pinned = pinned, archived = archived,
    sortOrder = sortOrder, createdAt = createdAt.toString(), updatedAt = updatedAt.toString(),
)

// `preview` is not in the backup format: it is a write-time cache of `content`, so it is
// regenerated on import rather than trusted from the file.
private fun BackupNote.toEntity() = NoteEntity(
    id = id, title = title, content = content,
    preview = Markdown.toPlainText(content),
    pinned = pinned, archived = archived, sortOrder = sortOrder,
    createdAt = Instant.parse(createdAt), updatedAt = Instant.parse(updatedAt),
)
