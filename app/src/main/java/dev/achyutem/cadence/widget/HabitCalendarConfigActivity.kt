package dev.achyutem.cadence.widget

import androidx.glance.GlanceId

/**
 * Configuration for the habit calendar widget.
 *
 * Subclasses the single-habit picker rather than duplicating it: the only difference is that this
 * one also asks for week or month, and both write into their own widget's Glance state.
 */
class HabitCalendarConfigActivity : SingleHabitConfigActivity() {

    override val offersPeriod: Boolean = true

    override suspend fun apply(glanceId: GlanceId, habitId: Long, period: HabitCalendarPeriod) {
        HabitCalendarWidget.configure(this, glanceId, habitId, period)
    }
}
