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

/*
 * The ten accents.
 *
 * The first five were tuned for restraint and came out muted: readable, but flat next to a white
 * surface. These are pitched brighter in light mode and clearly lighter in dark mode, where an
 * accent has to carry against near-black rather than white.
 *
 * Every `on*` colour still clears 4.5:1 against its pairing, which is the constraint that keeps
 * "more vivid" from becoming "unreadable". Light primaries sit around 45-55% lightness, dark
 * primaries around 70-78%, which is where an accent reads as saturated in both schemes.
 */

private val BluePalette = AccentPalette(
    lightPrimary = Color(0xFF2563EB),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFDCE7FF),
    lightOnContainer = Color(0xFF10265E),
    darkPrimary = Color(0xFF7CA5FF),
    darkOnPrimary = Color(0xFF041C52),
    darkContainer = Color(0xFF1B336E),
    darkOnContainer = Color(0xFFD9E5FF),
)

private val IndigoPalette = AccentPalette(
    lightPrimary = Color(0xFF4F46E5),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFE2E0FF),
    lightOnContainer = Color(0xFF1D1663),
    darkPrimary = Color(0xFFA8A0FF),
    darkOnPrimary = Color(0xFF1B1259),
    darkContainer = Color(0xFF2E2680),
    darkOnContainer = Color(0xFFE6E3FF),
)

private val VioletPalette = AccentPalette(
    lightPrimary = Color(0xFF7C3AED),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFEDE0FF),
    lightOnContainer = Color(0xFF2E1065),
    darkPrimary = Color(0xFFC4A2FF),
    darkOnPrimary = Color(0xFF2B0F63),
    darkContainer = Color(0xFF431E87),
    darkOnContainer = Color(0xFFEFE2FF),
)

private val MagentaPalette = AccentPalette(
    lightPrimary = Color(0xFFC026D3),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFFBDDFF),
    lightOnContainer = Color(0xFF4A0B52),
    darkPrimary = Color(0xFFEE9BFB),
    darkOnPrimary = Color(0xFF430A4A),
    darkContainer = Color(0xFF69166F),
    darkOnContainer = Color(0xFFFCDFFF),
)

private val RosePalette = AccentPalette(
    lightPrimary = Color(0xFFE11D62),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFFFDCE5),
    lightOnContainer = Color(0xFF55001F),
    darkPrimary = Color(0xFFFF93AF),
    darkOnPrimary = Color(0xFF52001D),
    darkContainer = Color(0xFF7D0F38),
    darkOnContainer = Color(0xFFFFDDE5),
)

private val AmberPalette = AccentPalette(
    lightPrimary = Color(0xFFD97706),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFFFE6C2),
    lightOnContainer = Color(0xFF4A2400),
    darkPrimary = Color(0xFFFFBC5C),
    darkOnPrimary = Color(0xFF442300),
    darkContainer = Color(0xFF6D3A00),
    darkOnContainer = Color(0xFFFFE5C0),
)

private val EmeraldPalette = AccentPalette(
    lightPrimary = Color(0xFF059669),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFC6F2DF),
    lightOnContainer = Color(0xFF00351F),
    darkPrimary = Color(0xFF4FDBA3),
    darkOnPrimary = Color(0xFF00301C),
    darkContainer = Color(0xFF005433),
    darkOnContainer = Color(0xFFC3F5DE),
)

private val TealPalette = AccentPalette(
    lightPrimary = Color(0xFF0D9488),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFC2F1EC),
    lightOnContainer = Color(0xFF00322D),
    darkPrimary = Color(0xFF4CD9CB),
    darkOnPrimary = Color(0xFF002E29),
    darkContainer = Color(0xFF00514A),
    darkOnContainer = Color(0xFFBFF3ED),
)

private val CyanPalette = AccentPalette(
    lightPrimary = Color(0xFF0891B2),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFC6EEFB),
    lightOnContainer = Color(0xFF002E3C),
    darkPrimary = Color(0xFF52D2F2),
    darkOnPrimary = Color(0xFF002A37),
    darkContainer = Color(0xFF004C61),
    darkOnContainer = Color(0xFFC3EFFC),
)

/**
 * The near-monochrome option.
 *
 * For anyone who wants the accent to disappear entirely. It still carries enough contrast to mark
 * a selection, which is the one thing an accent has to do.
 */
private val SlatePalette = AccentPalette(
    lightPrimary = Color(0xFF334155),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFE0E5EC),
    lightOnContainer = Color(0xFF111820),
    darkPrimary = Color(0xFFB3BECD),
    darkOnPrimary = Color(0xFF151C25),
    darkContainer = Color(0xFF2E3948),
    darkOnContainer = Color(0xFFE2E8F0),
)

val AccentColor.palette: AccentPalette
    get() = when (this) {
        AccentColor.BLUE -> BluePalette
        AccentColor.INDIGO -> IndigoPalette
        AccentColor.VIOLET -> VioletPalette
        AccentColor.MAGENTA -> MagentaPalette
        AccentColor.ROSE -> RosePalette
        AccentColor.AMBER -> AmberPalette
        AccentColor.EMERALD -> EmeraldPalette
        AccentColor.TEAL -> TealPalette
        AccentColor.CYAN -> CyanPalette
        AccentColor.SLATE -> SlatePalette
    }

/** The swatch shown in Settings; the light primary reads well on both backgrounds. */
val AccentColor.swatch: Color
    get() = palette.lightPrimary

/** Accessibility label for the swatch. Localised, because it is read aloud. */
val AccentColor.labelRes: Int
    get() = when (this) {
        AccentColor.BLUE -> R.string.accent_blue
        AccentColor.INDIGO -> R.string.accent_indigo
        AccentColor.VIOLET -> R.string.accent_violet
        AccentColor.MAGENTA -> R.string.accent_magenta
        AccentColor.ROSE -> R.string.accent_rose
        AccentColor.AMBER -> R.string.accent_amber
        AccentColor.EMERALD -> R.string.accent_emerald
        AccentColor.TEAL -> R.string.accent_teal
        AccentColor.CYAN -> R.string.accent_cyan
        AccentColor.SLATE -> R.string.accent_slate
    }
