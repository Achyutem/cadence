package dev.achyutem.cadence.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.achyutem.cadence.core.database.entity.DailyCheckInEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface CheckInDao {

    @Query("SELECT * FROM daily_check_ins WHERE date = :date")
    fun observeOn(date: LocalDate): Flow<DailyCheckInEntity?>

    @Query("SELECT * FROM daily_check_ins WHERE date = :date")
    suspend fun getOn(date: LocalDate): DailyCheckInEntity?

    @Query("SELECT * FROM daily_check_ins WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<DailyCheckInEntity>>

    @Upsert
    suspend fun upsert(checkIn: DailyCheckInEntity)

    @Query("DELETE FROM daily_check_ins WHERE date = :date")
    suspend fun deleteOn(date: LocalDate)

    // --- Backup ---

    @Query("SELECT * FROM daily_check_ins ORDER BY id ASC")
    suspend fun getAllForBackup(): List<DailyCheckInEntity>

    @androidx.room.Insert
    suspend fun insertAll(checkIns: List<DailyCheckInEntity>)

    @Query("DELETE FROM daily_check_ins")
    suspend fun deleteAll()
}
