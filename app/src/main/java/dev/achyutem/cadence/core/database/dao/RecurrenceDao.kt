package dev.achyutem.cadence.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.achyutem.cadence.core.database.entity.RecurrenceRuleEntity
import dev.achyutem.cadence.core.database.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurrenceDao {

    @Query("SELECT * FROM recurrence_rules WHERE id = :id")
    suspend fun getRule(id: Long): RecurrenceRuleEntity?

    @Query("SELECT * FROM recurrence_rules")
    fun observeAllRules(): Flow<List<RecurrenceRuleEntity>>

    @Insert
    suspend fun insertRule(rule: RecurrenceRuleEntity): Long

    @Update
    suspend fun updateRule(rule: RecurrenceRuleEntity)

    @Delete
    suspend fun deleteRule(rule: RecurrenceRuleEntity)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getReminder(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun getEnabledReminders(): List<ReminderEntity>

    @Insert
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    // --- Backup ---

    @Query("SELECT * FROM recurrence_rules ORDER BY id ASC")
    suspend fun getAllRulesForBackup(): List<RecurrenceRuleEntity>

    @Query("SELECT * FROM reminders ORDER BY id ASC")
    suspend fun getAllRemindersForBackup(): List<ReminderEntity>

    @Insert
    suspend fun insertRules(rules: List<RecurrenceRuleEntity>)

    @Insert
    suspend fun insertReminders(reminders: List<ReminderEntity>)

    @Query("DELETE FROM recurrence_rules")
    suspend fun deleteAllRules()

    @Query("DELETE FROM reminders")
    suspend fun deleteAllReminders()
}
