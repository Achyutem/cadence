package dev.achyutem.cadence.core.notifications

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Whether Cadence may post a notification, and what to do when it may not.
 *
 * ### Two different questions
 *
 * "Is `POST_NOTIFICATIONS` granted" and "will a notification actually appear" are not the same
 * question, and answering the first one was a bug on both ends of the version range.
 *
 * `POST_NOTIFICATIONS` is a runtime permission on Android 13 and a **string that means nothing**
 * before it. Asking `checkSelfPermission` about it on Android 12 returns `PERMISSION_DENIED`,
 * because the platform has no such permission to grant, which would silence every reminder on
 * exactly the devices that never needed to be asked. Answering "granted" instead fixes that, and
 * introduces the opposite error: a user on Android 12 who has switched Cadence's notifications
 * off in system settings is told everything is fine, and reminders vanish with nothing on screen
 * to explain why.
 *
 * [isEnabled] asks the question that matters on every version. The runtime permission only
 * decides *how* to ask for it back.
 */
object NotificationPermission {

    /** True when a notification posted right now would actually appear. */
    fun isEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Whether there is an in-app prompt, as opposed to a trip to system settings.
     *
     * Only Android 13 and up has a runtime permission to request. Below that, notifications are
     * turned back on in system settings and nothing the app does can prompt for it.
     */
    val isRequestable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * The permission to pass to a request launcher.
     *
     * Named rather than inlined so the one place that references the Android 13 constant is this
     * file, next to the version check that makes referencing it safe.
     */
    const val NAME: String = "android.permission.POST_NOTIFICATIONS"

    /** Cadence's own page in system notification settings, for the versions with no prompt. */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
