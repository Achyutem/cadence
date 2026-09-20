package dev.achyutem.cadence.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.achyutem.cadence.core.database.converter.CadenceTypeConverters
import dev.achyutem.cadence.core.database.dao.CheckInDao
import dev.achyutem.cadence.core.database.dao.HabitDao
import dev.achyutem.cadence.core.database.dao.NoteDao
import dev.achyutem.cadence.core.database.dao.RecurrenceDao
import dev.achyutem.cadence.core.database.dao.TagDao
import dev.achyutem.cadence.core.database.dao.TaskDao
import dev.achyutem.cadence.core.database.entity.DailyCheckInEntity
import dev.achyutem.cadence.core.database.entity.HabitEntity
import dev.achyutem.cadence.core.database.entity.HabitEntryEntity
import dev.achyutem.cadence.core.database.entity.NoteEntity
import dev.achyutem.cadence.core.database.entity.RecurrenceRuleEntity
import dev.achyutem.cadence.core.database.entity.ReminderEntity
import dev.achyutem.cadence.core.database.entity.TagEntity
import dev.achyutem.cadence.core.database.entity.TaskEntity
import dev.achyutem.cadence.core.database.entity.TaskOccurrenceEntity
import dev.achyutem.cadence.core.database.entity.TaskTagCrossRef

/**
 * The source of truth for everything in Cadence.
 *
 * Schema versions are exported to `app/schemas/` and committed. That is what makes migrations
 * testable: [dev.achyutem.cadence.MigrationTest] can open version N and migrate forward without
 * ever needing a device with old data on it.
 *
 * Migration policy: never destructive. `fallbackToDestructiveMigration` is deliberately *not*
 * used — this database holds years of a person's history and there is no cloud copy to restore
 * from. A missing migration should fail loudly in development, not silently wipe the user.
 */
@Database(
    entities = [
        TaskEntity::class,
        TaskOccurrenceEntity::class,
        HabitEntity::class,
        HabitEntryEntity::class,
        DailyCheckInEntity::class,
        NoteEntity::class,
        RecurrenceRuleEntity::class,
        ReminderEntity::class,
        TagEntity::class,
        TaskTagCrossRef::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(CadenceTypeConverters::class)
abstract class CadenceDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao
    abstract fun checkInDao(): CheckInDao
    abstract fun recurrenceDao(): RecurrenceDao
    abstract fun tagDao(): TagDao
    abstract fun noteDao(): NoteDao

    companion object {
        const val NAME = "cadence.db"

        /**
         * v1 → v2: added the `notes` table.
         *
         * Written by hand rather than generated, and it must match the schema Room expects
         * exactly — column order, types, NOT NULL and DEFAULT all included — or Room's identity
         * check fails at open time on an upgraded install while passing on a fresh one. That is
         * precisely the failure mode `MigrationTest` exists to catch before a user does.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SupportSQLiteDatabase) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notes` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `preview` TEXT NOT NULL,
                        `pinned` INTEGER NOT NULL,
                        `archived` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_pinned` ON `notes` (`pinned`)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_updatedAt` ON `notes` (`updatedAt`)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_archived` ON `notes` (`archived`)")
            }
        }

        /** Every migration, in order. Append here; never reorder or edit a shipped one. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun build(context: Context): CadenceDatabase =
            Room.databaseBuilder(context.applicationContext, CadenceDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .addCallback(ForeignKeysCallback)
                .build()
    }
}

/**
 * Room disables foreign key enforcement unless asked. Cadence relies on `ON DELETE CASCADE` for
 * subtasks and habit entries, so it has to be on for every connection.
 */
private object ForeignKeysCallback : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON")
    }
}
