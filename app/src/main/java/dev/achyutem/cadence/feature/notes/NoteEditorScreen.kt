package dev.achyutem.cadence.feature.notes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.designsystem.component.ButtonTone
import dev.achyutem.cadence.core.designsystem.component.CadenceDivider
import dev.achyutem.cadence.core.designsystem.component.CadenceIconButton
import dev.achyutem.cadence.core.designsystem.component.MarkdownText
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.token.Motion
import dev.achyutem.cadence.core.designsystem.token.Spacing

/**
 * The note editor.
 *
 * Two modes over one document, and which one you land in depends on what is there. A note with
 * content opens in **view mode**, because most of the time a note is opened to be read and landing
 * in an editor with a cursor in someone's prose invites accidental edits. A new or empty note opens
 * straight into editing, because there is nothing to read.
 *
 * Touching the body moves you into editing. Ticking a checkbox does not: that is reading
 * behaviour, and dropping someone into a text editor because they checked a box is the wrong
 * response.
 *
 * There is no save button. See [NoteEditorViewModel].
 */
@Composable
fun NoteEditorScreen(
    noteId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NoteEditorViewModel = viewModel(
        key = "note-$noteId",
        factory = NoteEditorViewModel.factory(noteId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Read here: `transitionSpec` runs outside composition and cannot call into the theme.
    val modeFadeMillis = CadenceTheme.duration(Motion.QUICK)

    /**
     * The body's text and selection.
     *
     * Hoisted to the screen so the format bar, which has to live outside the scrolling content to
     * stay above the keyboard, can act on the current selection. Keyed on the note so opening a
     * different one starts fresh rather than inheriting the last caret.
     */
    var body by remember(noteId, state.loading) {
        mutableStateOf(TextFieldValue(state.content, TextRange(state.content.length)))
    }

    // The model can change the body without the field doing it: a checkbox toggled in preview, or
    // a list continued. Sync in that direction only, and clamp the caret so it cannot land past
    // the end of shorter text.
    LaunchedEffect(state.content) {
        if (body.text != state.content) {
            body = body.copy(
                text = state.content,
                selection = TextRange(body.selection.start.coerceIn(0, state.content.length)),
            )
        }
    }

    // Whatever route the user takes out of this screen, the pending autosave is flushed first.
    DisposableEffect(Unit) { onDispose { viewModel.saveNow() } }
    BackHandler {
        viewModel.saveNow()
        onBack()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        EditorToolbar(
            previewMode = state.previewMode,
            pinned = state.pinned,
            onBack = { viewModel.saveNow(); onBack() },
            onTogglePreview = viewModel::togglePreview,
            onTogglePin = viewModel::togglePinned,
            onDelete = { viewModel.delete(onBack) },
        )
        CadenceDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter),
        ) {
            Spacer(Modifier.height(Spacing.md))

            PlainTextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                placeholder = stringResource(R.string.note_title_placeholder),
                textStyle = MaterialTheme.typography.headlineMedium,
                singleLine = true,
            )
            Spacer(Modifier.height(Spacing.sm))

            AnimatedContent(
                targetState = state.previewMode,
                transitionSpec = {
                    fadeIn(tween(modeFadeMillis)) togetherWith fadeOut(tween(modeFadeMillis))
                },
                label = "editorMode",
            ) { preview ->
                if (preview) {
                    // The tap target extends well past the rendered text. Without a minimum
                    // height, a short note leaves a large dead area, and a note made entirely of
                    // task rows has no tappable text at all, because each row belongs to its own
                    // checkbox. Either case would leave the toolbar toggle as the only way in.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 320.dp)
                            .clickable(
                                // No ripple: this is a whole page of text, not a button.
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = viewModel::beginEditing,
                            ),
                    ) {
                        MarkdownText(
                            source = state.content,
                            onToggleTask = viewModel::toggleTaskAtLine,
                        )
                    }
                } else {
                    MarkdownBody(
                        value = body,
                        onValueChange = { next ->
                            val continued = continueListIfNewline(body, next, viewModel::continueList)
                            if (continued != null) {
                                body = continued
                            } else {
                                body = next
                                viewModel.setContent(next.text)
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(Spacing.huge))
        }

        // Pinned below the scrolling content and inside the screen's imePadding, so it rides
        // above the keyboard rather than being buried behind it. Hidden in view mode, where
        // there is nothing to format.
        AnimatedVisibility(
            visible = !state.previewMode,
            enter = fadeIn(tween(modeFadeMillis)),
            exit = fadeOut(tween(modeFadeMillis)),
        ) {
            Column {
                CadenceDivider()
                FormatBar(
                    onApply = { transform ->
                        val next = transform(body)
                        body = next
                        viewModel.setContent(next.text)
                    },
                )
            }
        }
    }
}

/**
 * Detect that the user just pressed Enter, and continue the list if the caret was in one.
 *
 * Returns the replacement field value, or null when this was an ordinary edit the field should
 * handle itself. The model is updated by [onContinue], which also owns the autosave.
 */
private fun continueListIfNewline(
    previous: TextFieldValue,
    next: TextFieldValue,
    onContinue: (caret: Int) -> Pair<String, Int>?,
): TextFieldValue? {
    val insertedOneChar = next.text.length == previous.text.length + 1
    val caret = next.selection.start
    if (!insertedOneChar || !next.selection.collapsed || caret <= 0) return null
    if (next.text[caret - 1] != '\n') return null

    // The caret in the *old* text is where Enter was pressed.
    val (text, newCaret) = onContinue(caret - 1) ?: return null
    return TextFieldValue(text, TextRange(newCaret))
}

@Composable
private fun EditorToolbar(
    previewMode: Boolean,
    pinned: Boolean,
    onBack: () -> Unit,
    onTogglePreview: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        CadenceIconButton(
            icon = Icons.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
            onClick = onBack,
        )
        Spacer(Modifier.weight(1f))
        CadenceIconButton(
            icon = Icons.Rounded.PushPin,
            contentDescription = stringResource(if (pinned) R.string.notes_unpin else R.string.notes_pin),
            onClick = onTogglePin,
            tone = if (pinned) ButtonTone.Primary else ButtonTone.Ghost,
        )
        CadenceIconButton(
            icon = if (previewMode) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            contentDescription = stringResource(
                if (previewMode) R.string.note_edit_mode else R.string.note_preview_mode,
            ),
            onClick = onTogglePreview,
            tone = if (previewMode) ButtonTone.Primary else ButtonTone.Ghost,
        )
        CadenceIconButton(
            icon = Icons.Rounded.Delete,
            contentDescription = stringResource(R.string.action_delete),
            onClick = onDelete,
            tone = ButtonTone.Danger,
        )
    }
}

/** A borderless text field that looks like the text it holds, used for the note's heading. */
@Composable
private fun PlainTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun MarkdownBody(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Box {
                if (value.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.note_body_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 240.dp),
    )
}

@Composable
private fun FormatBar(onApply: ((TextFieldValue) -> TextFieldValue) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CadenceIconButton(
            icon = Icons.Rounded.Title,
            contentDescription = stringResource(R.string.format_heading),
            onClick = { onApply { it.prefixLine("## ") } },
        )
        CadenceIconButton(
            icon = Icons.Rounded.FormatBold,
            contentDescription = stringResource(R.string.format_bold),
            onClick = { onApply { it.wrapSelection("**") } },
        )
        CadenceIconButton(
            icon = Icons.Rounded.FormatItalic,
            contentDescription = stringResource(R.string.format_italic),
            onClick = { onApply { it.wrapSelection("*") } },
        )
        CadenceIconButton(
            icon = Icons.Rounded.Code,
            contentDescription = stringResource(R.string.format_code),
            onClick = { onApply { it.wrapSelection("`") } },
        )
        CadenceIconButton(
            icon = Icons.Rounded.FormatListBulleted,
            contentDescription = stringResource(R.string.format_bullet),
            onClick = { onApply { it.prefixLine("- ") } },
        )
        CadenceIconButton(
            icon = Icons.Rounded.CheckBoxOutlineBlank,
            contentDescription = stringResource(R.string.format_task),
            onClick = { onApply { it.prefixLine("- [ ] ") } },
        )
    }
}

/** Wrap the selection in [marker], or insert an empty pair with the caret in the middle. */
private fun TextFieldValue.wrapSelection(marker: String): TextFieldValue {
    val start = selection.min
    val end = selection.max
    return if (start == end) {
        TextFieldValue(
            text = text.substring(0, start) + marker + marker + text.substring(start),
            selection = TextRange(start + marker.length),
        )
    } else {
        val selected = text.substring(start, end)
        TextFieldValue(
            text = text.substring(0, start) + marker + selected + marker + text.substring(end),
            selection = TextRange(start + marker.length, end + marker.length),
        )
    }
}

/** Insert [prefix] at the start of the line the caret is on, if it is not already there. */
private fun TextFieldValue.prefixLine(prefix: String): TextFieldValue {
    val caret = selection.min
    val lineStart = text.lastIndexOf('\n', (caret - 1).coerceAtLeast(0))
        .let { if (it < 0) 0 else it + 1 }
    if (text.startsWith(prefix, lineStart)) return this
    return TextFieldValue(
        text = text.substring(0, lineStart) + prefix + text.substring(lineStart),
        selection = TextRange(caret + prefix.length),
    )
}
