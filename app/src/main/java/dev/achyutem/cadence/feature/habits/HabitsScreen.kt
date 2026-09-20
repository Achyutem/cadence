package dev.achyutem.cadence.feature.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.designsystem.component.EmptyState
import dev.achyutem.cadence.core.designsystem.theme.CadencePreviewTheme
import dev.achyutem.cadence.core.designsystem.token.Spacing

/**
 * Habits. Phase 3 fills this in with the habit list, metrics and streaks; Phase 0 establishes
 * the screen frame and its place in the graph.
 */
@Composable
fun HabitsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = Spacing.screenGutter, vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.habits_title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyState(
                title = stringResource(R.string.habits_empty_title),
                description = stringResource(R.string.habits_empty_description),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HabitsPreview() = CadencePreviewTheme { HabitsScreen() }
