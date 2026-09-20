package dev.achyutem.cadence.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.achyutem.cadence.CadenceApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Rebuilds the reminder horizon after any event that invalidates it.
 *
 * Alarms do not survive a reboot, and they are absolute instants that become wrong the moment the
 * clock or the time zone moves. All three cases are the same response: throw away the horizon and
 * compute it again from the database in the current zone.
 *
 * This is the other half of the reason reminders are stored as a local time rather than an
 * instant. Recomputing here is correct precisely because nothing absolute was ever persisted.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            -> Unit
            else -> return
        }

        val container = (context.applicationContext as? CadenceApplication)?.container ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                container.reminderScheduler.rescheduleAll()
                container.refreshWidgets()
            } finally {
                pending.finish()
            }
        }
    }
}
