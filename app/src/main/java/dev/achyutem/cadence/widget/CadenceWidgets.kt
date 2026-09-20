package dev.achyutem.cadence.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/**
 * One place that refreshes every widget.
 *
 * Called after any write that could change widget content, from the widgets themselves, and (in
 * a later phase) from the app after an edit. Centralised so nobody has to remember which widget
 * types exist when they add a new write path: adding a widget means adding one line here, not
 * auditing every call site.
 */
object CadenceWidgets {

    suspend fun updateAll(context: Context) {
        TodayWidget().updateAll(context)
        TodoListWidget().updateAll(context)
        HabitsWidget().updateAll(context)
        SingleHabitWidget().updateAll(context)
        HeatmapWidget().updateAll(context)
        NextTaskWidget().updateAll(context)
    }
}
