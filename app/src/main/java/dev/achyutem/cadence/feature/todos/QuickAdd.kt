package dev.achyutem.cadence.feature.todos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.database.entity.TaskPriority
import dev.achyutem.cadence.core.designsystem.component.ButtonTone
import dev.achyutem.cadence.core.designsystem.component.CadenceIconButton
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.token.Borders
import dev.achyutem.cadence.core.designsystem.token.Elevation
import dev.achyutem.cadence.core.designsystem.token.Motion
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.core.time.formatShort
import androidx.compose.ui.draw.shadow
import java.time.LocalDate
import java.time.LocalTime

/**
 * Quick add.
 *
 * The brief is blunt about this: creating a task must be *extremely* fast. So the primary path is
 * one line of text and one key, type, press Done, task exists. Date, time and priority are
 * available right there, but nothing is required and nothing blocks the save.
 *
 * The field **stays open and clears after each save**, because tasks arrive in bursts: capturing
 * four things in a row should be four sentences, not four round trips through a dialog. The
 * keyboard never dismisses until the user is finished.
 */
@Composable
fun QuickAddBar(
    visible: Boolean,
    /** Today, from the app clock. Never read from [LocalDate.now]; see `CLAUDE.md` #15. */
    today: LocalDate,
    onDismiss: () -> Unit,
    onSubmit: (title: String, date: LocalDate?, time: LocalTime?, priority: TaskPriority) -> Unit,
    modifier: Modifier = Modifier,
    defaultDate: LocalDate? = null,
) {
    var text by remember { mutableStateOf("") }
    var date by remember(defaultDate) { mutableStateOf(defaultDate) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var priority by remember { mutableStateOf(TaskPriority.NONE) }

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Back closes the composer before it closes the screen. Without this the first back press
    // leaves the app entirely while a half-typed task is on screen, which is the worst possible
    // outcome for a capture box.
    BackHandler(enabled = visible) {
        keyboard?.hide()
        onDismiss()
    }

    LaunchedEffect(visible) {
        if (visible) {
            focusRequester.requestFocus()
            keyboard?.show()
        } else {
            text = ""
            time = null
            priority = TaskPriority.NONE
        }
    }

    fun submit() {
        val title = text.trim()
        if (title.isEmpty()) return
        onSubmit(title, date, time, priority)
        // Cleared, but left open and focused: the next capture starts immediately.
        text = ""
        time = null
        priority = TaskPriority.NONE
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(CadenceTheme.duration(Motion.QUICK))),
        exit = fadeOut(tween(CadenceTheme.duration(Motion.MICRO))),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(Elevation.sheet, Radius.shapeLg, clip = false)
                    .clip(Radius.shapeLg)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(Borders.hairline, CadenceTheme.colors.border, Radius.shapeLg)
                    .padding(Spacing.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f).padding(horizontal = Spacing.xs)) {
                        if (text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.quick_add_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        )
                    }
                    CadenceIconButton(
                        icon = Icons.Rounded.ArrowUpward,
                        contentDescription = stringResource(R.string.quick_add_submit),
                        onClick = { submit() },
                        tone = if (text.isBlank()) ButtonTone.Ghost else ButtonTone.Primary,
                        enabled = text.isNotBlank(),
                    )
                }

                Spacer(Modifier.height(Spacing.xxs))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    QuickChip(
                        label = date?.let { dateChipLabel(it, today) }
                            ?: stringResource(R.string.quick_add_no_date),
                        active = date != null,
                        onClick = {
                            // Cycles today → tomorrow → none: three taps cover almost every
                            // capture, without opening a picker mid-thought.
                            val base = defaultDate ?: today
                            date = when (date) {
                                null -> base
                                base -> base.plusDays(1)
                                else -> null
                            }
                        },
                    )
                    QuickChip(
                        label = time?.let { "%02d:%02d".format(it.hour, it.minute) }
                            ?: stringResource(R.string.quick_add_no_time),
                        icon = Icons.Rounded.Schedule,
                        active = time != null,
                        onClick = {
                            time = when (time) {
                                null -> LocalTime.of(9, 0)
                                else -> null
                            }
                        },
                    )
                    QuickChip(
                        label = priorityLabel(priority),
                        icon = Icons.Rounded.Flag,
                        active = priority != TaskPriority.NONE,
                        onClick = {
                            priority = TaskPriority.entries[
                                (priority.ordinal + 1) % TaskPriority.entries.size
                            ]
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Row(
        modifier = Modifier
            .clip(Radius.shapeSm)
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
            )
            .border(
                Borders.hairline,
                if (active) MaterialTheme.colorScheme.primaryContainer else CadenceTheme.colors.border,
                Radius.shapeSm,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.xs, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(13.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun priorityLabel(priority: TaskPriority): String = stringResource(
    when (priority) {
        TaskPriority.NONE -> R.string.priority_none
        TaskPriority.LOW -> R.string.priority_low
        TaskPriority.MEDIUM -> R.string.priority_medium
        TaskPriority.HIGH -> R.string.priority_high
    }
)

@Composable
private fun dateChipLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.date_today)
    today.plusDays(1) -> stringResource(R.string.date_tomorrow)
    else -> date.formatShort()
}
