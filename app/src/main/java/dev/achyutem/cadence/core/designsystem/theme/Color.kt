package dev.achyutem.cadence.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The neutral ramp.
 *
 * True neutral greys, not the blue-cast ones this app started with. A tinted neutral fights every
 * accent it sits next to; a pure grey lets an accent be the only chromatic thing on screen, which
 * is the entire point of using accent sparingly.
 *
 * Light mode is white-on-white separated by borders. Dark mode bottoms out at near-black rather
 * than charcoal, on an OLED phone that is a genuinely darker, calmer surface, and it makes the
 * hairline borders the thing that defines structure in both schemes.
 */
private object Neutral {
    // Light
    val lightBackground = Color(0xFFFFFFFF)
    val lightSurface = Color(0xFFFFFFFF)
    val lightSurfaceLow = Color(0xFFFAFAFA)
    val lightSurfaceContainer = Color(0xFFF5F5F5)
    val lightSurfaceHigh = Color(0xFFEFEFEF)
    val lightOnSurface = Color(0xFF171717)
    val lightOnSurfaceVariant = Color(0xFF737373)
    val lightOutline = Color(0xFFD4D4D4)
    val lightOutlineVariant = Color(0xFFEAEAEA)

    // Dark
    val darkBackground = Color(0xFF0A0A0A)
    val darkSurface = Color(0xFF0A0A0A)
    val darkSurfaceLow = Color(0xFF111111)
    val darkSurfaceContainer = Color(0xFF171717)
    val darkSurfaceHigh = Color(0xFF1F1F1F)
    val darkOnSurface = Color(0xFFEDEDED)
    val darkOnSurfaceVariant = Color(0xFF8F8F8F)
    val darkOutline = Color(0xFF2E2E2E)
    val darkOutlineVariant = Color(0xFF1F1F1F)
}

private object Semantic {
    val lightSuccess = Color(0xFF17803D)
    val darkSuccess = Color(0xFF4ADE80)
    val lightWarning = Color(0xFFA16207)
    val darkWarning = Color(0xFFFACC15)
    val lightDanger = Color(0xFFB91C1C)
    val darkDanger = Color(0xFFF87171)

    // Task priority. Low is near-neutral on purpose: most tasks have no priority, and a list
    // that looks like a traffic light is a list nobody can scan.
    val lightPriorityLow = Color(0xFF8F8F8F)
    val lightPriorityMedium = Color(0xFFB45309)
    val lightPriorityHigh = Color(0xFFDC2626)
    val darkPriorityLow = Color(0xFF8F8F8F)
    val darkPriorityMedium = Color(0xFFFBBF24)
    val darkPriorityHigh = Color(0xFFF87171)
}

/**
 * Colours Material 3 has no slot for. Read through [LocalCadenceColors] as
 * `CadenceTheme.colors.…`.
 */
@Immutable
data class CadenceColors(
    val success: Color,
    val warning: Color,
    val danger: Color,
    val priorityLow: Color,
    val priorityMedium: Color,
    val priorityHigh: Color,
    /**
     * Five steps, index 0 = "no activity". Derived from the accent by blending toward the
     * surface, so the heatmap re-tints instantly when the accent changes and stays legible in
     * both schemes without a second hand-tuned table.
     */
    val heatmapLevels: List<Color>,
    /** Hairline rules between dense rows and along the calendar timeline. */
    val divider: Color,
    /** Default border for cards, inputs and controls. */
    val border: Color,
    /** Border for hovered/focused/selected surfaces, one step up in contrast. */
    val borderStrong: Color,
    /** The floating dock's own surface. */
    val dockSurface: Color,
    val dockOutline: Color,
    /** Track behind any progress bar or ring. */
    val progressTrack: Color,
    /**
     * The lifted chip inside a segmented control.
     *
     * This cannot be a Material slot: the selected chip must read as raised above its track in
     * both schemes, and no single `surfaceContainer*` role does that. In light mode "raised"
     * means whiter than the track; in dark mode it means lighter than the track, opposite
     * directions on the tonal ramp.
     */
    val segmentSelected: Color,
    /** Fill for an inert, non-accent chip or tag. */
    val chipSurface: Color,
)

