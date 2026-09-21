package dev.achyutem.cadence.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.LocalContext
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider
import dev.achyutem.cadence.core.datastore.AccentColor
import dev.achyutem.cadence.core.datastore.ThemeMode
import dev.achyutem.cadence.core.datastore.UserPreferences
import dev.achyutem.cadence.core.designsystem.theme.palette

/**
 * The widget colour bridge.
 *
 * Glance cannot use `MaterialTheme`, `CompositionLocal`-based design tokens, or Material's colour
 * generation, a widget renders into a `RemoteViews` tree in the launcher's process. So the app's
 * palette has to be reachable as plain values.
 *
 * This is exactly why the accents in `designsystem/theme/Accent.kt` are **hand-tuned constants
 * rather than generated from a seed**: the same five accents resolve identically here and in the
 * app, so a widget and the screen behind it can never drift apart.
 *
 * Light and dark are supplied as a pair to Glance's `ColorProvider`, which picks per the
 * launcher's current configuration; a widget must follow the system theme even when the app is
 * pinned to Light or Dark, because it lives on someone else's surface. The stored [ThemeMode] is
 * honoured only when it is an explicit override.
 */
data class WidgetColors(
    val background: ColorProvider,
    val surface: ColorProvider,
    val onSurface: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    val accent: ColorProvider,
    val onAccent: ColorProvider,
    val accentContainer: ColorProvider,
    val border: ColorProvider,
    val progressTrack: ColorProvider,
    /** Five heatmap steps, index 0 = empty. */
    val heatmap: List<ColorProvider>,
)

private object WidgetNeutral {
    val lightBackground = Color(0xFFFFFFFF)
    val lightSurface = Color(0xFFFAFAFA)
    val lightOnSurface = Color(0xFF171717)
    val lightOnSurfaceVariant = Color(0xFF737373)
    val lightBorder = Color(0xFFEAEAEA)
    val lightTrack = Color(0xFFEFEFEF)
    val lightHeatmapEmpty = Color(0xFFEDEDED)

    val darkBackground = Color(0xFF141414)
    val darkSurface = Color(0xFF1C1C1C)
    val darkOnSurface = Color(0xFFEDEDED)
    val darkOnSurfaceVariant = Color(0xFF8F8F8F)
    val darkBorder = Color(0xFF2E2E2E)
    val darkTrack = Color(0xFF2A2A2A)
    val darkHeatmapEmpty = Color(0xFF1F1F1F)
}

fun widgetColors(accent: AccentColor, themeMode: ThemeMode): WidgetColors {
    val palette = accent.palette

    // When the user has pinned the app to Light or Dark, both sides of each pair collapse to that
    // scheme so the widget matches the app. On SYSTEM, the pair is handed to Glance intact and
    // the launcher decides.
    fun pair(light: Color, dark: Color): ColorProvider = when (themeMode) {
        ThemeMode.LIGHT -> ColorProvider(light, light)
        ThemeMode.DARK -> ColorProvider(dark, dark)
        ThemeMode.SYSTEM -> ColorProvider(light, dark)
    }

    fun ramp(empty: Color, base: Color, primary: Color, fractions: List<Float>): List<Color> =
        buildList {
            add(empty)
            fractions.forEach { f -> add(androidx.compose.ui.graphics.lerp(base, primary, f)) }
        }

    val lightRamp = ramp(
        WidgetNeutral.lightHeatmapEmpty,
        WidgetNeutral.lightSurface,
        palette.heatmapSeed(dark = false),
        listOf(0.20f, 0.42f, 0.70f, 1f),
    )
    val darkRamp = ramp(
        WidgetNeutral.darkHeatmapEmpty,
        WidgetNeutral.darkSurface,
        palette.heatmapSeed(dark = true),
        listOf(0.32f, 0.54f, 0.77f, 1f),
    )

    return WidgetColors(
        background = pair(WidgetNeutral.lightBackground, WidgetNeutral.darkBackground),
        surface = pair(WidgetNeutral.lightSurface, WidgetNeutral.darkSurface),
        onSurface = pair(WidgetNeutral.lightOnSurface, WidgetNeutral.darkOnSurface),
        onSurfaceVariant = pair(
            WidgetNeutral.lightOnSurfaceVariant,
            WidgetNeutral.darkOnSurfaceVariant,
        ),
        accent = pair(palette.lightPrimary, palette.darkPrimary),
        onAccent = pair(palette.lightOnPrimary, palette.darkOnPrimary),
        accentContainer = pair(palette.lightContainer, palette.darkContainer),
        border = pair(WidgetNeutral.lightBorder, WidgetNeutral.darkBorder),
        progressTrack = pair(WidgetNeutral.lightTrack, WidgetNeutral.darkTrack),
        heatmap = lightRamp.zip(darkRamp) { l, d -> pair(l, d) },
    )
}

fun UserPreferences.widgetColors(): WidgetColors = widgetColors(accentColor, themeMode)
