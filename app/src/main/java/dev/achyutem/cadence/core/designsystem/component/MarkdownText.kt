package dev.achyutem.cadence.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.token.Borders
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.domain.notes.Markdown
import dev.achyutem.cadence.domain.notes.MarkdownBlock
import dev.achyutem.cadence.domain.notes.MarkdownSpan

/**
 * Renders the Markdown subset in [Markdown].
 *
 * Parsing is memoised on the source string, so scrolling a long note does not re-parse it on
 * every frame, the parse happens once per edit, not once per recomposition.
 *
 * [onToggleTask] receives the *source line index* of a tapped checkbox, so the caller can rewrite
 * that line in the original text. The rendered view never becomes the source of truth.
 */
@Composable
fun MarkdownText(
    source: String,
    modifier: Modifier = Modifier,
    onToggleTask: ((Int) -> Unit)? = null,
) {
    val blocks = remember(source) { Markdown.parse(source) }

    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    if (index > 0) Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = block.spans.toAnnotated(),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.titleMedium
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.xxs))
                }

                is MarkdownBlock.Paragraph -> Text(
                    text = block.spans.toAnnotated(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                is MarkdownBlock.BulletItem -> ListRow(marker = "•") {
                    Text(
                        text = block.spans.toAnnotated(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                is MarkdownBlock.NumberedItem -> ListRow(marker = "${block.number}.") {
                    Text(
                        text = block.spans.toAnnotated(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                is MarkdownBlock.TaskItem -> TaskRow(
                    checked = block.checked,
                    spans = block.spans,
                    onToggle = onToggleTask?.let { toggle -> { toggle(block.sourceLine) } },
                )

                is MarkdownBlock.Quote -> Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .width(2.dp)
                            .height(20.dp)
                            .background(CadenceTheme.colors.borderStrong),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = block.spans.toAnnotated(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is MarkdownBlock.CodeBlock -> Box(
                    modifier = Modifier
                        .padding(vertical = Spacing.xxs)
                        .fillMaxWidth()
                        .clip(Radius.shapeSm)
                        .background(CadenceTheme.colors.chipSurface)
                        .padding(Spacing.sm),
                ) {
                    Text(
                        text = block.code,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                }

                MarkdownBlock.Rule -> Box(
                    modifier = Modifier
                        .padding(vertical = Spacing.sm)
                        .fillMaxWidth()
                        .height(Borders.hairline)
                        .background(CadenceTheme.colors.divider),
                )

                // A blank source line is vertical space, which is exactly what the author meant.
                MarkdownBlock.Blank -> Spacer(Modifier.height(Spacing.xs))
            }
        }
    }
}

@Composable
private fun ListRow(marker: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = marker,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp),
        )
        content()
    }
}

@Composable
private fun TaskRow(
    checked: Boolean,
    spans: List<MarkdownSpan>,
    onToggle: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(17.dp)
                .clip(Radius.shapeXs)
                .background(
                    if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
                )
                .then(
                    if (checked) {
                        Modifier
                    } else {
                        Modifier.border(Borders.hairline, CadenceTheme.colors.borderStrong, Radius.shapeXs)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Text(
            text = spans.toAnnotated(),
            style = MaterialTheme.typography.bodyLarge,
            color = if (checked) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (checked) TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun List<MarkdownSpan>.toAnnotated(): AnnotatedString {
    val codeBackground = CadenceTheme.colors.chipSurface
    return buildAnnotatedString {
        this@toAnnotated.forEach { span ->
            val style = SpanStyle(
                fontWeight = if (span.bold) FontWeight.SemiBold else null,
                fontStyle = if (span.italic) FontStyle.Italic else null,
                fontFamily = if (span.code) FontFamily.Monospace else null,
                background = if (span.code) codeBackground else Color.Unspecified,
                textDecoration = if (span.strikethrough) TextDecoration.LineThrough else null,
            )
            withStyle(style) { append(span.text) }
        }
    }
}
