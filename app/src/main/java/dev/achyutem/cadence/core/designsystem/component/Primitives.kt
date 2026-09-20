package dev.achyutem.cadence.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.theme.tabularFigures
import dev.achyutem.cadence.core.designsystem.token.Borders
import dev.achyutem.cadence.core.designsystem.token.Motion
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing

/**
 * The small uppercase rule that heads each block ("TODOS", "HABITS", "NOTES").
 *
 * An optional [trailing] slot carries a count or an action on the same baseline, which keeps a
 * section header one dense line instead of a stack of two.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trailing?.invoke()
    }
}

/**
 * A flat progress bar with a rounded track.
 *
 * Drawn on a [Canvas] rather than using Material's `LinearProgressIndicator`, which in M3 renders
 * a gap and a stop indicator that are wrong for a calm progress row. Animating the drawn value
 * also means progress changes never re-layout the row they sit in.
 *
 * [progress] is clamped, so a habit at 150% of target still renders a full bar.
 */
@Composable
fun CadenceProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = CadenceTheme.colors.progressTrack,
    contentDescription: String? = null,
) {
    val target = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(
            durationMillis = CadenceTheme.duration(Motion.EMPHASIZED),
            easing = Motion.standardEasing,
        ),
        label = "progress",
    )

    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier.clearAndSetSemantics { }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(semanticsModifier),
    ) {
        drawRoundedBar(size.width, trackColor)
        if (animated > 0f) drawRoundedBar(size.width * animated, color)
    }
}

private fun DrawScope.drawRoundedBar(width: Float, color: Color) {
    if (width <= 0f) return
    drawRoundRect(
        color = color,
        topLeft = Offset.Zero,
        size = Size(width.coerceAtLeast(size.height), size.height),
        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
    )
}

/**
 * The shared empty state.
 *
 * Left-aligned, not centred: these sit inside sections whose headers are left-aligned, and a
 * centred block of text in a left-aligned column reads as a mistake. Text only, the brief rules
 * out decorative illustrations, and an empty list is a moment to be quiet rather than cheerful.
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
        if (action != null) {
            Box(modifier = Modifier.padding(top = Spacing.sm)) { action() }
        }
    }
}

/**
 * A centred empty state, for a whole screen with nothing in it yet, where there is no
 * left-aligned structure for it to line up with.
 */
@Composable
fun FullScreenEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xl, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp),
            )
        }
        if (action != null) {
            Box(modifier = Modifier.padding(top = Spacing.md)) { action() }
        }
    }
}

/** Hairline rule between dense rows. Thinner and quieter than the Material default. */
@Composable
fun CadenceDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        thickness = Borders.hairline,
        color = CadenceTheme.colors.divider,
    )
}

/**
 * A count or percentage that changes in place. Tabular figures, so the digits do not shift
 * horizontally as the value ticks.
 */
@Composable
fun MetricText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.tabularFigures,
        color = color,
        modifier = modifier,
    )
}

/**
 * The standard content surface: a hairline-bordered panel with a tight radius.
 *
 * Structure here comes from the border, not from a shadow or a tonal step. That reads as precise
 * rather than soft, and, unlike a shadow, it survives dark mode unchanged.
 */
@Composable
fun CadenceCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = Spacing.md,
    background: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.shapeLg)
            .background(background)
            .border(Borders.hairline, CadenceTheme.colors.border, Radius.shapeLg)
            .padding(contentPadding),
    ) {
        content()
    }
}

/** A small inert label, a tag, a unit, a count. */
@Composable
fun CadenceChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    background: Color = CadenceTheme.colors.chipSurface,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .clip(Radius.shapeXs)
            .background(background)
            .padding(horizontal = Spacing.xs, vertical = 3.dp),
    )
}
