package dev.achyutem.cadence

import dev.achyutem.cadence.core.datastore.AccentColor
import dev.achyutem.cadence.core.datastore.CompletedTaskBehavior
import dev.achyutem.cadence.core.datastore.ThemeMode
import dev.achyutem.cadence.core.datastore.UserPreferences
import dev.achyutem.cadence.core.designsystem.theme.palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class SettingsDefaultsTest {

    @Test
    fun `defaults are the calm, private ones`() {
        val defaults = UserPreferences.Default
        assertEquals(ThemeMode.SYSTEM, defaults.themeMode)
        assertEquals(AccentColor.BLUE, defaults.accentColor)
        assertEquals(DayOfWeek.MONDAY, defaults.weekStartsOn)
        assertEquals(CompletedTaskBehavior.MOVE_TO_BOTTOM, defaults.completedTaskBehavior)
        // Material You off by default: Cadence has its own identity.
        assertEquals(false, defaults.useDynamicColor)
    }

    @Test
    fun `every accent defines distinct light and dark primaries`() {
        AccentColor.entries.forEach { accent ->
            val palette = accent.palette
            assertNotEquals(
                "Accent $accent has the same primary in both schemes",
                palette.lightPrimary,
                palette.darkPrimary,
            )
        }
    }

    @Test
    fun `accents are distinguishable from one another`() {
        val primaries = AccentColor.entries.map { it.palette.lightPrimary }
        assertEquals(
            "Two accents share a primary colour",
            AccentColor.entries.size,
            primaries.toSet().size,
        )
    }
}