fun cadenceColors(accent: AccentPalette, dark: Boolean): CadenceColors {
    val base = if (dark) Neutral.darkSurfaceLow else Neutral.lightSurfaceLow
    return CadenceColors(
        success = if (dark) Semantic.darkSuccess else Semantic.lightSuccess,
        warning = if (dark) Semantic.darkWarning else Semantic.lightWarning,
        danger = if (dark) Semantic.darkDanger else Semantic.lightDanger,
        priorityLow = if (dark) Semantic.darkPriorityLow else Semantic.lightPriorityLow,
        priorityMedium = if (dark) Semantic.darkPriorityMedium else Semantic.lightPriorityMedium,
        priorityHigh = if (dark) Semantic.darkPriorityHigh else Semantic.lightPriorityHigh,
        heatmapLevels = heatmapRamp(accent.heatmapSeed(dark), base, dark),
        divider = if (dark) Neutral.darkOutlineVariant else Neutral.lightOutlineVariant,
        border = if (dark) Neutral.darkOutline else Neutral.lightOutlineVariant,
        borderStrong = if (dark) Color(0xFF3D3D3D) else Neutral.lightOutline,
        dockSurface = if (dark) Color(0xFF141414) else Color(0xFFFFFFFF),
        dockOutline = if (dark) Neutral.darkOutline else Neutral.lightOutline,
        progressTrack = if (dark) Neutral.darkSurfaceHigh else Neutral.lightSurfaceHigh,
        segmentSelected = if (dark) Color(0xFF2A2A2A) else Color(0xFFFFFFFF),
        chipSurface = if (dark) Neutral.darkSurfaceContainer else Neutral.lightSurfaceContainer,
    )
}

/**
 * The GitHub-style intensity ramp.
 *
 * Level 0 is an empty cell, visible enough to read as a grid, quiet enough to disappear. Levels
 * 1..4 walk from a wash of the accent to the accent itself. Dark mode uses steeper blend
 * fractions, because identical fractions read far darker against a near-black background.
 */
private fun heatmapRamp(primary: Color, base: Color, dark: Boolean): List<Color> {
    val empty = if (dark) Color(0xFF1A1A1A) else Color(0xFFEDEDED)
    val fractions = if (dark) listOf(0.32f, 0.54f, 0.77f, 1f) else listOf(0.20f, 0.42f, 0.70f, 1f)
    return buildList {
        add(empty)
        fractions.forEach { f -> add(lerp(base, primary, f)) }
    }
}

fun cadenceLightScheme(accent: AccentPalette): ColorScheme = lightColorScheme(
    primary = accent.lightPrimary,
    onPrimary = accent.lightOnPrimary,
    primaryContainer = accent.lightContainer,
    onPrimaryContainer = accent.lightOnContainer,
    secondary = Neutral.lightOnSurfaceVariant,
    onSecondary = Color.White,
    secondaryContainer = Neutral.lightSurfaceContainer,
    onSecondaryContainer = Neutral.lightOnSurface,
    tertiary = accent.lightPrimary,
    onTertiary = accent.lightOnPrimary,
    tertiaryContainer = accent.lightContainer,
    onTertiaryContainer = accent.lightOnContainer,
    background = Neutral.lightBackground,
    onBackground = Neutral.lightOnSurface,
    surface = Neutral.lightSurface,
    onSurface = Neutral.lightOnSurface,
    surfaceVariant = Neutral.lightSurfaceContainer,
    onSurfaceVariant = Neutral.lightOnSurfaceVariant,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Neutral.lightSurfaceLow,
    surfaceContainer = Neutral.lightSurfaceContainer,
    surfaceContainerHigh = Neutral.lightSurfaceHigh,
    surfaceContainerHighest = Neutral.lightSurfaceHigh,
    outline = Neutral.lightOutline,
    outlineVariant = Neutral.lightOutlineVariant,
    error = Semantic.lightDanger,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    scrim = Color(0xFF000000),
)

fun cadenceDarkScheme(accent: AccentPalette): ColorScheme = darkColorScheme(
    primary = accent.darkPrimary,
    onPrimary = accent.darkOnPrimary,
    primaryContainer = accent.darkContainer,
    onPrimaryContainer = accent.darkOnContainer,
    secondary = Neutral.darkOnSurfaceVariant,
    onSecondary = Neutral.darkBackground,
    secondaryContainer = Neutral.darkSurfaceContainer,
    onSecondaryContainer = Neutral.darkOnSurface,
    tertiary = accent.darkPrimary,
    onTertiary = accent.darkOnPrimary,
    tertiaryContainer = accent.darkContainer,
    onTertiaryContainer = accent.darkOnContainer,
    background = Neutral.darkBackground,
    onBackground = Neutral.darkOnSurface,
    surface = Neutral.darkSurface,
    onSurface = Neutral.darkOnSurface,
    surfaceVariant = Neutral.darkSurfaceContainer,
    onSurfaceVariant = Neutral.darkOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Neutral.darkSurfaceLow,
    surfaceContainer = Neutral.darkSurfaceContainer,
    surfaceContainerHigh = Neutral.darkSurfaceHigh,
    surfaceContainerHighest = Color(0xFF262626),
    outline = Neutral.darkOutline,
    outlineVariant = Neutral.darkOutlineVariant,
    error = Semantic.darkDanger,
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    scrim = Color(0xFF000000),
)
