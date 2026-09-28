package dev.achyutem.cadence

import dev.achyutem.cadence.core.datastore.AccentColor
import dev.achyutem.cadence.core.designsystem.theme.palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading a stored accent.
 *
 * The palette was reworked and several accents were renamed or merged. Falling back to blue for
 * all of them would silently reset the one visual choice most people make, so the names that went
 * away map to their nearest survivor. This is the test that keeps that mapping honest.
 */
class AccentMigrationTest {

    @Test
    fun `current names round trip`() {
        AccentColor.entries.forEach { accent ->
            assertEquals(accent, AccentColor.parse(accent.name))
        }
    }

    @Test
    fun `retired names land on their nearest survivor`() {
        assertEquals(AccentColor.BLUE, AccentColor.parse("INDIGO"))
        assertEquals(AccentColor.BLUE, AccentColor.parse("VIOLET"))
        assertEquals(AccentColor.RED, AccentColor.parse("ROSE"))
        assertEquals(AccentColor.ORANGE, AccentColor.parse("AMBER"))
        assertEquals(AccentColor.GREEN, AccentColor.parse("EMERALD"))
        assertEquals(AccentColor.GREEN, AccentColor.parse("TEAL"))
        assertEquals(AccentColor.GREY, AccentColor.parse("SLATE"))
        assertEquals(AccentColor.MONO, AccentColor.parse("WHITE"))
        assertEquals(AccentColor.JADE, AccentColor.parse("SPOTIFY"))
    }

    @Test
    fun `an unreadable value falls back rather than throwing`() {
        assertEquals(AccentColor.BLUE, AccentColor.parse(null))
        assertEquals(AccentColor.BLUE, AccentColor.parse(""))
        assertEquals(AccentColor.BLUE, AccentColor.parse("CHARTREUSE"))
    }

    @Test
    fun `a retired accent never lands on another retired accent`() {
        // The mapping has to point at something that still exists, or it is just a slower way of
        // falling back to blue.
        listOf(
            "INDIGO", "VIOLET", "ROSE", "AMBER", "EMERALD", "TEAL", "SLATE", "WHITE", "SPOTIFY",
        ).forEach {
            assertTrue("$it maps to a live accent", AccentColor.parse(it) in AccentColor.entries)
        }
    }

    @Test
    fun `the monochrome accent inverts between schemes`() {
        val mono = AccentColor.MONO.palette
        assertNotEquals(mono.primary(dark = false), mono.primary(dark = true))
    }

}
