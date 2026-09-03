package il.co.tradesmanager.data.repository

import il.co.tradesmanager.data.local.dao.AuditDao
import il.co.tradesmanager.data.local.entity.AuditLogEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Every change of consequence goes through here.
 *
 * Writing the log is not the caller's option: the repositories below call
 * [record] on the same code path that performs the change, so a mutation
 * without an audit row would have to be a deliberate edit to a repository, not
 * an oversight at a call site.
 */
class AuditTrail(private val dao: AuditDao) {

    object Action {
        const val CREATE = "CREATE"
        const val UPDATE = "UPDATE"
        const val DELETE = "DELETE"
        const val STOCK_CHANGE = "STOCK_CHANGE"
        const val SIGN_OFF = "SIGN_OFF"
        const val EXPORT = "EXPORT"
        const val PURGE = "PURGE"
    }

    suspend fun record(
        entityType: String,
        entityId: String,
        action: String,
        actorName: String,
        summary: String,
        payloadJson: String? = null,
    ) {
        dao.insert(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                entityType = entityType,
                entityId = entityId,
                action = action,
                actorId = null,
                actorName = actorName.ifBlank { "unknown" },
                summary = summary,
                payloadJson = payloadJson,
                occurredAt = System.currentTimeMillis(),
            ),
        )
    }

    fun recent(limit: Int = 500): Flow<List<AuditLogEntity>> = dao.observeRecent(limit)

    fun forEntity(type: String, id: String): Flow<List<AuditLogEntity>> = dao.observeFor(type, id)

    suspend fun exportSince(since: Long): List<AuditLogEntity> = dao.exportSince(since)

    /**
     * Applies a retention policy. The purge is itself logged, so a gap in the
     * trail is always explained by a row that says who removed what and when.
     */
    suspend fun purgeOlderThan(cutoff: Long, actorName: String): Int {
        val removed = dao.purgeOlderThan(cutoff)
        record(
            entityType = "audit_log",
            entityId = "retention",
            action = Action.PURGE,
            actorName = actorName,
            summary = "Purged $removed entries older than $cutoff",
        )
        return removed
    }
}
