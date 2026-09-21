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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import dev.achyutem.cadence.core.datastore.TimeFormat
import dev.achyutem.cadence.core.designsystem.component.CadenceDatePickerDialog
import dev.achyutem.cadence.core.designsystem.component.CadenceTimePickerDialog
import dev.achyutem.cadence.core.time.formatHourMinute
import dev.achyutem.cadence.core.time.formatShort
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.delay
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
// `isImeVisible` is the only way to observe the keyboard from Compose and has been experimental
// for years. Reading it is the whole fix for a composer that outlives its keyboard.
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    val use24Hour = CadenceTheme.preferences.timeFormat != TimeFormat.TWELVE_HOUR
    var text by remember { mutableStateOf("") }
    var date by remember(defaultDate) { mutableStateOf(defaultDate) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var priority by remember { mutableStateOf(TaskPriority.NONE) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    /**
     * How many chip menus are open.
     *
     * A count rather than a flag: tapping one chip while another's menu is open closes the first
     * and opens the second, and the two callbacks can arrive in either order. A flag would end up
     * false with a menu still on screen.
     */
    var openMenus by remember { mutableIntStateOf(0) }

    // Anything that legitimately takes the keyboard away without ending the capture.
    val holdingFocus = showDatePicker || showTimePicker || openMenus > 0

    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    if (showDatePicker) {
        CadenceDatePickerDialog(
            initial = date ?: defaultDate ?: today,
            onDismiss = { showDatePicker = false },
            onSelect = {
                date = it
                showDatePicker = false
            },
        )
    }
    if (showTimePicker) {
        CadenceTimePickerDialog(
            initial = time,
            use24Hour = use24Hour,
            onDismiss = { showTimePicker = false },
            onSelect = {
                time = it
                showTimePicker = false
            },
        )
    }

    // Back closes the composer before it closes the screen. Without this the first back press
    // leaves the app entirely while a half-typed task is on screen, which is the worst possible
    // outcome for a capture box.
    BackHandler(enabled = visible) {
        keyboard?.hide()
        onDismiss()
    }

    LaunchedEffect(visible) {
        if (!visible) {
            text = ""
            time = null
            priority = TaskPriority.NONE
        }
    }

    /**
     * Dismissing the keyboard dismisses the composer.
     *
     * The system back gesture hides the IME without reaching [BackHandler], which left the
     * composer stranded: an empty input floating above the dock, with the dock drawn over it,
     * and no obvious way to get rid of it. Anything that closes the keyboard is someone saying
     * they are done typing.
     *
     * Guarded on the keyboard having actually been up, so this cannot fire on the frame between
     * the composer appearing and the IME animating in, and suspended while [holdingFocus] is
     * true. A date picker or a chip menu takes the keyboard away as a matter of course; treating
     * that as "done typing" threw away the half-typed task the moment anyone touched a chip.
     *
     * When the interruption ends, the caret and the keyboard come back, so choosing a date leaves
     * you exactly where you were.
     */
    val imeVisible = WindowInsets.isImeVisible
    var keyboardWasUp by remember { mutableStateOf(false) }
    LaunchedEffect(visible, imeVisible, holdingFocus) {
        if (!visible) {
            keyboardWasUp = false
            return@LaunchedEffect
        }
        if (holdingFocus) {
            // Disarm. The keyboard is about to go for a reason, and it has to come back up
            // before an absence means anything again.
            keyboardWasUp = false
            return@LaunchedEffect
        }
        if (imeVisible) {
            keyboardWasUp = true
            return@LaunchedEffect
        }
        if (!keyboardWasUp) return@LaunchedEffect

        // Settle first. The IME reports itself hidden for a frame or two while a menu closes and
        // focus comes back, and dismissing on that flicker takes the composer away mid-gesture.
        // A keyboard that returns changes the keys and cancels this before it fires.
        delay(SETTLE_MILLIS)
        onDismiss()
    }

    LaunchedEffect(visible, holdingFocus) {
        if (visible && !holdingFocus) {
            focusRequester.requestFocus()
            keyboard?.show()
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
                    // Menus, not cycles. Tapping through today → tomorrow → none was fast for
                    // the two dates it knew and offered no way at all to reach a third, and the
                    // time chip could only ever say 09:00. One tap opens, one tap chooses, and
                    // the last item in each is the full picker.
                    ChipMenu(
                        label = date?.let { dateChipLabel(it, today) }
                            ?: stringResource(R.string.quick_add_no_date),
                        active = date != null,
                        onExpandedChange = { open -> openMenus += if (open) 1 else -1 },
                        options = buildList {
                            add(ChipOption(stringResource(R.string.date_today)) { date = today })
                            add(
                                ChipOption(stringResource(R.string.date_tomorrow)) {
                                    date = today.plusDays(1)
                                },
                            )
                            add(
                                ChipOption(stringResource(R.string.date_next_week)) {
                                    date = today.plusWeeks(1)
                                },
                            )
                            add(
                                ChipOption(stringResource(R.string.quick_add_pick_date)) {
                                    showDatePicker = true
                                },
                            )
                            if (date != null) {
                                add(ChipOption(stringResource(R.string.quick_add_no_date)) { date = null })
                            }
                        },
                    )
                    ChipMenu(
                        label = time?.formatHourMinute(use24Hour)
                            ?: stringResource(R.string.quick_add_no_time),
                        icon = Icons.Rounded.Schedule,
                        active = time != null,
                        onExpandedChange = { open -> openMenus += if (open) 1 else -1 },
                        options = buildList {
                            TIME_PRESETS.forEach { preset ->
                                add(ChipOption(preset.formatHourMinute(use24Hour)) { time = preset })
                            }
                            add(
                                ChipOption(stringResource(R.string.quick_add_pick_time)) {
                                    showTimePicker = true
                                },
                            )
                            if (time != null) {
                                add(ChipOption(stringResource(R.string.quick_add_no_time)) { time = null })
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

/** How long the keyboard has to stay away before the composer believes it. */
private const val SETTLE_MILLIS = 350L

/** One row in a [ChipMenu]. */
private data class ChipOption(val label: String, val onSelect: () -> Unit)

/**
 * A chip that opens a short menu.
 *
 * The menu is anchored to the chip rather than presented as a sheet, so the composer and the
 * half-typed title stay on screen behind it. Losing sight of what you were writing in order to
 * say "next Tuesday" is the thing this whole bar exists to avoid.
 */
@Composable
private fun ChipMenu(
    label: String,
    active: Boolean,
    options: List<ChipOption>,
    /** Reported so the composer knows the keyboard went for a reason. */
    onExpandedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    var expanded by remember { mutableStateOf(false) }

    fun setExpanded(open: Boolean) {
        if (expanded == open) return
        expanded = open
        onExpandedChange(open)
    }

    Box {
        QuickChip(label = label, active = active, icon = icon, onClick = { setExpanded(true) })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { setExpanded(false) },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = Radius.shapeMd,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        setExpanded(false)
                        option.onSelect()
                    },
                )
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

/** Times worth one tap. Anything else is two, through the picker. */
private val TIME_PRESETS = listOf(
    LocalTime.of(8, 0),
    LocalTime.of(9, 0),
    LocalTime.of(12, 0),
    LocalTime.of(18, 0),
    LocalTime.of(21, 0),
)

@Composable
private fun dateChipLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.date_today)
    today.plusDays(1) -> stringResource(R.string.date_tomorrow)
    else -> date.formatShort()
}
