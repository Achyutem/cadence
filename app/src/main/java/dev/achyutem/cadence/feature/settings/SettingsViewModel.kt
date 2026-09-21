package dev.achyutem.cadence.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.achyutem.cadence.core.common.cadenceViewModelFactory
import dev.achyutem.cadence.core.datastore.AccentColor
import dev.achyutem.cadence.core.datastore.SettingsRepository
import dev.achyutem.cadence.core.datastore.ThemeMode
import dev.achyutem.cadence.core.datastore.UserPreferences
import dev.achyutem.cadence.core.datastore.NotificationSound
import dev.achyutem.cadence.domain.backup.BackupEngine
import dev.achyutem.cadence.domain.backup.BackupResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek

/** Transient result of an import/export, shown once and then dismissed. */
sealed interface DataTransferState {
    data object Idle : DataTransferState
    data object Working : DataTransferState
    data class Success(val message: String) : DataTransferState
    data class Error(val message: String) : DataTransferState
}

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val backup: BackupEngine,
    private val reminders: dev.achyutem.cadence.core.notifications.ReminderScheduler,
) : ViewModel() {

    /** Whether the system will honour an exact alarm right now. */
    fun canScheduleExact(): Boolean = reminders.canScheduleExact()

    /**
     * Rebuild the reminder horizon.
     *
     * Called after any setting that changes what should fire or when: quiet hours, and after the
     * notification permission is granted.
     */
    fun rescheduleReminders() = viewModelScope.launch { reminders.rescheduleAll() }

    /**
     * Change the reminder tone.
     *
     * Rebuilds the notification channels immediately rather than waiting for the next reminder
     * to be scheduled, so opening system notification settings right afterwards shows the sound
     * that was just chosen.
     */
    fun setNotificationSound(sound: NotificationSound) = viewModelScope.launch {
        settings.setNotificationSound(sound)
        // The scheduler rebuilds the channels as it goes, and the channel id depends on the
        // sound, so already-scheduled alarms have to be rebuilt too or they would fire into
        // the channel that was just deleted.
        reminders.rescheduleAll()
    }

    fun setQuietHoursEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setQuietHoursEnabled(enabled)
        reminders.rescheduleAll()
    }

    fun setQuietHours(start: java.time.LocalTime, end: java.time.LocalTime) = viewModelScope.launch {
        settings.setQuietHours(start, end)
        reminders.rescheduleAll()
    }

    val preferences: StateFlow<UserPreferences> = settings.preferences.stateIn(
        scope = viewModelScope,
        // Keeps the flow alive briefly across configuration changes so rotating the device does
        // not drop and re-read DataStore.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPreferences.Default,
    )

    private val _transfer = MutableStateFlow<DataTransferState>(DataTransferState.Idle)
    val transfer: StateFlow<DataTransferState> = _transfer.asStateFlow()

    fun setDisplayName(name: String) = viewModelScope.launch { settings.setDisplayName(name) }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }

    fun setAccent(accent: AccentColor) = viewModelScope.launch { settings.setAccentColor(accent) }

    fun setDynamicColor(enabled: Boolean) =
        viewModelScope.launch { settings.setUseDynamicColor(enabled) }

    fun setButtonShape(shape: dev.achyutem.cadence.core.datastore.ButtonShape) =
        viewModelScope.launch { settings.setButtonShape(shape) }

    fun setReducedMotion(enabled: Boolean) =
        viewModelScope.launch { settings.setReducedMotion(enabled) }

    fun setWeekStart(day: DayOfWeek) = viewModelScope.launch { settings.setWeekStartsOn(day) }

    fun suggestedFileName(): String = backup.suggestedFileName()

    fun export(context: Context, uri: Uri) = viewModelScope.launch {
        _transfer.value = DataTransferState.Working
        val stream = runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
        if (stream == null) {
            _transfer.value = DataTransferState.Error("Could not open that file for writing.")
            return@launch
        }
        _transfer.value = when (val result = backup.export(stream)) {
            is BackupResult.ExportSuccess ->
                DataTransferState.Success("Exported ${result.records} records.")
            is BackupResult.Failure -> DataTransferState.Error(result.reason)
            else -> DataTransferState.Idle
        }
    }

    fun import(context: Context, uri: Uri) = viewModelScope.launch {
        _transfer.value = DataTransferState.Working
        val stream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
        if (stream == null) {
            _transfer.value = DataTransferState.Error("Could not open that file.")
            return@launch
        }
        _transfer.value = when (val result = backup.import(stream)) {
            is BackupResult.ImportSuccess ->
                DataTransferState.Success("Restored ${result.summary.records} records.")
            is BackupResult.Failure -> DataTransferState.Error(result.reason)
            else -> DataTransferState.Idle
        }
    }

    fun dismissTransfer() {
        _transfer.value = DataTransferState.Idle
    }

    companion object {
        val Factory = cadenceViewModelFactory { container ->
            SettingsViewModel(
                container.settingsRepository,
                container.backupEngine,
                container.reminderScheduler,
            )
        }
    }
}
