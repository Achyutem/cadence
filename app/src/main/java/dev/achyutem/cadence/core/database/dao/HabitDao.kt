package dev.achyutem.cadence.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import dev.achyutem.cadence.core.database.entity.HabitEntity
import dev.achyutem.cadence.core.database.entity.HabitEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface HabitDao {

    @Query("SELECT * FROM habits WHERE archived = 0 ORDER BY sortOrder ASC, id ASC")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY archived ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    fun observeById(id: Long): Flow<HabitEntity?>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun getById(id: Long): HabitEntity?

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Delete
    suspend fun delete(habit: HabitEntity)

    @Query("UPDATE habits SET archived = :archived, updatedAt = :at WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, at: Instant)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM habits")
    suspend fun maxSortOrder(): Int

    // --- Entries ---

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND date = :date")
    suspend fun getEntry(habitId: Long, date: LocalDate): HabitEntryEntity?

    @Query("SELECT * FROM habit_entries WHERE habitId = :habitId AND date = :date")
    fun observeEntry(habitId: Long, date: LocalDate): Flow<HabitEntryEntity?>

    @Query("SELECT * FROM habit_entries WHERE date = :date")
    fun observeEntriesOn(date: LocalDate): Flow<List<HabitEntryEntity>>

    /**
     * The heatmap and statistics query. Bounded by date so a five-year history costs one
     * indexed range scan, not a full table read.
     */
    @Query(
        """
        SELECT * FROM habit_entries
        WHERE habitId = :habitId AND date BETWEEN :start AND :end
        ORDER BY date ASC
        """
    )
    fun observeEntriesBetween(habitId: Long, start: LocalDate, end: LocalDate): Flow<List<HabitEntryEntity>>

    @Query("SELECT * FROM habit_entries WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    fun observeAllEntriesBetween(start: LocalDate, end: LocalDate): Flow<List<HabitEntryEntity>>

    @Query(
        """
        SELECT * FROM habit_entries
        WHERE habitId = :habitId AND date BETWEEN :start AND :end
        ORDER BY date ASC
        """
    )
    suspend fun getEntriesBetween(habitId: Long, start: LocalDate, end: LocalDate): List<HabitEntryEntity>

    @Upsert
    suspend fun upsertEntry(entry: HabitEntryEntity)

    @Query("DELETE FROM habit_entries WHERE habitId = :habitId AND date = :date")
    suspend fun deleteEntry(habitId: Long, date: LocalDate)

    // --- Backup. See the note in TaskDao. ---

    @Query("SELECT * FROM habits ORDER BY id ASC")
    suspend fun getAllForBackup(): List<HabitEntity>

    @Query("SELECT * FROM habit_entries ORDER BY id ASC")
    suspend fun getAllEntriesForBackup(): List<HabitEntryEntity>

    @Insert
    suspend fun insertAll(habits: List<HabitEntity>)

    @Insert
    suspend fun insertEntries(entries: List<HabitEntryEntity>)

    @Query("DELETE FROM habits")
    suspend fun deleteAllHabits()

    @Query("DELETE FROM habit_entries")
    suspend fun deleteAllEntries()
}
