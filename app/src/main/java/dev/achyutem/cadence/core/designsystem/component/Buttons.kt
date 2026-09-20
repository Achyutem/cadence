package dev.achyutem.cadence.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.token.Borders
import dev.achyutem.cadence.core.designsystem.token.Motion
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing

enum class ButtonTone {
    /** The one action on screen that matters. Filled with accent. */
    Primary,

    /** Everything else. Bordered, transparent fill. */
    Secondary,

    /** Tertiary actions inside dense rows. No border, no fill. */
    Ghost,

    /** Destructive. Bordered, danger-coloured text. */
    Danger,
}

enum class ButtonSize { Small, Medium }

/**
 * The app's button.
 *
 * Material's buttons carry a ripple, a 20dp radius, an elevation and a 40dp minimum that all
 * fight the rest of this design language. This one is a bordered or filled rectangle with a tight
 * radius, and its only press feedback is a **1.5% scale-down** — small enough that you feel it
 * rather than see it, which is what makes a button feel physical instead of decorated.
 */
@Composable
fun CadenceButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Secondary,
    size: ButtonSize = ButtonSize.Medium,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = Motion.SPRING_DAMPING,
            stiffness = Motion.SPRING_STIFFNESS,
        ),
        label = "buttonPress",
    )

    val colors = MaterialTheme.colorScheme
    val background = when (tone) {
        ButtonTone.Primary -> colors.primary
        else -> Color.Transparent
    }
    val contentColor = when (tone) {
        ButtonTone.Primary -> colors.onPrimary
        ButtonTone.Secondary -> colors.onSurface
        ButtonTone.Ghost -> colors.onSurfaceVariant
        ButtonTone.Danger -> CadenceTheme.colors.danger
    }
    val border = when (tone) {
        ButtonTone.Secondary -> BorderStroke(Borders.hairline, CadenceTheme.colors.border)
        ButtonTone.Danger -> BorderStroke(Borders.hairline, CadenceTheme.colors.border)
        else -> null
    }
    val height = if (size == ButtonSize.Small) 32.dp else 38.dp
    val horizontal = if (size == ButtonSize.Small) Spacing.sm else Spacing.md
    val alpha = if (enabled) 1f else 0.45f

    Row(
        modifier = modifier
            .scale(scale)
            .height(height)
            .defaultMinSize(minWidth = height)
            .clip(Radius.shapeSm)
            .background(background.copy(alpha = background.alpha * alpha))
            .then(if (border != null) Modifier.border(border, Radius.shapeSm) else Modifier)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor.copy(alpha = alpha),
                modifier = Modifier.size(if (size == ButtonSize.Small) 15.dp else 17.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor.copy(alpha = alpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Square icon-only button, for row affordances and toolbar actions. */
@Composable
fun CadenceIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Ghost,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Motion.SPRING_DAMPING,
            stiffness = Motion.SPRING_STIFFNESS,
        ),
        label = "iconButtonPress",
    )
    val contentColor = when (tone) {
        ButtonTone.Primary -> MaterialTheme.colorScheme.primary
        ButtonTone.Danger -> CadenceTheme.colors.danger
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .size(38.dp)
            .clip(Radius.shapeSm)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier
                .size(19.dp)
                .scale(scale),
        )
    }
}
