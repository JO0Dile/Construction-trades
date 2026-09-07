package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.security.AuditChain
import il.co.tradesmanager.data.local.dao.AuditDao
import il.co.tradesmanager.data.local.entity.AuditLogEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    /**
     * Appending is serialised.
     *
     * Every entry is hashed against the one before it, so appending is
     * read-then-write and two callers racing would both read the same
     * predecessor. The loser's link would be wrong and the trail would fail to
     * verify — reported as tampering, on a log nobody had touched, which is
     * the worst possible false alarm for a feature whose only job is to be
     * believed.
     *
     * A mutex rather than a transaction because the read and the write are two
     * DAO calls and the ordering matters more than the isolation. It holds
     * within this process, which is where the writers are: one app, one
     * database.
     */
    private val appending = Mutex()

    suspend fun record(
        entityType: String,
        entityId: String,
        action: String,
        actorName: String,
        summary: String,
        payloadJson: String? = null,
    ) = appending.withLock {
        val previous = dao.lastEntry()
        val sequence = (previous?.sequence ?: 0L) + 1L
        // The genesis value when there is nothing before, and when what came
        // before predates the chain: an unhashed row cannot be linked to, and
        // saying so plainly beats linking to an empty string as though it were
        // a hash.
        val previousHash = previous?.hash?.takeIf { it.isNotBlank() } ?: AuditChain.GENESIS
        val actor = actorName.ifBlank { "unknown" }
        val occurredAt = System.currentTimeMillis()
        dao.insert(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                entityType = entityType,
                entityId = entityId,
                action = action,
                actorId = null,
                actorName = actor,
                summary = summary,
                payloadJson = payloadJson,
                occurredAt = occurredAt,
                sequence = sequence,
                previousHash = previousHash,
                hash = AuditChain.link(
                    previousHash = previousHash,
                    sequence = sequence,
                    entityType = entityType,
                    entityId = entityId,
                    action = action,
                    actorId = null,
                    actorName = actor,
                    summary = summary,
                    payloadJson = payloadJson,
                    occurredAt = occurredAt,
                ),
            ),
        )
    }

    /**
     * Whether the trail still adds up.
     *
     * Checks the most recent [window] entries. The answer is about that
     * window, and the screen that shows it says so rather than implying the
     * whole history was examined.
     */
    suspend fun verify(window: Int = VERIFY_WINDOW): AuditChain.Verdict =
        AuditChain.verify(dao.newestFirst(window).asReversed().map { it.asChainEntry() })

    fun recent(limit: Int = 500): Flow<List<AuditLogEntity>> = dao.observeRecent(limit)

    fun forEntity(type: String, id: String): Flow<List<AuditLogEntity>> = dao.observeFor(type, id)

    suspend fun exportSince(since: Long): List<AuditLogEntity> = dao.exportSince(since)

    /**
     * Applies a retention policy. The purge is itself logged, so a gap in the
     * trail is always explained by a row that says who removed what and when.
     */
    suspend fun purgeOlderThan(cutoff: Long, actorName: String): Int {
        val removed = dao.purgeOlderThan(cutoff)
        // What the trail now starts at. Recorded because a purge and a
        // deletion look identical from inside the chain — the first surviving
        // entry points at something that is gone either way. Naming the edge
        // turns the one unverifiable break into a documented one.
        val edge = dao.oldestEntry()
        record(
            entityType = "audit_log",
            entityId = "retention",
            action = Action.PURGE,
            actorName = actorName,
            summary = "Purged $removed entries older than $cutoff",
            payloadJson = "{\"removed\":$removed,\"cutoff\":$cutoff," +
                "\"edgeSequence\":${edge?.sequence ?: 0}," +
                "\"edgePreviousHash\":\"${edge?.previousHash.orEmpty()}\"}",
        )
        return removed
    }

    companion object {

        /**
         * How many entries a verification looks at.
         *
         * Enough to cover a long job's recent history without reading a table
         * that grows for years into memory to answer one question on a
         * settings screen.
         */
        const val VERIFY_WINDOW = 2_000

        /** The entity as the chain rule sees it. */
        fun AuditLogEntity.asChainEntry() = AuditChain.Entry(
            id = id,
            sequence = sequence,
            entityType = entityType,
            entityId = entityId,
            action = action,
            actorId = actorId,
            actorName = actorName,
            summary = summary,
            payloadJson = payloadJson,
            occurredAt = occurredAt,
            previousHash = previousHash,
            hash = hash,
        )
    }
}
