package dev.achyutem.cadence.feature.notes

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.database.dao.NoteSummary
import dev.achyutem.cadence.core.designsystem.component.ButtonTone
import dev.achyutem.cadence.core.designsystem.component.CadenceAddFab
import dev.achyutem.cadence.core.designsystem.component.CadenceButton
import dev.achyutem.cadence.core.designsystem.component.CadenceIconButton
import dev.achyutem.cadence.core.designsystem.component.FullScreenEmptyState
import dev.achyutem.cadence.core.designsystem.component.SectionHeader
import dev.achyutem.cadence.core.designsystem.theme.CadencePreviewTheme
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.token.Borders
import dev.achyutem.cadence.core.designsystem.token.Motion
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.core.time.formatShort
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.ZoneId

@Composable
fun NotesScreen(
    onOpenNote: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotesViewModel = viewModel(factory = NotesViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.queryText.collectAsStateWithLifecycle()
    Box(modifier = modifier.fillMaxSize()) {
        NotesContent(
            state = state,
            query = query,
            onQueryChange = viewModel::setQuery,
            onOpenNote = onOpenNote,
            onTogglePin = viewModel::togglePinned,
        )

        CadenceAddFab(
            contentDescription = stringResource(R.string.notes_new),
            onClick = { onOpenNote(0L) },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun NotesContent(
    state: NotesUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenNote: (Long) -> Unit,
    onTogglePin: (NoteSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Spacing.screenGutter,
                    end = Spacing.screenGutter - Spacing.xs,
                    top = Spacing.xl,
                    bottom = Spacing.md,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.notes_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(horizontal = Spacing.screenGutter),
        )
        Spacer(Modifier.height(Spacing.md))

        when {
            state.loading -> Unit

            state.isEmpty && query.isNotBlank() -> FullScreenEmptyState(
                title = stringResource(R.string.notes_no_matches_title),
                description = stringResource(R.string.notes_no_matches_description, query),
            )

            state.isEmpty -> FullScreenEmptyState(
                title = stringResource(R.string.notes_empty_title),
                description = stringResource(R.string.notes_empty_description),
                action = {
                    CadenceButton(
                        text = stringResource(R.string.notes_empty_action),
                        onClick = { onOpenNote(0L) },
                        tone = ButtonTone.Primary,
                    )
                },
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.screenGutter,
                    end = Spacing.screenGutter,
                    bottom = Spacing.fabClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (state.pinned.isNotEmpty()) {
                    item(key = "pinned-header") {
                        SectionHeader(title = stringResource(R.string.notes_pinned))
                        Spacer(Modifier.height(Spacing.xxs))
                    }
                    items(state.pinned, key = { "p${it.id}" }) { note ->
                        NoteRow(note, onClick = { onOpenNote(note.id) }, onTogglePin = { onTogglePin(note) })
                    }
                    if (state.others.isNotEmpty()) {
                        item(key = "others-header") {
                            Spacer(Modifier.height(Spacing.md))
                            SectionHeader(title = stringResource(R.string.notes_all))
                            Spacer(Modifier.height(Spacing.xxs))
                        }
                    }
                }
                items(state.others, key = { "n${it.id}" }) { note ->
                    NoteRow(note, onClick = { onOpenNote(note.id) }, onTogglePin = { onTogglePin(note) })
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(Radius.shapeMd)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(Borders.hairline, CadenceTheme.colors.border, Radius.shapeMd)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.notes_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            CadenceIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.notes_search_clear),
                onClick = { onQueryChange("") },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun NoteRow(
    note: NoteSummary,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.shapeLg)
            .background(MaterialTheme.colorScheme.surface)
            .border(Borders.hairline, CadenceTheme.colors.border, Radius.shapeLg)
            .clickable(onClick = onClick)
            .padding(start = Spacing.md, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm)
            .animateContentSize(tween(CadenceTheme.duration(Motion.STANDARD))),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note.title.ifBlank { stringResource(R.string.notes_untitled) },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.preview.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = note.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = note.updatedAt.atZone(ZoneId.systemDefault()).toLocalDate().formatShort(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CadenceIconButton(
            icon = Icons.Rounded.PushPin,
            contentDescription = stringResource(
                if (note.pinned) R.string.notes_unpin else R.string.notes_pin,
            ),
            onClick = onTogglePin,
            tone = if (note.pinned) ButtonTone.Primary else ButtonTone.Ghost,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NotesPreview() = CadencePreviewTheme {
    NotesContent(
        state = NotesUiState(
            pinned = listOf(
                NoteSummary(1, "Reading list", "Designing Data-Intensive Applications · The Timeless Way of Building", true, false, 0, Instant.now(), Instant.now()),
            ),
            others = listOf(
                NoteSummary(2, "Standup notes", "Shipped the subscription UI. Blocked on tax rounding.", false, false, 0, Instant.now(), Instant.now()),
                NoteSummary(3, "Ideas", "A heatmap that shows time-of-day, not just day", false, false, 0, Instant.now(), Instant.now()),
            ),
            loading = false,
        ),
        query = "",
        onQueryChange = {},
        onOpenNote = {},
        onTogglePin = {},
    )
}
