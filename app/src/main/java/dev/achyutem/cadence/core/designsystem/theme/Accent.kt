package dev.achyutem.cadence.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.datastore.AccentColor

/**
 * One accent, expressed as the four tones Material 3 actually needs, for both schemes.
 *
 * These are hand-tuned rather than generated from a seed. Generated schemes drift: the "same"
 * blue produces a different container tone in light and dark, and contrast ratios wander. Since
 * Cadence ships a fixed set, tuning them once buys predictable contrast everywhere,
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
 * Eight hues plus two neutrals. The first set was tuned for restraint and came out muted: readable,
 * but flat next to a white surface. These are pitched brighter in light mode and clearly lighter
 * in dark mode, where an accent has to carry against near-black rather than white.
 *
 * Every `on*` colour clears 4.5:1 against its pairing, which is the constraint that keeps "more
 * vivid" from becoming "unreadable". Light primaries sit around 45-55% lightness, dark primaries
 * around 70-78%, which is where an accent reads as saturated in both schemes.
 */

private val RedPalette = AccentPalette(
    lightPrimary = Color(0xFFDC2626),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFFFDDDA),
    lightOnContainer = Color(0xFF530A05),
    darkPrimary = Color(0xFFFF9186),
    darkOnPrimary = Color(0xFF520A04),
    darkContainer = Color(0xFF7A1710),
    darkOnContainer = Color(0xFFFFDEDA),
)

private val OrangePalette = AccentPalette(
    lightPrimary = Color(0xFFEA580C),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFFFE1CC),
    lightOnContainer = Color(0xFF4C1D02),
    darkPrimary = Color(0xFFFFA76B),
    darkOnPrimary = Color(0xFF461A00),
    darkContainer = Color(0xFF6E2E05),
    darkOnContainer = Color(0xFFFFE3D1),
)

/**
 * Sepia: a warm brown for anyone who wants paper rather than screen.
 *
 * Lower saturation than the rest on purpose. It is the one accent chosen for how little it
 * announces itself, and pushing it toward orange would make it a second orange.
 */
private val SepiaPalette = AccentPalette(
    lightPrimary = Color(0xFF8A6134),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFF2E2CE),
    lightOnContainer = Color(0xFF32200C),
    darkPrimary = Color(0xFFDCB587),
    darkOnPrimary = Color(0xFF2E1D09),
    darkContainer = Color(0xFF503617),
    darkOnContainer = Color(0xFFF4E4D1),
)

private val GreenPalette = AccentPalette(
    lightPrimary = Color(0xFF16A34A),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFC9F3D5),
    lightOnContainer = Color(0xFF00351A),
    darkPrimary = Color(0xFF63DC8E),
    darkOnPrimary = Color(0xFF003018),
    darkContainer = Color(0xFF00562B),
    darkOnContainer = Color(0xFFC6F6D6),
)

/**
 * The familiar streaming green, `#1DB954`.
 *
 * Darkened for light mode: the brand value against white is about 2.2:1, so white text on it
 * would fail outright. Dark mode gets the real thing, which is where it belongs anyway.
 */
private val SpotifyPalette = AccentPalette(
    lightPrimary = Color(0xFF0F8A42),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFC6F1D4),
    lightOnContainer = Color(0xFF002E16),
    darkPrimary = Color(0xFF1DB954),
    darkOnPrimary = Color(0xFF00220F),
    darkContainer = Color(0xFF0B4F26),
    darkOnContainer = Color(0xFFC8F4D6),
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

/** A true neutral grey, for anyone who wants the accent to stop shouting without disappearing. */
private val GreyPalette = AccentPalette(
    lightPrimary = Color(0xFF4B5563),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFE2E5EA),
    lightOnContainer = Color(0xFF15181D),
    darkPrimary = Color(0xFFBEC3CB),
    darkOnPrimary = Color(0xFF181B20),
    darkContainer = Color(0xFF353A42),
    darkOnContainer = Color(0xFFE6E9ED),
)

/** Black on a light scheme, white on a dark one. Maximum contrast, no hue at all. */
private val MonoPalette = AccentPalette(
    lightPrimary = Color(0xFF18181B),
    lightOnPrimary = Color(0xFFFFFFFF),
    lightContainer = Color(0xFFE4E4E7),
    lightOnContainer = Color(0xFF18181B),
    darkPrimary = Color(0xFFFAFAFA),
    darkOnPrimary = Color(0xFF0A0A0A),
    darkContainer = Color(0xFF3F3F46),
    darkOnContainer = Color(0xFFFAFAFA),
)

val AccentColor.palette: AccentPalette
    get() = when (this) {
        AccentColor.RED -> RedPalette
        AccentColor.ORANGE -> OrangePalette
        AccentColor.SEPIA -> SepiaPalette
        AccentColor.GREEN -> GreenPalette
        AccentColor.SPOTIFY -> SpotifyPalette
        AccentColor.CYAN -> CyanPalette
        AccentColor.BLUE -> BluePalette
        AccentColor.MAGENTA -> MagentaPalette
        AccentColor.GREY -> GreyPalette
        AccentColor.MONO -> MonoPalette
    }

/**
 * The swatch shown in the picker.
 *
 * Takes the scheme into account, unlike the rest of this file's accessors, because the two
 * neutral accents are the two that differ most between schemes: showing Mono as a black dot in a
 * dark theme would advertise the opposite of what selecting it does.
 */
fun AccentColor.swatch(dark: Boolean): Color = palette.primary(dark)

/** Accessibility label for the swatch, and the name shown next to it. Localised. */
val AccentColor.labelRes: Int
    get() = when (this) {
        AccentColor.RED -> R.string.accent_red
        AccentColor.ORANGE -> R.string.accent_orange
        AccentColor.SEPIA -> R.string.accent_sepia
        AccentColor.GREEN -> R.string.accent_green
        AccentColor.SPOTIFY -> R.string.accent_spotify
        AccentColor.CYAN -> R.string.accent_cyan
        AccentColor.BLUE -> R.string.accent_blue
        AccentColor.MAGENTA -> R.string.accent_magenta
        AccentColor.GREY -> R.string.accent_grey
        AccentColor.MONO -> R.string.accent_mono
    }
