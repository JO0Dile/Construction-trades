package il.co.tradesmanager

import android.app.Application
import il.co.tradesmanager.data.repository.AuditTrail
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
        }.onFailure { failure ->
            // It used to be swallowed. A device that runs out of room part way
            // through then opens on an empty inventory with nothing anywhere
            // saying why — not to the user, not to whoever takes the call.
            // The version is deliberately not advanced above, so the next
            // launch tries again; this records that a launch did not manage
            // it, which is the only way anybody finds out it happened twice.
            //
            // Recording is itself best effort: if the database is the thing
            // that failed, this will fail too, and a crash in Application
            // .onCreate would take the app down over a diagnostic.
            runCatching {
                container.auditTrail.record(
                    entityType = "catalogue",
                    entityId = "seed",
                    action = AuditTrail.Action.CREATE,
                    actorName = "system",
                    summary = "Catalogue load failed: " +
                        (failure.message ?: failure::class.java.simpleName),
                )
            }
        }
    }
}
