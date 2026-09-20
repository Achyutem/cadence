package dev.achyutem.cadence.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.achyutem.cadence.CadenceApplication
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.database.entity.HabitEntity
import dev.achyutem.cadence.core.datastore.UserPreferences
import dev.achyutem.cadence.core.designsystem.component.FullScreenEmptyState
import dev.achyutem.cadence.core.designsystem.component.SegmentedControl
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.core.designsystem.token.Borders
import dev.achyutem.cadence.core.designsystem.token.Radius
import dev.achyutem.cadence.core.designsystem.token.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Pick which habit a single-habit widget shows.
 *
 * ### The result is set to CANCELED first
 *
 * A widget configuration activity that finishes without `RESULT_OK` is expected to leave nothing
 * behind, and the launcher removes the pending widget. Setting the cancelled result up front means
 * backing out of this screen does exactly that, and only a deliberate choice places the widget.
 *
 * The widget itself still works without ever visiting this screen: it falls back to the first
 * habit due today. Configuration is how you point a *second* one somewhere else, not a gate in
 * front of the first.
 */
open class SingleHabitConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    /** Whether this configuration screen also offers a week/month choice. */
    protected open val offersPeriod: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val container = (application as CadenceApplication).container
        val habits = container.habitDao.observeActive()
            .stateIn(lifecycleScope, SharingStarted.Eagerly, emptyList())
        val preferences = container.settingsRepository.preferences
            .stateIn(lifecycleScope, SharingStarted.Eagerly, UserPreferences.Default)

        setContent {
            val list by habits.collectAsStateWithLifecycle()
            val prefs by preferences.collectAsStateWithLifecycle()

            CadenceTheme(preferences = prefs) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding(),
                ) {
                    Text(
                        text = stringResource(R.string.widget_config_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(
                            start = Spacing.screenGutter,
                            end = Spacing.screenGutter,
                            top = Spacing.xl,
                        ),
                    )
                    Spacer(Modifier.height(Spacing.md))

                    if (list.isEmpty()) {
                        FullScreenEmptyState(
                            title = stringResource(R.string.habits_empty_title),
                            description = stringResource(R.string.widget_config_empty),
                        )
                    } else {
                        var period by remember { mutableStateOf(HabitCalendarPeriod.MONTH) }
                        if (offersPeriod) {
                            SegmentedControl(
                                options = HabitCalendarPeriod.entries,
                                selected = period,
                                onSelect = { period = it },
                                label = {
                                    stringResource(
                                        if (it == HabitCalendarPeriod.WEEK) {
                                            R.string.widget_config_week
                                        } else {
                                            R.string.widget_config_month
                                        }
                                    )
                                },
                                modifier = Modifier.padding(horizontal = Spacing.screenGutter),
                            )
                            Spacer(Modifier.height(Spacing.md))
                        }
                        HabitChoices(habits = list) { habit -> choose(habit, period) }
                    }
                }
            }
        }
    }

    protected open suspend fun apply(glanceId: GlanceId, habitId: Long, period: HabitCalendarPeriod) {
        SingleHabitWidget.configure(this, glanceId, habitId)
    }

    private fun choose(habit: HabitEntity, period: HabitCalendarPeriod) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@SingleHabitConfigActivity)
                .getGlanceIdBy(appWidgetId)
            apply(glanceId, habit.id, period)
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }
}

@Composable
private fun HabitChoices(habits: List<HabitEntity>, onChoose: (HabitEntity) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Spacing.screenGutter, vertical = Spacing.xs),
    ) {
        items(habits, key = { it.id }) { habit ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xxs)
                    .clip(Radius.shapeLg)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(Borders.hairline, CadenceTheme.colors.border, Radius.shapeLg)
                    .clickable { onChoose(habit) }
                    .padding(Spacing.md),
            ) {
                Text(
                    text = habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                habit.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
