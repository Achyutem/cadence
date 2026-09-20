package dev.achyutem.cadence.core.designsystem.token

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing scale. Four-point base, with a 2dp step for optical alignment of small glyphs.
 *
 * Screens compose from this scale rather than literal dp values; that is what keeps the rhythm
 * consistent between a dense task row and a spacious empty state.
 */
object Spacing {
    val hairline: Dp = 2.dp
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 20.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val xxxl: Dp = 40.dp
    val huge: Dp = 56.dp

    /** Horizontal page gutter. Every screen uses this so content edges line up across tabs. */
    val screenGutter: Dp = 20.dp

    /** Bottom padding reserved so content can scroll clear of the floating dock. */
    val dockClearance: Dp = 104.dp
}

/**
 * Corner radii, tightened to the Geist range.
 *
 * The earlier scale (10/14/20) read soft and consumer-ish. A crisp product UI lives at 6–12dp:
 * enough to feel considered, not enough to look like a toy. Only the dock and status chips are
 * fully round, where the pill shape is doing actual work.
 */
object Radius {
    val xs: Dp = 4.dp
    val sm: Dp = 6.dp
    val md: Dp = 8.dp
    val lg: Dp = 12.dp
    val xl: Dp = 16.dp

    val shapeXs = RoundedCornerShape(xs)
    val shapeSm = RoundedCornerShape(sm)
    val shapeMd = RoundedCornerShape(md)
    val shapeLg = RoundedCornerShape(lg)
    val shapeXl = RoundedCornerShape(xl)
    val pill = RoundedCornerShape(percent = 50)
}

/**
 * Border widths.
 *
 * Structure in this app comes from **hairline borders**, not shadows. A 1dp border at low
 * contrast separates surfaces without adding visual weight, stays crisp at any density, and —
 * unlike a shadow — looks identical in dark mode instead of disappearing into the background.
 */
object Borders {
    val hairline: Dp = 1.dp
    /** Used only for focus rings and the selected state of a segmented control. */
    val emphasis: Dp = 1.5.dp
}

/**
 * Motion tokens.
 *
 * Durations are deliberately short. The fastest-feeling interfaces are not the ones with the
 * slickest animation but the ones that get out of the way: a tap should resolve before you have
 * finished thinking about it. Anything over ~250ms on a routine interaction reads as lag.
 *
 * These are multiplied by [dev.achyutem.cadence.core.designsystem.theme.LocalMotionScale], which
 * collapses to 0 when the user has asked for reduced motion.
 */
object Motion {
    const val INSTANT = 0
    const val MICRO = 120
    const val QUICK = 160
    const val STANDARD = 200
    const val EMPHASIZED = 280
    const val EXPRESSIVE = 380

    /**
     * The workhorse curve: leaves immediately, settles gently. Equivalent to CSS
     * `cubic-bezier(.4, 0, .2, 1)` — the curve almost every well-tuned web UI converges on.
     */
    val standardEasing: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** Entering elements — decelerate into place with no overshoot. */
    val enterEasing: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

    /** Exiting elements — accelerate away. Always paired with a shorter duration than the enter. */
    val exitEasing: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /** Sliding indicators and thumbs: critically damped, so it arrives without wobbling. */
    const val SPRING_DAMPING = 0.9f
    const val SPRING_STIFFNESS = 700f

    /** Completion checkmarks, where a little life is wanted. */
    const val SPRING_DAMPING_BOUNCY = 0.62f
    const val SPRING_STIFFNESS_BOUNCY = 900f
}

/**
 * Elevation is used almost nowhere. The dock floats, and transient surfaces (sheets, menus,
 * dropdowns) lift. Everything else is separated by tone and a hairline border.
 */
object Elevation {
    val none: Dp = 0.dp
    val raised: Dp = 1.dp
    val dock: Dp = 12.dp
    val sheet: Dp = 16.dp
}

/** Minimum touch targets. [compact] is the smallest permitted for a control inside a row. */
object TouchTarget {
    val min: Dp = 48.dp
    val compact: Dp = 40.dp
    /** Segmented-control and chip height — smaller than a tap target, so the parent pads it out. */
    val control: Dp = 34.dp
}
