package dev.achyutem.cadence

import dev.achyutem.cadence.core.datastore.AccentColor
import dev.achyutem.cadence.core.designsystem.theme.palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    }

    @Test
    fun `an unreadable value falls back rather than throwing`() {
        assertEquals(AccentColor.BLUE, AccentColor.parse(null))
        assertEquals(AccentColor.BLUE, AccentColor.parse(""))
        assertEquals(AccentColor.BLUE, AccentColor.parse("CHARTREUSE"))
    }

    @Test
    fun `the two neutral accents differ where it matters`() {
        // White stays white in a dark scheme; mono inverts. If these ever converge in both
        // schemes, one of them has stopped being worth offering.
        val white = AccentColor.WHITE.palette
        val mono = AccentColor.MONO.palette
        assertNotEquals(white.primary(dark = false), mono.primary(dark = false))
    }
}
