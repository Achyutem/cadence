package dev.achyutem.cadence.feature.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.designsystem.component.ButtonTone
import dev.achyutem.cadence.core.designsystem.component.CadenceCard
import dev.achyutem.cadence.core.designsystem.component.CadenceIconButton
import dev.achyutem.cadence.core.designsystem.component.Heatmap
import dev.achyutem.cadence.core.designsystem.component.HeatmapLegend
import dev.achyutem.cadence.core.designsystem.component.SectionHeader
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.theme.tabularFigures
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.domain.habit.trim
import dev.achyutem.cadence.domain.recurrence.describe

/**
 * Habit detail: the streak, the heatmap and the numbers behind them.
 *
 * Streaks are stated, never celebrated. There is no flame animation, no "keep it up!", no
 * milestone. A streak is a fact about the data, and the moment it becomes a reward it starts
 * distorting the behaviour it was supposed to measure.
 */
@Composable
fun HabitDetailScreen(
    habitId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HabitDetailViewModel = viewModel(
        key = "habit-$habitId",
        factory = HabitDetailViewModel.factory(habitId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CadenceIconButton(
                icon = Icons.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            CadenceIconButton(
                icon = Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.action_delete),
                onClick = { viewModel.delete(onBack) },
                tone = ButtonTone.Danger,
            )
        }

        val habit = state.habit
        if (state.loading || habit == null) return@Column

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenGutter),
        ) {
            Text(
                text = habit.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = state.rule?.describe() ?: stringResource(R.string.habit_schedule_daily),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(Spacing.lg))

            // The streak, stated plainly.
            Text(
                text = pluralStringResource(
                    R.plurals.habit_streak,
                    state.currentStreak,
                    state.currentStreak,
                ),
                style = MaterialTheme.typography.displaySmall.tabularFigures,
                color = if (state.currentStreak > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            Spacer(Modifier.height(Spacing.lg))

            SectionHeader(title = stringResource(R.string.habit_activity))
            Spacer(Modifier.height(Spacing.xs))
            CadenceCard(contentPadding = Spacing.sm) {
                Column {
                    // Six months does not fit on a phone. The heatmap handles its own horizontal
                    // scrolling so that the weekday gutter stays pinned while the grid moves.
                    Heatmap(
                        startDate = state.heatmapStart,
                        endDate = state.heatmapEnd,
                        levels = state.heatmapLevels,
                        weekStart = state.preferences.weekStartsOn,
                        markedDate = state.today,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    HeatmapLegend(modifier = Modifier.align(Alignment.End))
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            SectionHeader(title = stringResource(R.string.habit_this_month))
            Spacer(Modifier.height(Spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                StatTile(
                    label = stringResource(R.string.habit_consistency),
                    value = state.monthRate.percent?.let { "$it%" }
                        ?: stringResource(R.string.habit_no_data_short),
                    caption = if (state.monthRate.hasData) {
                        "${state.monthRate.completed} / ${state.monthRate.scheduled} days"
                    } else {
                        stringResource(R.string.habit_no_data)
                    },
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = stringResource(R.string.habit_best_streak),
                    value = state.bestStreak.toString(),
                    caption = "days",
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.averageValue != null) {
                Spacer(Modifier.height(Spacing.xs))
                StatTile(
                    label = stringResource(R.string.habit_average),
                    value = state.averageValue!!.trim(),
                    caption = habit.unit ?: "",
                )
            }

            Spacer(Modifier.height(Spacing.dockClearance))
        }
    }
}

/** A single number with a label above and a caption below. */
@Composable
private fun StatTile(
    label: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    CadenceCard(modifier = modifier, contentPadding = Spacing.sm) {
        Column {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.tabularFigures,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
