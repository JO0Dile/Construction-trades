package il.co.tradesmanager.di

import android.content.Context
import il.co.tradesmanager.data.catalog.CatalogSeeder
import il.co.tradesmanager.data.catalog.CatalogSource
import il.co.tradesmanager.data.local.AppDatabase
import il.co.tradesmanager.data.local.DatabaseFactory
import il.co.tradesmanager.data.repository.AccountRepository
import il.co.tradesmanager.data.repository.AuditTrail
import il.co.tradesmanager.data.repository.InventoryRepository
import il.co.tradesmanager.data.repository.CertificationRepository
import il.co.tradesmanager.data.repository.ConcreteRepository
import il.co.tradesmanager.data.repository.DailyLogRepository
import il.co.tradesmanager.data.repository.EquipmentRepository
import il.co.tradesmanager.data.repository.EvidenceRepository
import il.co.tradesmanager.data.repository.MembershipRepository
import il.co.tradesmanager.data.repository.MoneyRepository
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.PurchasingRepository
import il.co.tradesmanager.data.repository.ProjectRepository
import il.co.tradesmanager.data.repository.SafetyRepository
import il.co.tradesmanager.data.repository.ScaffoldRepository
import il.co.tradesmanager.data.repository.ScheduleRepository
import il.co.tradesmanager.data.repository.SessionRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    /** Which companies somebody belongs to, and their role in each. */
    val memberships = MembershipRepository(database.membershipDao(), auditTrail)

    val accounts = AccountRepository(database.accountDao(), memberships, auditTrail)

    /** Who is signed in, and which lenses that opens. */
    val session = SessionRepository(settings, accounts, memberships)

    val inventory = InventoryRepository(database.inventoryDao(), auditTrail)

    /**
     * The company whose work is on screen, as a flow.
     *
     * Derived from the session rather than read from settings directly: a
     * preference naming a firm somebody has been taken off must not scope
     * anything to it. distinctUntilChanged so an unrelated settings change
     * does not re-run every job query in the app.
     */
    private val activeCompanyId: kotlinx.coroutines.flow.Flow<String?> = session.state
        .map { (it as? SessionRepository.State.SignedIn)?.active?.companyId }
        .distinctUntilChanged()

    val projects = ProjectRepository(
        dao = database.projectDao(),
        source = catalogSource,
        audit = auditTrail,
        activeCompanyId = activeCompanyId,
    )

    val photos = PhotoRepository(appContext, database.photoDao(), auditTrail)



    val certifications =
        CertificationRepository(database.certificationDao(), catalogSource, auditTrail)

    val equipment = EquipmentRepository(database.equipmentDao(), auditTrail)

    /** Concrete arriving, and the clock on every load. */
    val concrete = ConcreteRepository(database.concreteDao(), auditTrail)

    val scaffolds = ScaffoldRepository(database.scaffoldDao(), auditTrail)

    /** The daily site log — the יומן עבודה a site manager has to keep. */
    val dailyLogs = DailyLogRepository(database.dailyLogDao(), auditTrail)

    /** Toolbox talks and permits to work. */
    val evidence = EvidenceRepository(database.evidenceDao(), auditTrail)

    val purchasing = PurchasingRepository(
        dao = database.purchasingDao(),
        projects = database.projectDao(),
        inventory = inventory,
        audit = auditTrail,
    )

    val money = MoneyRepository(database.moneyDao(), equipment, purchasing, auditTrail)

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
