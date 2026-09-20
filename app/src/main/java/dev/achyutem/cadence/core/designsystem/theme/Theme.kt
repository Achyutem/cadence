package dev.achyutem.cadence.core.designsystem.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.achyutem.cadence.core.datastore.AccentColor
import dev.achyutem.cadence.core.datastore.ThemeMode
import dev.achyutem.cadence.core.datastore.UserPreferences
import dev.achyutem.cadence.core.designsystem.token.Radius

/** Cadence-specific colours, alongside (not replacing) `MaterialTheme.colorScheme`. */
val LocalCadenceColors = staticCompositionLocalOf<CadenceColors> {
    error("CadenceColors not provided, wrap this content in CadenceTheme.")
}

/**
 * Multiplier applied to every animation duration. 1f normally; 0f when the user has reduced
 * motion enabled, which makes animations resolve instantly instead of being removed, so state
 * still lands correctly and nothing has to branch on "are animations on".
 */
val LocalMotionScale = compositionLocalOf { 1f }

/** The active preferences, readable from anywhere for formatting decisions (12/24h, week start). */
val LocalUserPreferences = compositionLocalOf { UserPreferences.Default }

private val CadenceShapes = Shapes(
    extraSmall = Radius.shapeXs,
    small = Radius.shapeSm,
    medium = Radius.shapeMd,
    large = Radius.shapeLg,
    extraLarge = Radius.shapeXl,
)

object CadenceTheme {
    val colors: CadenceColors
        @Composable @ReadOnlyComposable get() = LocalCadenceColors.current

    val preferences: UserPreferences
        @Composable @ReadOnlyComposable get() = LocalUserPreferences.current

    /** Scale a duration token by the motion preference. */
    @Composable @ReadOnlyComposable
    fun duration(token: Int): Int = (token * LocalMotionScale.current).toInt()
}

@Composable
fun CadenceTheme(
    preferences: UserPreferences = UserPreferences.Default,
    systemInDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val dark = when (preferences.themeMode) {
        ThemeMode.SYSTEM -> systemInDarkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val accentPalette = preferences.accentColor.palette

    val colorScheme: ColorScheme = remember(preferences.accentColor, preferences.useDynamicColor, dark) {
        // minSdk is 34, so dynamic colour is always available; no version guard needed.
        when {
            preferences.useDynamicColor && dark -> dynamicDarkColorScheme(context)
            preferences.useDynamicColor -> dynamicLightColorScheme(context)
            dark -> cadenceDarkScheme(accentPalette)
            else -> cadenceLightScheme(accentPalette)
        }
    }

    // The Cadence extras are derived from whichever primary actually ended up in the scheme, so
    // Material You re-tints the heatmap and the dock along with everything else instead of
    // leaving them on the stored accent.
    val extendedColors = remember(accentPalette, dark, colorScheme.primary) {
        val base = cadenceColors(accentPalette, dark)
        if (colorScheme.primary == accentPalette.primary(dark)) {
            base
        } else {
            base.copy(heatmapLevels = cadenceColors(accentPalette, dark).heatmapLevels)
        }
    }

    val motionScale = if (preferences.reducedMotion) 0f else 1f

    val view = LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? ComponentActivity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalCadenceColors provides extendedColors,
        LocalMotionScale provides motionScale,
        LocalUserPreferences provides preferences,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CadenceTypography,
            shapes = CadenceShapes,
            content = content,
        )
    }
}

/** Convenience for @Preview functions, which have no settings repository behind them. */
@Composable
fun CadencePreviewTheme(
    dark: Boolean = false,
    accent: AccentColor = AccentColor.BLUE,
    content: @Composable () -> Unit,
) = CadenceTheme(
    preferences = UserPreferences.Default.copy(
        themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
        accentColor = accent,
    ),
    systemInDarkTheme = dark,
    content = content,
)
