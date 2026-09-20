package dev.achyutem.cadence.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.achyutem.cadence.core.database.entity.TagEntity
import dev.achyutem.cadence.core.database.entity.TaskTagCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun getByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        """
        SELECT tags.* FROM tags
        INNER JOIN task_tags ON tags.id = task_tags.tagId
        WHERE task_tags.taskId = :taskId
        ORDER BY tags.name ASC
        """
    )
    fun observeTagsForTask(taskId: Long): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(crossRef: TaskTagCrossRef)

    @Query("DELETE FROM task_tags WHERE taskId = :taskId AND tagId = :tagId")
    suspend fun unlink(taskId: Long, tagId: Long)

    // --- Backup ---

    @Query("SELECT * FROM tags ORDER BY id ASC")
    suspend fun getAllForBackup(): List<TagEntity>

    @Query("SELECT * FROM task_tags")
    suspend fun getAllCrossRefsForBackup(): List<TaskTagCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRefs(refs: List<TaskTagCrossRef>)

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    @Query("DELETE FROM task_tags")
    suspend fun deleteAllCrossRefs()
}
