package il.co.tradesmanager

import android.app.Application
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TradesManagerApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        loadCatalogueIfNeeded()
    }

    /**
     * Reference data is refreshed when the shipped catalogue version moves
     * ahead of what this device has loaded — a store update or a downloaded
     * catalogue refresh. It only ever touches the read-only catalogue tables,
     * so it is safe to run at launch while the user is already on a screen.
     */
    private fun loadCatalogueIfNeeded() = appScope.launch {
        runCatching {
            val settings = container.settings.settings.first()
            val shipped = container.catalogSource.manifest().catalogVersion
            if (settings.seededCatalogVersion >= shipped) return@runCatching
            container.seeder.loadReferenceData()
            container.settings.setSeededCatalogVersion(shipped)
        }
    }
}
