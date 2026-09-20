package dev.achyutem.cadence.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.achyutem.cadence.core.database.entity.BreathingSessionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface BreathingDao {

    @Query("SELECT * FROM breathing_sessions WHERE date BETWEEN :start AND :end ORDER BY createdAt DESC")
    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<BreathingSessionEntity>>

    @Query("SELECT * FROM breathing_sessions ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<BreathingSessionEntity>>

    /** Personal best, for the static-apnea card. Null until the first hold is recorded. */
    @Query("SELECT MAX(longestHoldSeconds) FROM breathing_sessions")
    fun observeLongestHold(): Flow<Int?>

    @Query("SELECT COUNT(*) FROM breathing_sessions WHERE date = :date")
    fun observeCountOn(date: LocalDate): Flow<Int>

    @Insert
    suspend fun insert(session: BreathingSessionEntity): Long

    @Query("SELECT * FROM breathing_sessions ORDER BY id ASC")
    suspend fun getAllForBackup(): List<BreathingSessionEntity>

    @Insert
    suspend fun insertAll(sessions: List<BreathingSessionEntity>)

    @Query("DELETE FROM breathing_sessions")
    suspend fun deleteAll()
}
