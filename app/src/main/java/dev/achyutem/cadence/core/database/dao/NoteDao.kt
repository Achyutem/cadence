package dev.achyutem.cadence.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.achyutem.cadence.core.database.entity.NoteEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface NoteDao {

    /**
     * Pinned notes first, then most-recently-edited. The list query deliberately does not select
     * `content`: a note body can be thousands of characters and the list only ever shows
     * `preview`, so loading bodies would be reading megabytes to draw a screen.
     */
    @Query(
        """
        SELECT id, title, preview, pinned, archived, sortOrder, createdAt, updatedAt
        FROM notes
        WHERE archived = 0
        ORDER BY pinned DESC, sortOrder ASC, updatedAt DESC
        """
    )
    fun observeSummaries(): Flow<List<NoteSummary>>

    @Query(
        """
        SELECT id, title, preview, pinned, archived, sortOrder, createdAt, updatedAt
        FROM notes
        WHERE archived = 0 AND (title LIKE '%' || :query || '%' OR preview LIKE '%' || :query || '%')
        ORDER BY pinned DESC, updatedAt DESC
        """
    )
    fun searchSummaries(query: String): Flow<List<NoteSummary>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes ORDER BY id ASC")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes WHERE archived = 0")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Insert
    suspend fun insertAll(notes: List<NoteEntity>)

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE notes SET pinned = :pinned, updatedAt = :at WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, at: Instant)

    @Query("UPDATE notes SET archived = :archived, updatedAt = :at WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, at: Instant)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}

/** Projection for the note list — everything except the body. */
data class NoteSummary(
    val id: Long,
    val title: String,
    val preview: String,
    val pinned: Boolean,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)
