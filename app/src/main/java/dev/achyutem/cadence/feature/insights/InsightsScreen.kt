package dev.achyutem.cadence.feature.insights

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.designsystem.component.CadenceCard
import dev.achyutem.cadence.core.designsystem.component.CadenceIconButton
import dev.achyutem.cadence.core.designsystem.component.FullScreenEmptyState
import dev.achyutem.cadence.core.designsystem.token.Spacing
import dev.achyutem.cadence.domain.insights.Insight
import dev.achyutem.cadence.domain.insights.InsightConfidence

/**
 * Insights.
 *
 * Each card states the observation and, underneath, exactly what it was computed from. The basis
 * line is not a footnote: an insight the user cannot check is a claim they have to take on faith,
 * and a personal analytics feature that asks for faith is the wrong feature.
 *
 * The empty state says how much data there is rather than apologising, because "not yet" is the
 * honest answer for most of the first month and there is nothing to fix.
 */
@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InsightsViewModel = viewModel(factory = InsightsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CadenceIconButton(
                icon = Icons.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                onClick = onBack,
            )
            Text(
                text = stringResource(R.string.insights_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Spacing.xxs),
            )
        }

        if (state.loading) return@Column

        if (state.insights.isEmpty()) {
            FullScreenEmptyState(
                title = stringResource(R.string.insights_empty_title),
                description = pluralStringResource(
                    R.plurals.insights_empty_description,
                    state.daysAnalysed,
                    state.daysAnalysed,
                ),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.screenGutter,
                end = Spacing.screenGutter,
                top = Spacing.sm,
                bottom = Spacing.dockClearance,
            ),
        ) {
            item(key = "intro") {
                Text(
                    text = pluralStringResource(
                        R.plurals.insights_intro,
                        state.daysAnalysed,
                        state.daysAnalysed,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.md))
            }

            items(state.insights, key = { it.id }) { insight ->
                InsightCard(insight)
                Spacer(Modifier.height(Spacing.xs))
            }

            item(key = "footer") {
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.insights_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InsightCard(insight: Insight) {
    CadenceCard {
        Column {
            Text(
                text = stringResource(
                    when (insight.confidence) {
                        InsightConfidence.OBSERVATION -> R.string.insights_observation
                        InsightConfidence.PATTERN -> R.string.insights_pattern
                    }
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                text = insight.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = insight.basis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
