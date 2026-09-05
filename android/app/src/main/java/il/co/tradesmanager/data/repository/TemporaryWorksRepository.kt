package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.safety.TemporaryWorks
import il.co.tradesmanager.data.local.dao.TemporaryWorksDao
import il.co.tradesmanager.data.local.entity.TemporaryWorksEntity
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * The temporary works register.
 *
 * Each step is its own call rather than one save, because each is somebody
 * putting their name to something: checked, erected, inspected, loaded,
 * released, struck. A form with six date fields would let one person fill in
 * all six on the day of the accident.
 */
class TemporaryWorksRepository(
    private val dao: TemporaryWorksDao,
    private val audit: AuditTrail,
) {

    enum class Kind { PROPPING, FORMWORK, SHORING, FACADE_RETENTION, EDGE_PROTECTION, OTHER }

    fun observeForProject(projectId: String): Flow<List<TemporaryWorksEntity>> =
        dao.observeForProject(projectId)

    fun observe(id: String): Flow<TemporaryWorksEntity?> = dao.observe(id)

    suspend fun create(
        projectId: String,
        description: String,
        kind: Kind,
        category: TemporaryWorks.CheckCategory,
        actorName: String,
    ): TemporaryWorksEntity {
        val now = System.currentTimeMillis()
        val item = TemporaryWorksEntity(
            id = UUID.randomUUID().toString(),
            reference = String.format(Locale.ROOT, "TW-%03d", dao.count() + 1),
            projectId = projectId,
            description = description.trim(),
            kind = kind.name,
            checkCategory = category.name,
            designReference = null,
            designerName = null,
            checkerName = null,
            checkedAt = null,
            erectedAt = null,
            inspectedAt = null,
            inspectedByName = null,
            loadedAt = null,
            supportsPourId = null,
            supportsPourAt = null,
            minimumStrikingDays = TemporaryWorks.DEFAULT_STRIKING_DAYS,
            releasedByName = null,
            releasedAt = null,
            struckAt = null,
            notes = null,
            createdByName = actorName,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(item)
        audit.record(
            ITEM, item.id, AuditTrail.Action.CREATE, actorName,
            "${item.reference} ${item.description}",
        )
        return item
    }

    /** The design and its details. Editable until it has been checked. */
    suspend fun setDesign(
        item: TemporaryWorksEntity,
        designReference: String?,
        designerName: String?,
        minimumStrikingDays: Long,
        supportsPourId: String?,
        supportsPourAt: Long?,
        actorName: String,
    ) {
        dao.upsert(
            item.copy(
                designReference = designReference?.trim()?.takeIf { it.isNotEmpty() },
                designerName = designerName?.trim()?.takeIf { it.isNotEmpty() },
                minimumStrikingDays = minimumStrikingDays.coerceAtLeast(0L),
                supportsPourId = supportsPourId,
                supportsPourAt = supportsPourAt,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        audit.record(ITEM, item.id, AuditTrail.Action.UPDATE, actorName, item.reference)
    }

    /**
     * Somebody has checked the design.
     *
     * The checker's name is theirs, not the signed-in user's, because the
     * person checking a category-three design is often not the person holding
     * the phone — and recording the wrong name is worse than recording none.
     */
    suspend fun markChecked(item: TemporaryWorksEntity, checkerName: String, actorName: String) {
        val now = System.currentTimeMillis()
        dao.upsert(
            item.copy(
                checkerName = checkerName.trim(),
                checkedAt = now,
                updatedAt = now,
            ),
        )
        audit.record(
            ITEM, item.id, AuditTrail.Action.SIGN_OFF, actorName,
            "${item.reference} checked by ${checkerName.trim()}",
        )
    }

    suspend fun markErected(item: TemporaryWorksEntity, actorName: String) {
        val now = System.currentTimeMillis()
        dao.upsert(item.copy(erectedAt = now, updatedAt = now))
        audit.record(ITEM, item.id, AuditTrail.Action.UPDATE, actorName, "${item.reference} erected")
    }

    /**
     * Somebody has compared what was built against what was drawn.
     *
     * Separate from erecting, and by design not the same call. The commonest
     * way temporary works go wrong is not a bad design but a good design built
     * differently, with nobody holding the two up against each other.
     */
    suspend fun markInspected(item: TemporaryWorksEntity, actorName: String) {
        val now = System.currentTimeMillis()
        dao.upsert(
            item.copy(inspectedAt = now, inspectedByName = actorName, updatedAt = now),
        )
        audit.record(
            ITEM, item.id, AuditTrail.Action.SIGN_OFF, actorName,
            "${item.reference} inspected against the design",
        )
    }

    suspend fun markLoaded(item: TemporaryWorksEntity, actorName: String) {
        val now = System.currentTimeMillis()
        dao.upsert(item.copy(loadedAt = now, updatedAt = now))
        audit.record(ITEM, item.id, AuditTrail.Action.UPDATE, actorName, "${item.reference} loaded")
    }

    /**
     * The permit to strike: somebody with the authority says it may come down.
     *
     * Kept apart from actually striking it, and recorded with a name, because
     * this is the decision that drops slabs. Afterwards the question is always
     * who released it, and the honest answer is usually that nobody remembers.
     */
    suspend fun release(item: TemporaryWorksEntity, actorName: String) {
        val now = System.currentTimeMillis()
        dao.upsert(
            item.copy(releasedByName = actorName, releasedAt = now, updatedAt = now),
        )
        audit.record(
            ITEM, item.id, AuditTrail.Action.SIGN_OFF, actorName,
            "${item.reference} released for striking",
        )
    }

    suspend fun markStruck(item: TemporaryWorksEntity, actorName: String) {
        val now = System.currentTimeMillis()
        dao.upsert(item.copy(struckAt = now, updatedAt = now))
        audit.record(ITEM, item.id, AuditTrail.Action.UPDATE, actorName, "${item.reference} struck")
    }

    suspend fun remove(item: TemporaryWorksEntity, actorName: String) {
        dao.delete(item)
        audit.record(ITEM, item.id, AuditTrail.Action.DELETE, actorName, item.reference)
    }

    private companion object {
        const val ITEM = "temporary_works"
    }
}
