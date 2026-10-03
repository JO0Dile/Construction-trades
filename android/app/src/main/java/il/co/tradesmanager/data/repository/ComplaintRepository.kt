package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.evidence.Complaints
import il.co.tradesmanager.data.local.dao.ComplaintDao
import il.co.tradesmanager.data.local.entity.ComplaintEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A job's complaints register, with the rules in `core.evidence.Complaints`
 * applied on the write, under one lock for the numbering and the answer.
 */
class ComplaintRepository(
    private val dao: ComplaintDao,
    private val audit: AuditTrail,
) {

    enum class Refusal {
        NOT_ALLOWED,
        BLANK_FROM,
        BLANK_DESCRIPTION,
        RECEIVED_IN_FUTURE,
        BLANK_RESPONSE,
        ALREADY_ANSWERED,
        UNKNOWN,
    }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    private val lock = Mutex()

    /** Every job's rows, recent first, for the search box. */
    suspend fun all(): List<ComplaintEntity> = dao.all()

    fun observeForProject(projectId: String): Flow<List<ComplaintEntity>> = dao.observeForProject(projectId)

    suspend fun receive(
        role: Role,
        projectId: String,
        fromWhom: String,
        contact: String?,
        channel: Complaints.Channel,
        subject: Complaints.Subject,
        description: String,
        receivedAt: Long,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<ComplaintEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        Complaints.receiveRefusal(fromWhom, description, receivedAt, now)?.let {
            return@withLock Result.failure(Refused(it.asRefusal()))
        }
        runCatching {
            val complaint = ComplaintEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                reference = Complaints.reference(dao.countForProject(projectId)),
                fromWhom = fromWhom.trim(),
                contact = contact?.trim()?.ifBlank { null },
                channel = channel.name,
                subject = subject.name,
                description = description.trim(),
                receivedAt = receivedAt,
                receivedByName = byName,
            )
            dao.upsert(complaint)
            audit.record(
                ENTITY, complaint.id, AuditTrail.Action.CREATE, byName,
                Summary.of(Summaries.CP_RECEIVED, complaint.reference, complaint.fromWhom),
            )
            complaint
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** What was done, and what they were told. Written once. */
    suspend fun answer(
        role: Role,
        complaintId: String,
        response: String,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<ComplaintEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val complaint = dao.complaint(complaintId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        Complaints.answerRefusal(response, complaint.answeredAt)?.let {
            return@withLock Result.failure(Refused(it.asRefusal()))
        }
        val answered = complaint.copy(response = response.trim(), answeredAt = now, answeredByName = byName)
        runCatching {
            dao.upsert(answered)
            audit.record(
                ENTITY, complaint.id, AuditTrail.Action.UPDATE, byName,
                Summary.of(Summaries.CP_ANSWERED, complaint.reference),
            )
            answered
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    private fun Complaints.Refusal.asRefusal(): Refusal = when (this) {
        Complaints.Refusal.BLANK_FROM -> Refusal.BLANK_FROM
        Complaints.Refusal.BLANK_DESCRIPTION -> Refusal.BLANK_DESCRIPTION
        Complaints.Refusal.RECEIVED_IN_FUTURE -> Refusal.RECEIVED_IN_FUTURE
        Complaints.Refusal.BLANK_RESPONSE -> Refusal.BLANK_RESPONSE
        Complaints.Refusal.ALREADY_ANSWERED -> Refusal.ALREADY_ANSWERED
    }

    companion object {
        /** Whoever keeps the site's record keeps what the neighbours said about it. */
        fun mayWrite(role: Role): Boolean = role.canWrite(Lens.EVIDENCE)

        fun mayRead(role: Role): Boolean = role.canRead(Lens.EVIDENCE)

        private const val ENTITY = "complaint"
    }
}
