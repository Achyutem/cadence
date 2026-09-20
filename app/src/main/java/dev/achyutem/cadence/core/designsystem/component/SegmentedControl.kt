package dev.achyutem.cadence.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.token.Borders
import dev.achyutem.cadence.core.designsystem.token.Motion
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.core.designsystem.token.TouchTarget

/**
 * A single-choice control: a bordered track with one lifted chip that **slides** between options.
 *
 * The earlier version cross-faded each option's background independently. That is the cheap way
 * to build this, and it reads cheap: at any moment mid-transition two chips are half-visible and
 * none of them is the selection. Here there is exactly one chip, and it travels — so the control
 * always shows precisely one selected thing, and the movement itself tells you where the
 * selection went. It is the difference between a control that changes and a control that
 * *responds*.
 *
 * The chip is laid out as a sibling behind the labels rather than as a background on the selected
 * item, which is what makes a single continuously-animating indicator possible at all.
 *
 * Options are equal width. That is a deliberate constraint: it keeps the geometry trivial (no
 * subcomposition, no position measurement, nothing to go wrong on a re-layout) and it is correct
 * for the short, comparable labels this control is for. Anything with long or unbalanced labels
 * should be a different control.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (T) -> String,
) {
    if (options.isEmpty()) return
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val haptics = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(TouchTarget.control + Spacing.xxs * 2)
            .clip(Radius.shapeMd)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(Borders.hairline, CadenceTheme.colors.border, Radius.shapeMd)
            .padding(Spacing.xxs),
    ) {
        val trackWidth = maxWidth
        val itemWidth = trackWidth / options.size

        // Critically damped: the chip arrives exactly where it is going without wobbling past it.
        // A bouncier spring here would be the single most annoying animation in the app, because
        // this control is used constantly and its movement is always horizontal and short.
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = if (CadenceTheme.duration(Motion.STANDARD) == 0) {
                spring(stiffness = Float.MAX_VALUE)
            } else {
                spring(
                    dampingRatio = Motion.SPRING_DAMPING,
                    stiffness = Motion.SPRING_STIFFNESS,
                )
            },
            label = "segmentIndicator",
        )

        Box(
            modifier = Modifier
                // Lambda overload: reading the animated value in the layout phase instead of
                // in composition means each frame re-lays-out rather than recomposing.
                .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                .width(itemWidth)
                .height(TouchTarget.control)
                .clip(Radius.shapeSm)
                .background(CadenceTheme.colors.segmentSelected)
                .border(Borders.hairline, CadenceTheme.colors.border, Radius.shapeSm),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TouchTarget.control)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val contentColor by animateColorAsState(
                    targetValue = when {
                        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        isSelected -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(CadenceTheme.duration(Motion.QUICK)),
                    label = "segmentLabel",
                )
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .width(itemWidth)
                        .height(TouchTarget.control)
                        .selectable(
                            selected = isSelected,
                            enabled = enabled,
                            interactionSource = interactionSource,
                            // No ripple: the chip's movement is the feedback, and a ripple
                            // underneath a moving surface looks like a rendering mistake.
                            indication = null,
                            role = Role.RadioButton,
                            onClick = {
                                if (!isSelected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(option)
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Spacing.xs),
                    )
                }
            }
        }
    }
}
