package dev.achyutem.cadence.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import dev.achyutem.cadence.R

/**
 * Notification channels.
 *
 * Two of them, not one. Separate channels let the user silence habit nudges without losing task
 * reminders, and that choice belongs to them in system settings rather than to an in-app toggle
 * we invented. Once a channel exists its importance is the user's to change, so the values here
 * are only the starting point.
 */
object NotificationChannels {

    const val TASKS = "cadence.tasks"
    const val HABITS = "cadence.habits"

    /**
     * A channel an earlier version created, deleted on launch.
     *
     * The daily check-in it belonged to is gone. A channel outlives the feature that made it, so
     * leaving this behind would put a permanently silent, permanently unused row in the user's
     * system notification settings.
     */
    private const val RETIRED_CHECK_IN = "cadence.checkin"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                TASKS,
                context.getString(R.string.channel_tasks),
                // Timed tasks are the one thing here worth interrupting for.
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_tasks_description)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                HABITS,
                context.getString(R.string.channel_habits),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_habits_description)
            }
        )

        manager.deleteNotificationChannel(RETIRED_CHECK_IN)
    }
}
