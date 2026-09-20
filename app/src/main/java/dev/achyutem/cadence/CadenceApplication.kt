package dev.achyutem.cadence

import android.app.Application
import dev.achyutem.cadence.core.common.AppContainer

/**
 * Startup does as close to nothing as possible: the container is constructed, but the database
 * and DataStore behind it are lazy, so no disk I/O happens before the first frame is drawn.
 */
class CadenceApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
