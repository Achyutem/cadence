package dev.achyutem.cadence.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import dev.achyutem.cadence.R

/**
 * Typography.
 *
 * Cadence ships **one** font file: Geist Variable (SIL OFL, 166 KB, licence in `licenses/`). A
 * variable font means every weight from 100 to 900 comes from a single 166 KB asset instead of
 * five static files, so the whole type system costs less than two static weights would.
 *
 * Geist is a neutral grotesque built for interfaces: unusually even colour at small sizes, a
 * tall x-height that keeps 13sp list text legible, and genuine tabular figures, which matters
 * here, because this app is full of numbers that change in place.
 *
 * Variable-axis instancing requires API 26; `minSdk` is 34, so there is no fallback path.
 */
@OptIn(ExperimentalTextApi::class)
private fun geist(weight: Int) = Font(
    resId = R.font.geist_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val GeistFontFamily = FontFamily(
    geist(300),
    geist(400),
    geist(500),
    geist(600),
    geist(700),
)

private val trimmedLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

@Suppress("DEPRECATION")
private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Float,
): TextStyle = TextStyle(
    fontFamily = GeistFontFamily,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    // Without these two, large text sits inside a stubborn extra gap that makes precise vertical
    // spacing impossible: the optical box and the layout box stop agreeing.
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = trimmedLineHeight,
)

/**
 * The scale.
 *
 * Tracking is negative on everything above 18sp and neutral below. Large text set at default
 * tracking always looks loose; small UI text set tight becomes hard to scan. The only positive
 * tracking in the system is on the uppercase section labels, where the extra air is what makes
 * them read as structure rather than as content.
 */
val CadenceTypography = Typography(
    displayLarge = style(44, 48, FontWeight.Medium, -1.6f),
    displayMedium = style(38, 42, FontWeight.Medium, -1.4f),
    displaySmall = style(32, 36, FontWeight.Medium, -1.1f),

    headlineLarge = style(28, 34, FontWeight.SemiBold, -0.8f),
    headlineMedium = style(24, 30, FontWeight.SemiBold, -0.6f),
    headlineSmall = style(20, 26, FontWeight.SemiBold, -0.4f),

    titleLarge = style(18, 24, FontWeight.SemiBold, -0.3f),
    titleMedium = style(15, 21, FontWeight.Medium, -0.15f),
    titleSmall = style(14, 20, FontWeight.Medium, -0.1f),

    bodyLarge = style(15, 23, FontWeight.Normal, -0.1f),
    bodyMedium = style(14, 21, FontWeight.Normal, 0f),
    bodySmall = style(13, 19, FontWeight.Normal, 0f),

    labelLarge = style(14, 18, FontWeight.Medium, -0.1f),
    labelMedium = style(12, 16, FontWeight.Medium, 0f),
    // Section headers: TODOS, HABITS, NOTES.
    labelSmall = style(11, 14, FontWeight.Medium, 0.8f),
)

/**
 * Tabular figures, for any number that changes in place, progress percentages, streak counts,
 * habit values, timeline clock labels, heatmap tooltips.
 *
 * With proportional digits a counter ticking 8 → 9 → 10 shifts everything beside it sideways.
 * It is a small thing that makes an interface feel unfinished, and it is free to fix.
 */
val TextStyle.tabularFigures: TextStyle
    get() = copy(fontFeatureSettings = "tnum")
