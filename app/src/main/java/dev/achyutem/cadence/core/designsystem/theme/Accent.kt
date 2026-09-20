package dev.achyutem.cadence.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.datastore.AccentColor

/**
 * One accent, expressed as the four tones Material 3 actually needs, for both schemes.
 *
 * These are hand-tuned rather than generated from a seed. Generated schemes drift: the "same"
 * blue produces a different container tone in light and dark, and contrast ratios wander. Since
 * Cadence ships five fixed accents, tuning them once buys predictable contrast everywhere,
 * including in Glance widgets, which cannot run Material's colour generation at all.
 *
 * Every `on*` colour below clears 4.5:1 against its pairing.
 */
data class AccentPalette(
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
) {
    fun primary(dark: Boolean): Color = if (dark) darkPrimary else lightPrimary
    fun onPrimary(dark: Boolean): Color = if (dark) darkOnPrimary else lightOnPrimary
    fun container(dark: Boolean): Color = if (dark) darkContainer else lightContainer
    fun onContainer(dark: Boolean): Color = if (dark) darkOnContainer else lightOnContainer
}

private val BluePalette = AccentPalette(
    lightPrimary = Color(0xFF3B5BF0),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFE0E6FF),
    lightOnContainer = Color(0xFF0D1A66),
    darkPrimary = Color(0xFF9DB2FF),
    darkOnPrimary = Color(0xFF0F1F63),
    darkContainer = Color(0xFF1F2A66),
    darkOnContainer = Color(0xFFDCE3FF),
)

private val PurplePalette = AccentPalette(
    lightPrimary = Color(0xFF6D45D9),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFEBE1FF),
    lightOnContainer = Color(0xFF260F5E),
    darkPrimary = Color(0xFFC2AAFF),
    darkOnPrimary = Color(0xFF2A1064),
    darkContainer = Color(0xFF3A1F79),
    darkOnContainer = Color(0xFFEBDFFF),
)

private val GreenPalette = AccentPalette(
    lightPrimary = Color(0xFF19794E),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFCFF0DF),
    lightOnContainer = Color(0xFF04301D),
    darkPrimary = Color(0xFF6FD6A1),
    darkOnPrimary = Color(0xFF00351F),
    darkContainer = Color(0xFF0F4C30),
    darkOnContainer = Color(0xFFC8F3DC),
)

private val OrangePalette = AccentPalette(
    lightPrimary = Color(0xFFB65A0C),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFFFE2C7),
    lightOnContainer = Color(0xFF441E00),
    darkPrimary = Color(0xFFFFB570),
    darkOnPrimary = Color(0xFF4A2300),
    darkContainer = Color(0xFF68360A),
    darkOnContainer = Color(0xFFFFE1C6),
)

private val PinkPalette = AccentPalette(
    lightPrimary = Color(0xFFB92F68),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFFFDEEA),
    lightOnContainer = Color(0xFF450022),
    darkPrimary = Color(0xFFFF9FC4),
    darkOnPrimary = Color(0xFF530026),
    darkContainer = Color(0xFF74113E),
    darkOnContainer = Color(0xFFFFDCE9),
)

val AccentColor.palette: AccentPalette
    get() = when (this) {
        AccentColor.BLUE -> BluePalette
        AccentColor.PURPLE -> PurplePalette
        AccentColor.GREEN -> GreenPalette
        AccentColor.ORANGE -> OrangePalette
        AccentColor.PINK -> PinkPalette
    }

/** The swatch shown in Settings; the light primary reads well on both backgrounds. */
val AccentColor.swatch: Color
    get() = palette.lightPrimary

/** Accessibility label for the swatch. Localised, because it is read aloud. */
val AccentColor.labelRes: Int
    get() = when (this) {
        AccentColor.BLUE -> R.string.accent_blue
        AccentColor.PURPLE -> R.string.accent_purple
        AccentColor.GREEN -> R.string.accent_green
        AccentColor.ORANGE -> R.string.accent_orange
        AccentColor.PINK -> R.string.accent_pink
    }
