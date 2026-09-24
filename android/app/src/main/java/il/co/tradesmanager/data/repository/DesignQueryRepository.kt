package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.work.Queries
import il.co.tradesmanager.data.local.dao.DesignQueryDao
import il.co.tradesmanager.data.local.entity.DesignQueryEntity
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A job's questions to its designers, with the rules in `core.work.Queries`
 * applied on the write. Numbering is under a lock, so two questions asked in
 * the same second cannot both be Q-004.
 */
class DesignQueryRepository(
    private val dao: DesignQueryDao,
    private val audit: AuditTrail,
) {

    enum class Refusal {
        NOT_ALLOWED,
        BLANK_QUESTION,
        NOBODY_ASKED,
        NEEDED_BY_TOO_EARLY,
        BLANK_ANSWER,
        ALREADY_ANSWERED,
        UNKNOWN,
    }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    private val numbering = Mutex()

    /** Every job's rows, recent first, for the search box. */
    suspend fun all(): List<DesignQueryEntity> = dao.all()

    fun observeForProject(projectId: String): Flow<List<DesignQueryEntity>> = dao.observeForProject(projectId)

    suspend fun raise(
        role: Role,
        projectId: String,
        question: String,
        askedOf: String,
        drawingNumber: String?,
        neededBy: Long?,
        byName: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Result<DesignQueryEntity> = numbering.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        Queries.raiseRefusal(question, askedOf, now, neededBy, zone)?.let {
            return@withLock Result.failure(Refused(it.asRefusal()))
        }
        runCatching {
            val query = DesignQueryEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                reference = Queries.reference(dao.countForProject(projectId)),
                question = question.trim(),
                askedOf = askedOf.trim(),
                drawingNumber = drawingNumber?.trim()?.ifBlank { null },
                askedAt = now,
                neededBy = neededBy,
                askedByName = byName,
            )
            dao.upsert(query)
            audit.record(
                ENTITY, query.id, AuditTrail.Action.CREATE, byName,
                Summary.of(Summaries.QUERY_RAISED, query.reference, query.askedOf),
            )
            query
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    suspend fun answer(
        role: Role,
        queryId: String,
        answer: String,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<DesignQueryEntity> {
        if (!mayWrite(role)) return Result.failure(Refused(Refusal.NOT_ALLOWED))
        val query = dao.query(queryId) ?: return Result.failure(Refused(Refusal.UNKNOWN))
        Queries.answerRefusal(answer, query.answeredAt)?.let { return Result.failure(Refused(it.asRefusal())) }
        val answered = query.copy(answer = answer.trim(), answeredAt = now, answerRecordedByName = byName)
        return runCatching {
            dao.upsert(answered)
            audit.record(
                ENTITY, query.id, AuditTrail.Action.UPDATE, byName,
                Summary.of(Summaries.QUERY_ANSWERED, query.reference),
            )
            answered
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    private fun Queries.Refusal.asRefusal(): Refusal = when (this) {
        Queries.Refusal.BLANK_QUESTION -> Refusal.BLANK_QUESTION
        Queries.Refusal.NOBODY_ASKED -> Refusal.NOBODY_ASKED
        Queries.Refusal.NEEDED_BY_TOO_EARLY -> Refusal.NEEDED_BY_TOO_EARLY
        Queries.Refusal.BLANK_ANSWER -> Refusal.BLANK_ANSWER
        Queries.Refusal.ALREADY_ANSWERED -> Refusal.ALREADY_ANSWERED
    }

    companion object {
        /** Whoever plans the work asks the questions about it. */
        fun mayWrite(role: Role): Boolean = role.canWrite(Lens.PLAN)

        private const val ENTITY = "design_query"
    }
}
