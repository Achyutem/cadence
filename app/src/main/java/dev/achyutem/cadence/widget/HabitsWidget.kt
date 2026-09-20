package dev.achyutem.cadence.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height

/**
 * Today's habits, each with a stepper.
 *
 * This is the widget the brief's "tap to increment water" is about: six glasses over a day is six
 * taps on the home screen and zero app launches.
 */
class HabitsWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = context.appContainer().loadWidgetSnapshot()
        provideContent { Content(snapshot) }
    }

    @Composable
    private fun Content(snapshot: WidgetSnapshot) {
        val colors = snapshot.preferences.widgetColors()

        WidgetSurface(colors = colors) {
            WidgetHeader(
                title = "Habits",
                colors = colors,
                trailing = if (snapshot.habits.isEmpty()) null else {
                    "${snapshot.completedHabits}/${snapshot.habits.size}"
                },
            )
            Spacer(modifier = GlanceModifier.height(6.dp))

            if (snapshot.habits.isEmpty()) {
                WidgetEmpty("No habits due today", colors)
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(snapshot.habits, itemId = { it.id }) { habit ->
                        WidgetHabitRow(habit = habit, colors = colors)
                    }
                }
            }
        }
    }

    companion object {
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)
    }
}

class HabitsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitsWidget()
}
