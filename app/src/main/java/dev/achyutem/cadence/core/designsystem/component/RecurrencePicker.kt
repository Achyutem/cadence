package dev.achyutem.cadence.core.designsystem.component

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.database.entity.RecurrenceRuleEntity
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.token.Borders
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.core.time.narrowLabel
import dev.achyutem.cadence.core.time.weekdayOrder
import dev.achyutem.cadence.domain.recurrence.RecurrenceEngine
import dev.achyutem.cadence.domain.recurrence.RecurrencePresets
import dev.achyutem.cadence.domain.recurrence.describe
import java.time.DayOfWeek
import java.time.LocalDate

/** The repeat patterns offered directly, in the order people reach for them. */
private enum class RepeatChoice { NONE, DAILY, WEEKDAYS, WEEKLY, MONTHLY_DAY, MONTHLY_WEEKDAY, YEARLY }

/**
 * Choosing a recurrence.
 *
 * Presets first, with the weekday grid appearing only when "specific days" is chosen. The full
 * vocabulary in `RecurrenceRuleEntity` can express things nobody picks from a list (every 3 weeks
 * on Tuesday and Friday, ending after 12 occurrences), and exposing all of it up front would make
 * the common case of "every day" slower to reach than it is to type.
 *
 * The sheet never builds a rule by hand: every option goes through [RecurrencePresets], so a
 * weekly rule can never end up carrying a `dayOfMonth` that the engine would silently ignore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurrencePickerSheet(
    current: RecurrenceRuleEntity?,
    anchor: LocalDate,
    weekStart: DayOfWeek,
    onDismiss: () -> Unit,
    onSelect: (RecurrenceRuleEntity?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var choice by remember { mutableStateOf(current.toChoice()) }
    var days by remember {
        mutableStateOf(current?.daysOfWeek ?: setOf(anchor.dayOfWeek))
    }
    var interval by remember { mutableStateOf(current?.interval ?: 1) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = Radius.shapeXl,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.recurrence_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.md))

            RepeatChoice.entries.forEach { option ->
                ChoiceRow(
                    label = stringResource(option.labelRes()),
                    selected = option == choice,
                    onClick = { choice = option },
                )
            }

            if (choice == RepeatChoice.WEEKLY) {
                Spacer(Modifier.height(Spacing.sm))
                WeekdayGrid(
                    weekStart = weekStart,
                    selected = days,
                    onToggle = { day ->
                        // Never allow an empty set: a weekly rule with no days matches nothing,
                        // which is silent and confusing rather than an error.
                        days = if (day in days && days.size > 1) days - day else days + day
                    },
                )
            }

            if (choice != RepeatChoice.NONE) {
                Spacer(Modifier.height(Spacing.sm))
                IntervalRow(
                    interval = interval,
                    unitRes = choice.unitRes(),
                    onChange = { interval = it.coerceIn(1, 12) },
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                CadenceButton(
                    text = stringResource(R.string.action_cancel),
                    tone = ButtonTone.Secondary,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                CadenceButton(
                    text = stringResource(R.string.action_done),
                    tone = ButtonTone.Primary,
                    onClick = {
                        onSelect(buildRule(choice, anchor, days, interval))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

private fun buildRule(
    choice: RepeatChoice,
    anchor: LocalDate,
    days: Set<DayOfWeek>,
    interval: Int,
): RecurrenceRuleEntity? = when (choice) {
    RepeatChoice.NONE -> null
    RepeatChoice.DAILY -> RecurrencePresets.daily(anchor, interval)
    RepeatChoice.WEEKDAYS -> RecurrencePresets.weekdays(anchor)
    RepeatChoice.WEEKLY -> RecurrencePresets.weekly(anchor, days, interval)
    RepeatChoice.MONTHLY_DAY -> RecurrencePresets.monthlyOnDay(anchor, anchor.dayOfMonth, interval)
    RepeatChoice.MONTHLY_WEEKDAY -> RecurrencePresets.monthlyOnWeekday(
        anchor = anchor,
        week = if (anchor.plusDays(7).month != anchor.month) {
            RecurrenceEngine.LAST_WEEK_OF_MONTH
        } else {
            (anchor.dayOfMonth - 1) / 7 + 1
        },
        weekday = anchor.dayOfWeek,
        interval = interval,
    )
    RepeatChoice.YEARLY -> RecurrencePresets.yearly(anchor, interval)
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radius.shapeSm)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                )
                .border(
                    Borders.hairline,
                    if (selected) MaterialTheme.colorScheme.primary else CadenceTheme.colors.borderStrong,
                    CircleShape,
                ),
        )
        Spacer(Modifier.size(Spacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun WeekdayGrid(
    weekStart: DayOfWeek,
    selected: Set<DayOfWeek>,
    onToggle: (DayOfWeek) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        weekdayOrder(weekStart).forEach { day ->
            val isOn = day in selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(Radius.shapeSm)
                    .background(
                        if (isOn) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    )
                    .border(
                        Borders.hairline,
                        if (isOn) MaterialTheme.colorScheme.primaryContainer else CadenceTheme.colors.border,
                        Radius.shapeSm,
                    )
                    .clickable { onToggle(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day.narrowLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isOn) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun IntervalRow(interval: Int, unitRes: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.recurrence_every),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(Spacing.xs))
        CadenceIconButton(
            icon = Icons.Rounded.Remove,
            contentDescription = stringResource(R.string.recurrence_less),
            onClick = { onChange(interval - 1) },
            enabled = interval > 1,
        )
        Text(
            text = interval.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        CadenceIconButton(
            icon = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.recurrence_more),
            onClick = { onChange(interval + 1) },
        )
        Spacer(Modifier.size(Spacing.xxs))
        Text(
            text = stringResource(unitRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun RecurrenceRuleEntity?.toChoice(): RepeatChoice {
    if (this == null) return RepeatChoice.NONE
    return when (frequency) {
        dev.achyutem.cadence.core.database.entity.RecurrenceFrequency.DAILY -> RepeatChoice.DAILY
        dev.achyutem.cadence.core.database.entity.RecurrenceFrequency.WEEKLY ->
            if (daysOfWeek?.size == 5 && daysOfWeek?.none { it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY } == true) {
                RepeatChoice.WEEKDAYS
            } else {
                RepeatChoice.WEEKLY
            }
        dev.achyutem.cadence.core.database.entity.RecurrenceFrequency.MONTHLY_BY_DAY -> RepeatChoice.MONTHLY_DAY
        dev.achyutem.cadence.core.database.entity.RecurrenceFrequency.MONTHLY_BY_WEEKDAY -> RepeatChoice.MONTHLY_WEEKDAY
        dev.achyutem.cadence.core.database.entity.RecurrenceFrequency.YEARLY -> RepeatChoice.YEARLY
    }
}

private fun RepeatChoice.labelRes(): Int = when (this) {
    RepeatChoice.NONE -> R.string.recurrence_none
    RepeatChoice.DAILY -> R.string.recurrence_daily
    RepeatChoice.WEEKDAYS -> R.string.recurrence_weekdays
    RepeatChoice.WEEKLY -> R.string.recurrence_weekly
    RepeatChoice.MONTHLY_DAY -> R.string.recurrence_monthly_day
    RepeatChoice.MONTHLY_WEEKDAY -> R.string.recurrence_monthly_weekday
    RepeatChoice.YEARLY -> R.string.recurrence_yearly
}

private fun RepeatChoice.unitRes(): Int = when (this) {
    RepeatChoice.DAILY -> R.string.recurrence_unit_days
    RepeatChoice.WEEKDAYS, RepeatChoice.WEEKLY -> R.string.recurrence_unit_weeks
    RepeatChoice.MONTHLY_DAY, RepeatChoice.MONTHLY_WEEKDAY -> R.string.recurrence_unit_months
    RepeatChoice.YEARLY -> R.string.recurrence_unit_years
    RepeatChoice.NONE -> R.string.recurrence_unit_days
}

/** The one-line summary shown on a row that has a rule attached. */
@Composable
fun recurrenceSummary(rule: RecurrenceRuleEntity?): String =
    rule?.describe() ?: stringResource(R.string.recurrence_none)
