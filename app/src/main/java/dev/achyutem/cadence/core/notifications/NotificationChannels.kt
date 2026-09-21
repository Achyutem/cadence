package dev.achyutem.cadence.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import dev.achyutem.cadence.R
import dev.achyutem.cadence.core.datastore.NotificationSound
import kotlin.math.absoluteValue

/**
 * Notification channels.
 *
 * Two of them, not one. Separate channels let the user silence habit nudges without losing task
 * reminders, and that choice belongs to them in system settings rather than to an in-app toggle
 * we invented. Once a channel exists its importance is the user's to change, so the values here
 * are only the starting point.
 *
 * ### Why the ids carry a token
 *
 * **A channel's sound cannot be changed after it is created.** Android freezes sound, vibration
 * and importance at creation time precisely so that an app cannot turn itself back up behind the
 * user's back; `createNotificationChannel` on an existing id updates the name and description and
 * silently ignores everything else.
 *
 * So changing the sound means retiring the channel and making a new one. Each id ends in a short
 * token derived from the chosen sound, and [ensureCreated] deletes every Cadence channel that is
 * not one of the current pair.
 *
 * The cost is real and worth stating: recreating a channel drops whatever the user changed about
 * it in system settings. That is the trade Android imposes for an in-app sound picker, and it is
 * why the picker says so.
 */
object NotificationChannels {

    private const val TASKS_PREFIX = "cadence.tasks"
    private const val HABITS_PREFIX = "cadence.habits"

    /**
     * Channels earlier versions created.
     *
     * The unsuffixed ids predate the sound setting, and `cadence.checkin` belonged to the daily
     * check-in, which is gone. A channel outlives the feature that made it, so leaving these
     * behind would put unused rows in the user's system notification settings forever.
     */
    private val RETIRED = listOf("cadence.tasks", "cadence.habits", "cadence.checkin")

    fun tasksChannel(sound: NotificationSound): String = "$TASKS_PREFIX.${sound.token()}"

    fun habitsChannel(sound: NotificationSound): String = "$HABITS_PREFIX.${sound.token()}"

    fun ensureCreated(context: Context, sound: NotificationSound) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val tasks = tasksChannel(sound)
        val habits = habitsChannel(sound)

        manager.createNotificationChannel(
            NotificationChannel(
                tasks,
                context.getString(R.string.channel_tasks),
                // Timed tasks are the one thing here worth interrupting for.
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_tasks_description)
                applySound(sound)
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                habits,
                context.getString(R.string.channel_habits),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.channel_habits_description)
                applySound(sound)
            }
        )

        // Everything else of ours goes, including the pair for a sound the user has moved on
        // from. Left alone they accumulate: one dead row per sound ever tried.
        val keep = setOf(tasks, habits)
        manager.notificationChannels
            .map { it.id }
            .filter { it.startsWith("cadence.") && it !in keep }
            .plus(RETIRED)
            .distinct()
            .forEach(manager::deleteNotificationChannel)
    }

    private fun NotificationChannel.applySound(sound: NotificationSound) {
        when (sound) {
            // Leave the channel on whatever the system considers the default notification tone.
            NotificationSound.SystemDefault -> Unit
            NotificationSound.Silent -> setSound(null, null)
            is NotificationSound.Custom -> setSound(
                sound.uri.toUri(),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
    }

    /**
     * A short, stable id fragment for a sound.
     *
     * Hashed rather than embedded: a ringtone URI is long, contains characters a channel id
     * should not, and would put the user's media library layout into a string the system keeps.
     */
    private fun NotificationSound.token(): String = when (this) {
        NotificationSound.SystemDefault -> "default"
        NotificationSound.Silent -> "silent"
        is NotificationSound.Custom -> uri.hashCode().absoluteValue.toString(16)
    }
}
