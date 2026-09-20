package dev.achyutem.cadence.widget

import android.content.Context
import dev.achyutem.cadence.CadenceApplication
import dev.achyutem.cadence.core.common.AppContainer
import dev.achyutem.cadence.core.database.entity.TaskEntity
import dev.achyutem.cadence.core.datastore.UserPreferences
import dev.achyutem.cadence.domain.habit.Habit
import dev.achyutem.cadence.domain.habit.toHabit
import dev.achyutem.cadence.domain.recurrence.RecurrenceEngine
import dev.achyutem.cadence.domain.statistics.HabitStatistics
import dev.achyutem.cadence.domain.task.Task
import dev.achyutem.cadence.domain.task.orderedForDay
import dev.achyutem.cadence.domain.task.toTask
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Everything a widget needs, in one snapshot.
 *
 * Widgets read **the same DAOs and the same domain functions as the app**. There is no widget
 * cache, no parallel query layer and no duplicated notion of "is this habit due today" — which is
 * the usual way widget and app quietly start disagreeing about the user's day.
 *
 * Loaded as a one-shot suspend read rather than as Flows: `provideGlance` runs once per update
 * and then the composition is serialised into a `RemoteViews` tree. There is nothing on the other
 * side to receive a second emission.
 */
data class WidgetSnapshot(
    val today: LocalDate,
    val preferences: UserPreferences,
    val tasks: List<Task>,
    val overdue: List<Task>,
    val habits: List<Habit>,
    val noteCount: Int,
) {
    val completedTasks: Int get() = tasks.count { it.completed }
    val totalTasks: Int get() = tasks.size
    val remainingTasks: List<Task> get() = tasks.filterNot { it.completed }
    val taskProgress: Float? get() = if (tasks.isEmpty()) null else completedTasks.toFloat() / tasks.size
    val completedHabits: Int get() = habits.count { it.completed }
    val nextTask: Task? get() = remainingTasks.firstOrNull { it.startTime != null } ?: remainingTasks.firstOrNull()
}

/** The container, reached the same way the app reaches it. */
fun Context.appContainer(): AppContainer =
    (applicationContext as CadenceApplication).container

suspend fun AppContainer.loadWidgetSnapshot(): WidgetSnapshot {
    val today = clock.today()
    val preferences = settingsRepository.preferences.first()

    val scheduled = taskDao.observeScheduledOn(today).first()
    val overdue = taskDao.observeOverdue(today).first()
    val subtasks = taskDao.observeAllSubtasks().first()
    val childrenByParent = subtasks.groupBy { it.parentTaskId }
    fun TaskEntity.build(): Task = toTask(childrenByParent[id].orEmpty())

    val habitEntities = habitDao.observeActive().first()
    val entries = habitDao.observeEntriesOn(today).first()
    val rules = recurrenceDao.observeAllRules().first().associateBy { it.id }
    val entriesByHabit = entries.associateBy { it.habitId }

    return WidgetSnapshot(
        today = today,
        preferences = preferences,
        tasks = scheduled.map { it.build() }.orderedForDay(),
        overdue = overdue.map { it.build() }.filterNot { it.completed },
        habits = habitEntities
            .map { habit ->
                val rule = habit.recurrenceRuleId?.let(rules::get)
                habit.toHabit(
                    entry = entriesByHabit[habit.id],
                    scheduledToday = rule == null || RecurrenceEngine.occursOn(rule, today),
                )
            }
            .filter { it.scheduledToday },
        noteCount = database.noteDao().observeCount().first(),
    )
}

/** One habit plus the history the single-habit and heatmap widgets need. */
data class HabitWidgetSnapshot(
    val habit: Habit?,
    val preferences: UserPreferences,
    val today: LocalDate,
    val currentStreak: Int,
    val heatmapLevels: Map<LocalDate, Int>,
    val heatmapStart: LocalDate,
)

suspend fun AppContainer.loadHabitSnapshot(habitId: Long, heatmapDays: Long): HabitWidgetSnapshot {
    val today = clock.today()
    val preferences = settingsRepository.preferences.first()
    val entity = habitDao.getById(habitId)
    val start = today.minusDays(heatmapDays - 1)

    if (entity == null) {
        return HabitWidgetSnapshot(null, preferences, today, 0, emptyMap(), start)
    }

    val entries = habitDao.getEntriesBetween(habitId, start, today)
    val rule = entity.recurrenceRuleId?.let { recurrenceDao.getRule(it) }
    val todayEntry = habitDao.getEntry(habitId, today)

    return HabitWidgetSnapshot(
        habit = entity.toHabit(entry = todayEntry),
        preferences = preferences,
        today = today,
        // The same statistics functions the detail screen uses — a streak cannot read differently
        // on the home screen than it does inside the app.
        currentStreak = HabitStatistics.currentStreak(entries, rule, entity.startDate, today),
        heatmapLevels = HabitStatistics.heatmapLevels(entries, entity.targetValue, start, today),
        heatmapStart = start,
    )
}
