package dev.achyutem.cadence

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.achyutem.cadence.core.database.CadenceDatabase
import dev.achyutem.cadence.core.database.entity.HabitEntity
import dev.achyutem.cadence.core.database.entity.HabitEntryEntity
import dev.achyutem.cadence.core.database.entity.HabitType
import dev.achyutem.cadence.core.database.entity.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * Verifies the schema actually works on a device: converters resolve, foreign keys cascade, the
 * unique indices hold, and the range queries return what the calendar will ask for.
 *
 * Runs against an in-memory database so it leaves nothing behind.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSmokeTest {

    private lateinit var db: CadenceDatabase

    private val now: Instant = Instant.parse("2026-09-20T10:15:30Z")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CadenceDatabase::class.java,
        ).build()
        // In-memory builders skip the production callback, so enforce FKs explicitly.
        db.openHelper.writableDatabase.execSQL("PRAGMA foreign_keys = ON")
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun taskRoundTripsThroughConverters() = runTest {
        val id = db.taskDao().insert(
            TaskEntity(
                title = "Finish subscription UI",
                scheduledDate = LocalDate.of(2026, 9, 21),
                startTime = java.time.LocalTime.of(11, 0),
                durationMinutes = 90,
                createdAt = now,
                updatedAt = now,
            )
        )

        val loaded = db.taskDao().getById(id)
        assertNotNull(loaded)
        assertEquals(LocalDate.of(2026, 9, 21), loaded!!.scheduledDate)
        assertEquals(java.time.LocalTime.of(11, 0), loaded.startTime)
        assertEquals(now, loaded.createdAt)
    }

    @Test
    fun scheduledRangeQueryExcludesDatesOutsideTheWindow() = runTest {
        val dao = db.taskDao()
        listOf(
            LocalDate.of(2026, 9, 19),
            LocalDate.of(2026, 9, 20),
            LocalDate.of(2026, 9, 25),
            LocalDate.of(2026, 10, 1),
        ).forEachIndexed { index, date ->
            dao.insert(TaskEntity(title = "t$index", scheduledDate = date, createdAt = now, updatedAt = now))
        }

        val inWeek = dao.observeScheduledBetween(
            LocalDate.of(2026, 9, 20),
            LocalDate.of(2026, 9, 26),
        ).first()

        assertEquals(2, inWeek.size)
    }

    @Test
    fun deletingAParentCascadesToSubtasks() = runTest {
        val dao = db.taskDao()
        val parentId = dao.insert(TaskEntity(title = "Parent", createdAt = now, updatedAt = now))
        dao.insert(TaskEntity(title = "Child", parentTaskId = parentId, createdAt = now, updatedAt = now))

        assertEquals(1, dao.observeSubtasks(parentId).first().size)

        dao.deleteById(parentId)

        assertEquals(0, dao.observeSubtasks(parentId).first().size)
    }

    @Test
    fun habitEntryIsUniquePerHabitAndDate() = runTest {
        val habitDao = db.habitDao()
        val habitId = habitDao.insert(
            HabitEntity(
                name = "Water",
                type = HabitType.QUANTITY,
                targetValue = 8.0,
                unit = "glasses",
                startDate = LocalDate.of(2026, 9, 1),
                createdAt = now,
                updatedAt = now,
            )
        )
        val date = LocalDate.of(2026, 9, 20)

        habitDao.upsertEntry(
            HabitEntryEntity(
                habitId = habitId, date = date, value = 3.0, completed = false,
                createdAt = now, updatedAt = now,
            )
        )
        // Upsert again for the same day; this must replace, not accumulate.
        habitDao.upsertEntry(
            habitDao.getEntry(habitId, date)!!.copy(value = 8.0, completed = true, updatedAt = now)
        )

        val entries = habitDao.getEntriesBetween(habitId, date, date)
        assertEquals(1, entries.size)
        assertEquals(8.0, entries.single().value, 0.0)
    }

    @Test
    fun deletingAHabitCascadesToItsEntries() = runTest {
        val habitDao = db.habitDao()
        val habit = HabitEntity(
            name = "Reading",
            type = HabitType.DURATION,
            targetValue = 30.0,
            startDate = LocalDate.of(2026, 9, 1),
            createdAt = now,
            updatedAt = now,
        )
        val habitId = habitDao.insert(habit)
        habitDao.upsertEntry(
            HabitEntryEntity(
                habitId = habitId, date = LocalDate.of(2026, 9, 20), value = 42.0,
                completed = true, createdAt = now, updatedAt = now,
            )
        )

        habitDao.delete(habit.copy(id = habitId))

        assertEquals(0, habitDao.getEntriesBetween(habitId, LocalDate.MIN, LocalDate.MAX).size)
    }

    @Test
    fun recurringTaskOccurrenceIsCreatedOnFirstCompletion() = runTest {
        val dao = db.taskDao()
        val taskId = dao.insert(TaskEntity(title = "Stretch", createdAt = now, updatedAt = now))
        val date = LocalDate.of(2026, 9, 20)

        assertNull(dao.getOccurrence(taskId, date))

        dao.setOccurrenceCompleted(taskId, date, completed = true, at = now)
        assertEquals(true, dao.getOccurrence(taskId, date)?.completed)

        dao.setOccurrenceCompleted(taskId, date, completed = false, at = now)
        val occurrence = dao.getOccurrence(taskId, date)
        assertEquals(false, occurrence?.completed)
        assertNull(occurrence?.completedAt)
    }

    /**
     * Unticking a task.
     *
     * `setCompleted` used to share one parameter between `completedAt` and `updatedAt`, so
     * unticking passed null to both and the NOT NULL constraint on `updatedAt` took the app down
     * on the second tap of any checkbox. Cheap to get wrong, cheap to guard.
     */
    @Test
    fun aTaskCanBeUntickedWithoutViolatingTheUpdatedAtConstraint() = runTest {
        val id = db.taskDao().insert(
            TaskEntity(title = "Buy milk", createdAt = now, updatedAt = now),
        )
        val later = now.plusSeconds(60)

        db.taskDao().setCompleted(id, completed = true, completedAt = now, updatedAt = now)
        assertEquals(true, db.taskDao().getById(id)?.completed)

        db.taskDao().setCompleted(id, completed = false, completedAt = null, updatedAt = later)

        val task = db.taskDao().getById(id)
        assertEquals(false, task?.completed)
        assertNull("completedAt is cleared", task?.completedAt)
        assertEquals("updatedAt is always now", later, task?.updatedAt)
    }

    /**
     * A completed undated task is still a task.
     *
     * The backlog query used to hard-code `completed = 0`, so ticking something with no date made
     * it disappear with no way to see or undo it, whatever the user had chosen under "completed
     * tasks". Whether to show completed work is a preference, not a fact about the query.
     */
    @Test
    fun theBacklogKeepsCompletedTasks() = runTest {
        val id = db.taskDao().insert(
            TaskEntity(title = "No date", createdAt = now, updatedAt = now),
        )
        db.taskDao().setCompleted(id, completed = true, completedAt = now, updatedAt = now)

        val backlog = db.taskDao().observeBacklog().first()

        assertEquals(1, backlog.size)
        assertEquals(true, backlog.single().completed)
    }

}
