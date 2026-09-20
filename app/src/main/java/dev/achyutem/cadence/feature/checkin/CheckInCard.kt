package dev.achyutem.cadence.feature.checkin

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.database.entity.DailyCheckInEntity
import dev.achyutem.cadence.core.designsystem.component.CadenceCard
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.token.Borders
import dev.achyutem.cadence.core.designsystem.token.Motion
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing

/**
 * The check-in card on Today.
 *
 * One row of five faces and one row of five energy levels. No sliders, no text field until asked
 * for, no "submit". The whole interaction is meant to cost one tap, because the moment it starts
 * feeling like a form it becomes another task, which is exactly what the brief rules out.
 *
 * Once something is recorded the card shrinks to a summary rather than disappearing, so the day's
 * answer stays visible and changeable without being a standing demand.
 */
@Composable
fun CheckInCard(
    entry: DailyCheckInEntity?,
    onMood: (Int) -> Unit,
    onEnergy: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    CadenceCard(modifier = modifier) {
        Column {
            Text(
                text = stringResource(
                    if (entry == null) R.string.checkin_prompt else R.string.checkin_recorded,
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.sm))

            MoodRow(selected = entry?.mood, onSelect = onMood)

            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.checkin_energy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.xxs))
            EnergyRow(selected = entry?.energy, onSelect = onEnergy)
        }
    }
}

/**
 * Faces rather than numbers.
 *
 * A 1..5 mood scale means nothing without a label, and labelling every point ("Very bad", "Bad")
 * makes a grid of judgements. Faces are read instantly and carry no score.
 */
@Composable
private fun MoodRow(selected: Int?, onSelect: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        (1..5).forEach { value ->
            val isSelected = selected == value
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.86f,
                animationSpec = spring(
                    dampingRatio = Motion.SPRING_DAMPING_BOUNCY,
                    stiffness = Motion.SPRING_STIFFNESS,
                ),
                label = "moodScale",
            )
            val background by animateColorAsState(
                targetValue = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                animationSpec = tween(CadenceTheme.duration(Motion.QUICK)),
                label = "moodBackground",
            )
            val label = stringResource(moodLabelRes(value))

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(background)
                    .border(
                        Borders.hairline,
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else CadenceTheme.colors.border,
                        CircleShape,
                    )
                    .clickable(role = Role.RadioButton) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(value)
                    }
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = moodFace(value),
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.scale(scale),
                )
            }
        }
    }
}

/** Energy as five rising bars: comparative, and no more precise than self-report deserves. */
@Composable
private fun EnergyRow(selected: Int?, onSelect: (Int) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
        verticalAlignment = Alignment.Bottom,
    ) {
        (1..5).forEach { value ->
            val isOn = selected != null && value <= selected
            val color by animateColorAsState(
                targetValue = if (isOn) {
                    MaterialTheme.colorScheme.primary
                } else {
                    CadenceTheme.colors.progressTrack
                },
                animationSpec = tween(CadenceTheme.duration(Motion.QUICK)),
                label = "energyBar",
            )
            val label = stringResource(R.string.checkin_energy_level, value)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height((14 + value * 5).dp)
                    .clip(Radius.shapeXs)
                    .background(color)
                    .clickable(role = Role.RadioButton) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(value)
                    }
                    .semantics { contentDescription = label },
            )
        }
    }
}

internal fun moodFace(value: Int): String = when (value) {
    1 -> "😞"
    2 -> "😕"
    3 -> "😐"
    4 -> "🙂"
    else -> "😄"
}

private fun moodLabelRes(value: Int): Int = when (value) {
    1 -> R.string.checkin_mood_1
    2 -> R.string.checkin_mood_2
    3 -> R.string.checkin_mood_3
    4 -> R.string.checkin_mood_4
    else -> R.string.checkin_mood_5
}
