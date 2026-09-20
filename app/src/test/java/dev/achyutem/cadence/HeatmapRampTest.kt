package dev.achyutem.cadence

import dev.achyutem.cadence.core.datastore.AccentColor
import dev.achyutem.cadence.core.designsystem.theme.cadenceColors
import dev.achyutem.cadence.core.designsystem.theme.palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The heatmap ramp is derived rather than hand-authored, so it needs guarding: five levels, all
 * distinct, and monotonically increasing in intensity. A ramp with two equal steps would render
 * "did it once" and "did it four times" as the same square.
 */
class HeatmapRampTest {

    @Test
    fun `ramp has five levels for every accent in both schemes`() {
        AccentColor.entries.forEach { accent ->
            listOf(true, false).forEach { dark ->
                val levels = cadenceColors(accent.palette, dark).heatmapLevels
                assertEquals("$accent dark=$dark", 5, levels.size)
            }
        }
    }

    @Test
    fun `ramp levels are all distinct`() {
        AccentColor.entries.forEach { accent ->
            listOf(true, false).forEach { dark ->
                val levels = cadenceColors(accent.palette, dark).heatmapLevels
                assertEquals(
                    "$accent dark=$dark has duplicate levels",
                    levels.size,
                    levels.toSet().size,
                )
            }
        }
    }

    @Test
    fun `ramp intensity increases monotonically away from the empty cell`() {
        AccentColor.entries.forEach { accent ->
            listOf(true, false).forEach { dark ->
                val levels = cadenceColors(accent.palette, dark).heatmapLevels
                val empty = levels.first()
                val distances = levels.drop(1).map { it.distanceFrom(empty) }
                distances.zipWithNext { a, b ->
                    assertTrue(
                        "$accent dark=$dark: ramp is not monotonic ($a then $b)",
                        b > a,
                    )
                }
            }
        }
    }

    private fun androidx.compose.ui.graphics.Color.distanceFrom(
        other: androidx.compose.ui.graphics.Color,
    ): Float {
        val dr = red - other.red
        val dg = green - other.green
        val db = blue - other.blue
        return dr * dr + dg * dg + db * db
    }
}
