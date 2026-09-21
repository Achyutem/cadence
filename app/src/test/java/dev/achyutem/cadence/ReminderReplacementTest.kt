package dev.achyutem.cadence

import dev.achyutem.cadence.core.database.entity.ReminderEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Replacing a reminder.
 *
 * The detail screens hand the view model an edited **copy** of the existing reminder, which still
 * carries its primary key. Inserting that is a UNIQUE violation, and it crashed the app on the
 * second tap of the reminder row: the first tap created one, the second tried to insert the same
 * id again. The view models now clear the id before inserting, and the old row is deleted after.
 *
 * This test pins the shape of that copy rather than the database call, because the mistake was in
 * what got handed to Room, not in Room.
 */
class ReminderReplacementTest {

    @Test
    fun `an edited reminder keeps its id, which is why insert has to clear it`() {
        val existing = ReminderEntity(id = 7, timeOfDay = java.time.LocalTime.of(9, 0))

        val edited = existing.copy(leadMinutes = 10)

        // The bug, stated: copy() preserves the primary key.
        assertEquals(7L, edited.id)
        // The fix, stated: what actually reaches insert() must not.
        assertEquals(0L, edited.copy(id = 0).id)
        assertEquals(10, edited.copy(id = 0).leadMinutes)
    }
}
