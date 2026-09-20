package dev.achyutem.cadence.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * A note: a heading and a body.
 *
 * The body is stored as **raw Markdown text**, not as a parsed tree or rich-text spans. That is
 * the decision everything else here follows from:
 *
 *  - It is trivially exportable and diffable, a note survives leaving this app intact.
 *  - It cannot become corrupt in a way that loses the user's words. A parser bug shows wrong
 *    formatting; a broken span model loses text.
 *  - The renderer can improve later without a migration, because the source of truth is what the
 *    user typed.
 *
 * [preview] is the one denormalised field in the schema, and it is not derived *data*; it is a
 * truncated copy of the body with formatting stripped, maintained on write. A note list must
 * render a hundred previews per frame, and running a Markdown parser over full bodies to do it
 * would make scrolling the one slow thing in the app. It is a cache of presentation, never a
 * source of truth, and it is rebuilt from [content] whenever the note is saved.
 */
@Entity(
    tableName = "notes",
    indices = [
        Index("pinned"),
        Index("updatedAt"),
        Index("archived"),
    ],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String = "",
    /** First ~200 characters of [content] with Markdown syntax stripped. See the class note. */
    val preview: String = "",
    val pinned: Boolean = false,
    val archived: Boolean = false,
    /** Manual ordering within the pinned group. Unpinned notes sort by [updatedAt]. */
    val sortOrder: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
)
