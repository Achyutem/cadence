package dev.achyutem.cadence.core.common

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.achyutem.cadence.CadenceApplication

/** The [AppContainer] belonging to the running application, from inside a ViewModel factory. */
val CreationExtras.appContainer: AppContainer
    get() = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CadenceApplication).container

/**
 * Small helper so each ViewModel can declare its own factory in one line:
 *
 * ```
 * companion object {
 *     val Factory = cadenceViewModelFactory { SettingsViewModel(it.settingsRepository) }
 * }
 * ```
 */
inline fun <reified VM : androidx.lifecycle.ViewModel> cadenceViewModelFactory(
    crossinline create: (AppContainer) -> VM,
): ViewModelProvider.Factory = viewModelFactory {
    initializer { create(appContainer) }
}
