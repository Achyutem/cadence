package dev.achyutem.cadence.widget

import androidx.glance.GlanceId

/**
 * Pick which habit the Activity widget charts.
 *
 * It used to take the first active habit and give you no say, which is fine until you track more
 * than one thing, and a thirteen-week grid of the wrong habit is worse than no grid.
 *
 * Reuses [SingleHabitConfigActivity] wholesale: same list, same cancel-first contract, only the
 * write at the end differs. There is no week/month choice here, the Activity widget is always a
 * rolling thirteen weeks.
 */
class HeatmapConfigActivity : SingleHabitConfigActivity() {

    override suspend fun apply(glanceId: GlanceId, habitId: Long, period: HabitCalendarPeriod) {
        HeatmapWidget.configure(this, glanceId, habitId)
    }
}
