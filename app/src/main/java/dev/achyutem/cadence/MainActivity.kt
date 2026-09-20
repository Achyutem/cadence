package dev.achyutem.cadence

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.achyutem.cadence.core.datastore.UserPreferences
import dev.achyutem.cadence.core.designsystem.theme.CadenceTheme
import dev.achyutem.cadence.navigation.CadenceApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as CadenceApplication).container

        // Preferences are read as a state flow with the last-known value retained, so a theme
        // change never flashes the default theme on the way through.
        val preferencesFlow = container.settingsRepository.preferences.stateIn(
            scope = lifecycleScope,
            started = SharingStarted.Eagerly,
            initialValue = UserPreferences.Default,
        )

        // Roll the reminder horizon forward on every launch. Alarms are only laid down a week
        // ahead, so something has to extend the window; the app opening is the most reliable
        // trigger there is, and the reboot receiver covers the rest.
        lifecycleScope.launch {
            container.reminderScheduler.rescheduleAll()
        }

        setContent {
            val preferences by preferencesFlow.collectAsStateWithLifecycle()
            CadenceTheme(preferences = preferences) {
                CadenceApp()
            }
        }
    }
}
