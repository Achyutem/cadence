package dev.achyutem.cadence.core.notifications

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Whether Cadence may post a notification.
 *
 * `POST_NOTIFICATIONS` is a runtime permission on Android 13 and a **string that means nothing**
 * before it. Asking `checkSelfPermission` about it on Android 12 returns `PERMISSION_DENIED`,
 * because the platform has no such permission to grant, and taking that answer at face value
 * would silence every reminder on exactly the devices that never needed to be asked.
 *
 * So the version check is the answer, not a guard around it: below 13, notifications are allowed
 * unless the user has turned the app off in system settings, which is not something an app gets
 * to inspect or prompt about.
 */
object NotificationPermission {

    /** True when a notification posted right now would actually appear. */
    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * The permission to pass to a request launcher.
     *
     * Named rather than inlined so the one place that references the Android 13 constant is this
     * file, next to the version check that makes referencing it safe.
     */
    const val NAME: String = "android.permission.POST_NOTIFICATIONS"
}
