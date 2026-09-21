package dev.achyutem.cadence.feature.breathing

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.theme.tabularFigures
import dev.achyutem.cadence.core.designsystem.token.Motion
import dev.achyutem.cadence.domain.breathing.BreathPhaseKind
import dev.achyutem.cadence.domain.breathing.asClock

/**
 * The breathing circle.
 *
 * The whole point of a guided breathing screen is that you should be able to follow it **without
 * reading anything**. So the circle is the instruction: it grows through an inhale, holds its
 * size through a hold, shrinks through an exhale. Eyes half-closed, the size alone tells you what
 * to do, which is the state most of these exercises are done in.
 *
 * Size is driven directly by phase progress rather than by an infinite looping animation, so the
 * circle can never drift out of step with the countdown; the two are the same number.
 *
 * A ring around it sweeps the phase like a clock hand. The runner only ticks once a second, so
 * both the sweep and the fill are animated linearly over exactly one second: the target lands on
 * each tick and the animation carries the eye between them. Linear, not eased, because an eased
 * second stalls at its own edges and a clock that hesitates every second looks broken. A fresh
 * phase resets rather than animating backwards from full to empty.
 *
 * The countdown text is excluded from the accessibility tree: a value that changes every second
 * makes a screen reader unusable. The phase announcement belongs to the caller, which says it
 * once per phase.
 */
@Composable
fun BreathCircle(
    kind: BreathPhaseKind,
    phaseProgress: Float,
    secondsLeft: Int,
    /** Which phase of the session this is, so a new one restarts the sweep rather than unwinding. */
    phaseIndex: Int,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp,
) {
    // Where the lungs are, 0f = empty, 1f = full.
    val targetFill = when (kind) {
        BreathPhaseKind.INHALE -> phaseProgress
        BreathPhaseKind.HOLD_FULL -> 1f
        BreathPhaseKind.EXHALE -> 1f - phaseProgress
        BreathPhaseKind.HOLD_EMPTY -> 0f
        // A breathe-up or lead-in is free breathing; the circle rests mid-way rather than
        // pretending to conduct it.
        BreathPhaseKind.RECOVER, BreathPhaseKind.PREPARE -> 0.45f
    }

    // Reduced motion gets the raw value. A sweep is decoration on top of a number that is
    // already on screen, and it is the one thing here someone might ask to stop moving.
    val animate = CadenceTheme.motionScale > 0f

    val fill by animateFloatAsState(
        targetValue = targetFill,
        animationSpec = tween(durationMillis = if (animate) TICK_MILLIS else 0, easing = LinearEasing),
        label = "breathFill",
    )

    /**
     * The clock hand.
     *
     * Keyed on the phase index so the first frame of a new phase jumps to 0 instead of unwinding
     * from wherever the last one finished, which would run the sweep backwards through the whole
     * dial every time a round changed.
     */
    val sweep = remember(phaseIndex) { Animatable(0f) }
    LaunchedEffect(phaseIndex, phaseProgress, animate) {
        if (animate) {
            sweep.animateTo(
                targetValue = phaseProgress.coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = TICK_MILLIS, easing = LinearEasing),
            )
        } else {
            sweep.snapTo(phaseProgress.coerceIn(0f, 1f))
        }
    }

    val accent = MaterialTheme.colorScheme.primary
    val ringTrack = CadenceTheme.colors.progressTrack
    val holdColor = CadenceTheme.colors.warning

    val circleColor by animateColorAsState(
        targetValue = when (kind) {
            BreathPhaseKind.HOLD_FULL, BreathPhaseKind.HOLD_EMPTY -> holdColor
            else -> accent
        },
        animationSpec = tween(CadenceTheme.duration(Motion.EMPHASIZED)),
        label = "breathColor",
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val outer = this.size.minDimension / 2f
            val ringStroke = 3.dp.toPx()

            drawCircle(color = ringTrack, radius = outer - ringStroke / 2f, style = Stroke(ringStroke))
            drawArc(
                color = circleColor,
                startAngle = -90f,
                sweepAngle = 360f * sweep.value,
                useCenter = false,
                style = Stroke(ringStroke),
                topLeft = androidx.compose.ui.geometry.Offset(ringStroke / 2f, ringStroke / 2f),
                size = androidx.compose.ui.geometry.Size(
                    this.size.width - ringStroke,
                    this.size.height - ringStroke,
                ),
            )

            // The breathing body. Never smaller than MIN_FILL of the circle, so an empty hold is
            // still a visible object rather than a vanishing dot.
            val minRadius = outer * MIN_FILL
            val maxRadius = outer - ringStroke * 4
            drawCircle(
                color = circleColor.copy(alpha = 0.16f),
                radius = minRadius + (maxRadius - minRadius) * fill,
            )
        }

        Text(
            text = secondsLeft.asClock(),
            style = MaterialTheme.typography.displayMedium.tabularFigures,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

private const val MIN_FILL = 0.28f

/** One tick of the runner. The sweep spends exactly this long crossing each second. */
private const val TICK_MILLIS = 1_000
