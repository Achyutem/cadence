package dev.achyutem.cadence.core.common

import android.content.Context
import dev.achyutem.cadence.core.database.CadenceDatabase
import dev.achyutem.cadence.core.datastore.SettingsRepository
import dev.achyutem.cadence.domain.backup.BackupEngine
import dev.achyutem.cadence.core.time.CadenceClock
import dev.achyutem.cadence.core.time.SystemCadenceClock

/**
 * Manual dependency container.
 *
 * Cadence has one process, one database and a handful of long-lived singletons. Hilt would add a
 * compiler plugin, a second annotation processor and a layer of indirection to solve a problem
 * this app does not have — the brief explicitly rules out "elaborate dependency injection for
 * trivial objects".
 *
 * What matters for testability is that nothing constructs its own dependencies: every ViewModel
 * takes what it needs as constructor parameters, and tests pass fakes directly without needing
 * the container at all.
 *
 * The database is created lazily so that app startup does not open SQLite on the main thread
 * before the first frame.
 */
class AppContainer(
    private val context: Context,
    val clock: CadenceClock = SystemCadenceClock,
) {
    val database: CadenceDatabase by lazy { CadenceDatabase.build(context) }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }

    val backupEngine: BackupEngine by lazy {
        BackupEngine(
            database = database,
            settings = settingsRepository,
            clock = clock,
            appVersion = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            }.getOrDefault(""),
        )
    }

    /**
     * Refreshes every home-screen widget.
     *
     * Called by ViewModels after a write. Without this the widgets only update when *they* are
     * the thing that changed — complete a task in the app and the home screen would keep showing
     * it outstanding until the next 30-minute refresh, which is exactly the kind of staleness
     * that makes people stop trusting a widget.
     *
     * Failures are swallowed: a widget that cannot be updated must never take down the write that
     * triggered it.
     */
    suspend fun refreshWidgets() {
        runCatching { dev.achyutem.cadence.widget.CadenceWidgets.updateAll(context) }
            .onFailure { android.util.Log.w("CadenceWidgets", "widget refresh failed", it) }
    }

    val taskDao get() = database.taskDao()
    val habitDao get() = database.habitDao()
    val checkInDao get() = database.checkInDao()
    val recurrenceDao get() = database.recurrenceDao()
    val tagDao get() = database.tagDao()
}
