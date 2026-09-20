package dev.achyutem.cadence.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.token.Borders
import dev.achyutem.cadence.core.designsystem.token.Elevation
import dev.achyutem.cadence.core.designsystem.token.Motion
import dev.achyutem.cadence.core.designsystem.token.Radius

/**
 * One entry in the dock. [selectedIcon] is normally the filled variant of [icon] — the weight
 * change registers before the colour does.
 */
data class DockItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

private val TabWidth = 54.dp
private val DockHeight = 56.dp
private val IndicatorHeight = 40.dp

/**
 * Cadence's primary navigation: a floating dock drawn over the content, narrower than the screen.
 *
 * **Fixed-width tabs with a sliding indicator.** An earlier version expanded the selected tab to
 * reveal its label, which looked elegant in a screenshot and was worse to use: every selection
 * re-laid-out the whole bar, so the tab you wanted next was never where you last saw it. With
 * five destinations that stops being a quirk and becomes a real cost. Now positions are constant,
 * every target is the same size, and the only thing that moves is the indicator travelling to the
 * tab you chose — which is also the thing that makes the transition read as one continuous
 * gesture rather than two independent fades.
 *
 * Labels are therefore not drawn. They are still present for accessibility as content
 * descriptions on every tab, selected or not, so a screen reader announces all five.
 *
 * The component does not consume window insets; the caller positions it.
 */
@Composable
fun CadenceDock(
    items: List<DockItem>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val selectedIndex = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val reduceMotion = CadenceTheme.duration(Motion.STANDARD) == 0

    val indicatorOffset by animateDpAsState(
        targetValue = TabWidth * selectedIndex,
        animationSpec = if (reduceMotion) {
            spring(stiffness = Float.MAX_VALUE)
        } else {
            spring(dampingRatio = Motion.SPRING_DAMPING, stiffness = Motion.SPRING_STIFFNESS)
        },
        label = "dockIndicator",
    )

    Box(
        modifier = modifier
            .shadow(Elevation.dock, Radius.pill, clip = false)
            .clip(Radius.pill)
            .background(CadenceTheme.colors.dockSurface)
            .border(Borders.hairline, CadenceTheme.colors.dockOutline, Radius.pill)
            .height(DockHeight)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(TabWidth)
                .height(IndicatorHeight)
                .clip(Radius.pill)
                .background(MaterialTheme.colorScheme.primaryContainer),
        )

        Row(
            modifier = Modifier.selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            items.forEachIndexed { index, item ->
                DockTab(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { onSelect(item.key) },
                )
            }
        }
    }
}

@Composable
private fun DockTab(
    item: DockItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(
            durationMillis = CadenceTheme.duration(Motion.QUICK),
            easing = Motion.standardEasing,
        ),
        label = "dockContent",
    )
    // A small settle on the icon as it becomes active. The indicator is already carrying the
    // movement, so this only needs to be felt, not seen.
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = spring(
            dampingRatio = Motion.SPRING_DAMPING,
            stiffness = Motion.SPRING_STIFFNESS,
        ),
        label = "dockIconScale",
    )

    Box(
        modifier = Modifier
            .width(TabWidth)
            .height(IndicatorHeight)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = {
                    if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (selected) item.selectedIcon else item.icon,
            // Always present, selected or not: no tab draws its label, so the description is the
            // only name a screen reader has.
            contentDescription = item.label,
            tint = contentColor,
            modifier = Modifier
                .size(21.dp)
                .scale(iconScale),
        )
    }
}
