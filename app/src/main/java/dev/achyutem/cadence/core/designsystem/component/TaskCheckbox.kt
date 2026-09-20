package dev.achyutem.cadence.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.token.Motion
import dev.achyutem.cadence.core.designsystem.token.TouchTarget

/**
 * The completion control.
 *
 * This is the single most-used interaction in the app, so it gets the most attention. Three things
 * happen together, and together they are what makes ticking something off feel good:
 *
 *  1. The box fills, on a spring with a small overshoot.
 *  2. The tick is **drawn**, not faded in; the stroke is revealed along its own path, so the
 *     mark appears to be made rather than to arrive. A cross-faded glyph looks like a state
 *     change; a drawn stroke looks like an action you performed.
 *  3. A haptic fires on completion only, never on un-completing, because undoing is a correction
 *     and should not be congratulated.
 *
 * Drawn on a Canvas rather than composed from Material's `Checkbox`, which animates a fade and
 * carries a 48dp ripple that cannot be tuned out.
 */
@Composable
fun TaskCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    enabled: Boolean = true,
    /** 0f..1f for a parent whose children are partly done; drawn as a partial fill. */
    partialProgress: Float = 0f,
    contentDescription: String? = null,
) {
    val haptics = LocalHapticFeedback.current

    val fillProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Motion.SPRING_DAMPING_BOUNCY,
            stiffness = Motion.SPRING_STIFFNESS_BOUNCY,
        ),
        label = "checkboxFill",
    )
    // The tick lags the fill slightly and runs faster, which is what reads as "drawn onto" the
    // filled box rather than "appearing with" it.
    val tickProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(
            durationMillis = CadenceTheme.duration(Motion.MICRO),
            delayMillis = if (checked) CadenceTheme.duration(Motion.MICRO) / 3 else 0,
            easing = Motion.standardEasing,
        ),
        label = "checkboxTick",
    )

    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val border = CadenceTheme.colors.borderStrong

    Box(
        modifier = modifier
            .size(TouchTarget.min)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onValueChange = { next ->
                    if (next) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onCheckedChange(next)
                },
            )
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 1.6.dp.toPx()
            val radius = 5.dp.toPx()
            val inset = stroke / 2f
            val boxSize = Size(this.size.width - stroke, this.size.height - stroke)
            val corner = CornerRadius(radius, radius)

            // Outline is always drawn; the fill grows over it.
            drawRoundRect(
                color = border,
                topLeft = Offset(inset, inset),
                size = boxSize,
                cornerRadius = corner,
                style = Stroke(width = stroke),
            )

            if (partialProgress > 0f && fillProgress < 1f) {
                // Partial parent state: a bar of fill from the bottom, never a tick. A half-done
                // parent is not done, and must not read as if it were.
                clipRect(
                    top = this.size.height * (1f - partialProgress),
                ) {
                    drawRoundRect(
                        color = accent.copy(alpha = 0.28f),
                        topLeft = Offset(inset, inset),
                        size = boxSize,
                        cornerRadius = corner,
                    )
                }
            }

            if (fillProgress > 0f) {
                scale(fillProgress) {
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(inset, inset),
                        size = boxSize,
                        cornerRadius = corner,
                    )
                }
            }

            if (tickProgress > 0f) {
                drawTick(
                    progress = tickProgress,
                    color = onAccent,
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }
    }
}

/**
 * Reveal the tick along its own length.
 *
 * The mark is two segments, down-right, then up-right. Rather than measuring the path, the
 * progress is split between the segments proportionally to their length, so the pen appears to
 * travel at a constant speed through the corner instead of pausing there.
 */
private fun DrawScope.drawTick(
    progress: Float,
    color: Color,
    strokeWidth: Float,
) {
    val w = size.width
    val h = size.height
    val start = Offset(w * 0.26f, h * 0.52f)
    val corner = Offset(w * 0.44f, h * 0.70f)
    val end = Offset(w * 0.75f, h * 0.32f)

    val firstLength = (corner - start).getDistance()
    val secondLength = (end - corner).getDistance()
    val total = firstLength + secondLength
    val travelled = total * progress

    val path = Path().apply {
        moveTo(start.x, start.y)
        if (travelled <= firstLength) {
            val t = if (firstLength == 0f) 0f else travelled / firstLength
            lineTo(start.x + (corner.x - start.x) * t, start.y + (corner.y - start.y) * t)
        } else {
            lineTo(corner.x, corner.y)
            val t = if (secondLength == 0f) 0f else (travelled - firstLength) / secondLength
            lineTo(corner.x + (end.x - corner.x) * t, corner.y + (end.y - corner.y) * t)
        }
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
