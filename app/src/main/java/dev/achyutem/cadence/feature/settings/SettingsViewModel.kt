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
) : ViewModel() {

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
            SettingsViewModel(container.settingsRepository, container.backupEngine)
        }
    }
}
