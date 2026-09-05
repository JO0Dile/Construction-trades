package il.co.tradesmanager.di

import android.content.Context
import il.co.tradesmanager.data.catalog.CatalogSeeder
import il.co.tradesmanager.data.catalog.CatalogSource
import il.co.tradesmanager.data.local.AppDatabase
import il.co.tradesmanager.data.local.DatabaseFactory
import il.co.tradesmanager.data.repository.AuditTrail
import il.co.tradesmanager.data.repository.InventoryRepository
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.ProjectRepository
import il.co.tradesmanager.data.repository.SafetyRepository
import il.co.tradesmanager.data.repository.ScheduleRepository
import il.co.tradesmanager.data.repository.SettingsRepository
import il.co.tradesmanager.data.repository.TradeRepository
import il.co.tradesmanager.data.sync.NoOpSyncEngine
import il.co.tradesmanager.data.update.UpdateRepository
import il.co.tradesmanager.data.sync.SyncEngine

/**
 * Hand-rolled dependency container.
 *
 * The graph is a dozen objects with no cycles and no scopes beyond "lives as
 * long as the process", so a code-generating injector would add a build step
 * and a layer of indirection to solve a problem this app does not have. Tests
 * construct the pieces they need directly.
 */
class AppContainer(context: Context, encryptDatabase: Boolean = true) {

    private val appContext = context.applicationContext

    private val databaseResult = DatabaseFactory.create(appContext, encryptDatabase)

    /** False when the device could not load SQLCipher; surfaced in Settings. */
    val databaseIsEncrypted: Boolean = databaseResult.encrypted

    val database: AppDatabase = databaseResult.database

    val catalogSource = CatalogSource(appContext)

    val settings = SettingsRepository(appContext)

    val updates = UpdateRepository(appContext)

    val auditTrail = AuditTrail(database.auditDao())

    val inventory = InventoryRepository(database.inventoryDao(), auditTrail)

    val projects = ProjectRepository(database.projectDao(), catalogSource, auditTrail)

    val photos = PhotoRepository(appContext, database.photoDao(), auditTrail)

    val schedule = ScheduleRepository(database.scheduleDao(), auditTrail)

    val safety = SafetyRepository(database.safetyDao(), database.catalogDao(), auditTrail)

    val catalogDao = database.catalogDao()

    val trades = TradeRepository(catalogDao, auditTrail)

    val seeder = CatalogSeeder(
        source = catalogSource,
        catalogDao = catalogDao,
        inventoryDao = database.inventoryDao(),
        auditDao = database.auditDao(),
    )

    /**
     * Category -> icon name, read once from the shared catalogue. Small enough
     * (a few KB) that a lazy synchronous read costs nothing, and it must be
     * available synchronously because list rows draw with it.
     */
    /** Pictures shipped with the app, keyed by catalogue item id. */
    val bundledImages: Map<String, String> by lazy { catalogSource.bundledImages() }

    val categoryIcons: Map<String, String> by lazy {
        runCatching { catalogSource.manifest().categoryIcons }.getOrDefault(emptyMap())
    }

    val syncEngine: SyncEngine = NoOpSyncEngine()
}
