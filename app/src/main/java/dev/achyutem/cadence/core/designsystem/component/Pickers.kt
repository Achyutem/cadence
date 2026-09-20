package dev.achyutem.cadence.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.token.Borders
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Date and time pickers.
 *
 * These wrap Material's own pickers rather than reimplementing them. A calendar grid and a clock
 * dial are large, fiddly, heavily accessibility-tested controls, and people already know how the
 * platform ones behave. The custom design language stops at the dialog's frame.
 *
 * The conversion at the boundary is the part worth care: Material's date picker speaks UTC epoch
 * millis, and Cadence speaks [LocalDate]. Converting through `ZoneOffset.UTC` rather than the
 * device zone is deliberate, because the picker's value is a calendar date with no time in it;
 * running it through a local zone is exactly how date pickers end up off by one day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CadenceDatePickerDialog(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onSelect: (LocalDate?) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli(),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            CadenceButton(
                text = stringResource(R.string.action_done),
                tone = ButtonTone.Primary,
                onClick = {
                    onSelect(
                        state.selectedDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                    )
                    onDismiss()
                },
            )
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                CadenceButton(
                    text = stringResource(R.string.picker_clear),
                    tone = ButtonTone.Ghost,
                    onClick = { onSelect(null); onDismiss() },
                )
                CadenceButton(
                    text = stringResource(R.string.action_cancel),
                    tone = ButtonTone.Ghost,
                    onClick = onDismiss,
                )
            }
        },
        colors = androidx.compose.material3.DatePickerDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CadenceTimePickerDialog(
    initial: LocalTime?,
    use24Hour: Boolean,
    onDismiss: () -> Unit,
    onSelect: (LocalTime?) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial?.hour ?: 9,
        initialMinute = initial?.minute ?: 0,
        is24Hour = use24Hour,
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(Radius.shapeXl)
                .background(MaterialTheme.colorScheme.surface)
                .border(Borders.hairline, CadenceTheme.colors.border, Radius.shapeXl)
                .padding(Spacing.lg),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.picker_time_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Spacing.md))
            TimePicker(state = state)
            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                CadenceButton(
                    text = stringResource(R.string.picker_clear),
                    tone = ButtonTone.Ghost,
                    onClick = { onSelect(null); onDismiss() },
                )
                Spacer(Modifier.weight(1f))
                CadenceButton(
                    text = stringResource(R.string.action_cancel),
                    tone = ButtonTone.Ghost,
                    onClick = onDismiss,
                )
                CadenceButton(
                    text = stringResource(R.string.action_done),
                    tone = ButtonTone.Primary,
                    onClick = {
                        onSelect(LocalTime.of(state.hour, state.minute))
                        onDismiss()
                    },
                )
            }
        }
    }
}
