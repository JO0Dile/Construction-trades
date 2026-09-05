package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.people.Expiry
import il.co.tradesmanager.data.catalog.CatalogSource
import il.co.tradesmanager.data.catalog.ProjectKind
import il.co.tradesmanager.data.local.dao.CertificationDao
import il.co.tradesmanager.data.local.entity.CertificationEntity
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The tickets people hold, and when they run out.
 *
 * Part of the People lens. A lapsed work-at-height or site-manager certificate
 * is a legal exposure for the company and a danger for the person holding it,
 * and nobody discovers it on the day — they discover it when an inspector
 * asks. So the app knows the dates and says something first.
 */
class CertificationRepository(
    private val dao: CertificationDao,
    private val source: CatalogSource,
    private val audit: AuditTrail,
) {

    fun observeForAccount(accountId: String): Flow<List<CertificationEntity>> =
        dao.observeForAccount(accountId)

    /** Account id -> that person's tickets, for a list that badges every row. */
    fun observeByAccount(): Flow<Map<String, List<CertificationEntity>>> =
        dao.observeAll().map { all -> all.groupBy { it.accountId } }

    /**
     * Anything lapsed or lapsing inside the renewal window, soonest first.
     * The window is computed here rather than in SQL so it moves with the day
     * rather than with whenever the query was written.
     */
    fun observeNeedingAttention(now: () -> Long = System::currentTimeMillis): Flow<List<CertificationEntity>> =
        dao.observeExpiringBefore(now() + Expiry.WARNING_DAYS * DAY_MILLIS)

    /** Suggested titles, in the language on screen. Content, not code. */
    suspend fun suggestedKinds(): List<ProjectKind> = withContext(Dispatchers.IO) {
        runCatching { source.manifest().certificationKinds }.getOrDefault(emptyList())
    }

    suspend fun add(
        accountId: String,
        title: String,
        reference: String?,
        expiresOn: Long?,
        actorName: String,
    ): CertificationEntity {
        val certification = CertificationEntity(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
            title = title.trim(),
            reference = reference?.trim()?.takeIf { it.isNotEmpty() },
            issuedOn = null,
            expiresOn = expiresOn,
            notes = null,
            createdAt = System.currentTimeMillis(),
        )
        dao.upsert(certification)
        audit.record(ENTITY, certification.id, AuditTrail.Action.CREATE, actorName, certification.title)
        return certification
    }

    suspend fun remove(certification: CertificationEntity, actorName: String) {
        dao.delete(certification)
        audit.record(ENTITY, certification.id, AuditTrail.Action.DELETE, actorName, certification.title)
    }

    private companion object {
        const val ENTITY = "certification"
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
