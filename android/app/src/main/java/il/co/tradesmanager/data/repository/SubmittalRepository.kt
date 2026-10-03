package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.work.Submittals
import il.co.tradesmanager.data.local.dao.SubmittalDao
import il.co.tradesmanager.data.local.entity.SubmittalEntity
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A job's material submittals, with the rules in `core.work.Submittals`
 * applied on the write. Numbering is under a lock, so two submittals made in
 * the same second cannot both be MS-004.
 */
class SubmittalRepository(
    private val dao: SubmittalDao,
    private val audit: AuditTrail,
) {

    enum class Refusal {
        NOT_ALLOWED,
        BLANK_ITEM,
        NOBODY_ASKED,
        NEEDED_BY_TOO_EARLY,
        ALREADY_DECIDED,
        NO_REVIEWER,
        REJECTED_WITHOUT_REASON,
        NOTES_MISSING,
        NOT_REJECTED,
        ALREADY_RESUBMITTED,
        UNKNOWN,
    }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    private val numbering = Mutex()

    /** Every job's rows, recent first, for the search box. */
    suspend fun all(): List<SubmittalEntity> = dao.all()

    fun observeForProject(projectId: String): Flow<List<SubmittalEntity>> = dao.observeForProject(projectId)

    suspend fun submit(
        role: Role,
        projectId: String,
        item: String,
        supplier: String?,
        location: String?,
        submittedTo: String,
        neededBy: Long?,
        byName: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Result<SubmittalEntity> = numbering.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        Submittals.submitRefusal(item, submittedTo, now, neededBy, zone)?.let {
            return@withLock Result.failure(Refused(it.asRefusal()))
        }
        runCatching {
            val submittal = SubmittalEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                reference = Submittals.reference(dao.countFirstSubmissions(projectId)),
                revision = 0,
                item = item.trim(),
                supplier = supplier?.trim()?.ifBlank { null },
                location = location?.trim()?.ifBlank { null },
                submittedTo = submittedTo.trim(),
                submittedAt = now,
                neededBy = neededBy,
                submittedByName = byName,
            )
            dao.upsert(submittal)
            audit.record(
                ENTITY, submittal.id, AuditTrail.Action.CREATE, byName,
                Summary.of(Summaries.MS_SUBMITTED, submittal.reference, submittal.submittedTo),
            )
            submittal
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /**
     * The next go after a rejection: the same number, the next revision, to
     * the same person, with what is proposed now -- which may be a different
     * product, since that is often the point.
     */
    suspend fun resubmit(
        role: Role,
        rejectedId: String,
        item: String,
        neededBy: Long?,
        byName: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Result<SubmittalEntity> = numbering.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val rejected = dao.submittal(rejectedId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        Submittals.resubmitRefusal(Submittals.decisionOf(rejected.decision), dao.resubmissionCount(rejectedId) > 0)?.let {
            return@withLock Result.failure(Refused(it.asRefusal()))
        }
        Submittals.submitRefusal(item, rejected.submittedTo, now, neededBy, zone)?.let {
            return@withLock Result.failure(Refused(it.asRefusal()))
        }
        runCatching {
            val next = rejected.copy(
                id = UUID.randomUUID().toString(),
                revision = rejected.revision + 1,
                item = item.trim(),
                submittedAt = now,
                neededBy = neededBy,
                submittedByName = byName,
                resubmissionOf = rejected.id,
                decision = null,
                reviewerName = null,
                notes = null,
                decidedAt = null,
                decisionRecordedByName = null,
            )
            dao.upsert(next)
            audit.record(
                ENTITY, next.id, AuditTrail.Action.CREATE, byName,
                Summary.of(Summaries.MS_RESUBMITTED, next.reference, next.revision.toString()),
            )
            next
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** What the reviewer said. Written once; a rejection is followed by [resubmit], not an edit. */
    suspend fun decide(
        role: Role,
        submittalId: String,
        decision: Submittals.Decision,
        reviewerName: String,
        notes: String,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<SubmittalEntity> = numbering.withLock {
        // Under the lock, so two answers typed in the same second cannot both land.
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val submittal = dao.submittal(submittalId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        Submittals.decisionRefusal(decision, reviewerName, notes, submittal.decidedAt)?.let {
            return@withLock Result.failure(Refused(it.asRefusal()))
        }
        val decided = submittal.copy(
            decision = decision.name,
            reviewerName = reviewerName.trim(),
            notes = notes.trim().ifBlank { null },
            decidedAt = now,
            decisionRecordedByName = byName,
        )
        val phrase = when (decision) {
            Submittals.Decision.APPROVED -> Summaries.MS_APPROVED
            Submittals.Decision.APPROVED_AS_NOTED -> Summaries.MS_APPROVED_AS_NOTED
            Submittals.Decision.REJECTED -> Summaries.MS_REJECTED
        }
        runCatching {
            dao.upsert(decided)
            audit.record(
                ENTITY, submittal.id, AuditTrail.Action.UPDATE, byName,
                Summary.of(phrase, submittal.reference, decided.reviewerName.orEmpty()),
            )
            decided
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    private fun Submittals.Refusal.asRefusal(): Refusal = when (this) {
        Submittals.Refusal.BLANK_ITEM -> Refusal.BLANK_ITEM
        Submittals.Refusal.NOBODY_ASKED -> Refusal.NOBODY_ASKED
        Submittals.Refusal.NEEDED_BY_TOO_EARLY -> Refusal.NEEDED_BY_TOO_EARLY
        Submittals.Refusal.ALREADY_DECIDED -> Refusal.ALREADY_DECIDED
        Submittals.Refusal.NO_REVIEWER -> Refusal.NO_REVIEWER
        Submittals.Refusal.REJECTED_WITHOUT_REASON -> Refusal.REJECTED_WITHOUT_REASON
        Submittals.Refusal.NOTES_MISSING -> Refusal.NOTES_MISSING
        Submittals.Refusal.NOT_REJECTED -> Refusal.NOT_REJECTED
        Submittals.Refusal.ALREADY_RESUBMITTED -> Refusal.ALREADY_RESUBMITTED
    }

    companion object {
        /** Whoever plans the work sends its materials for approval. */
        fun mayWrite(role: Role): Boolean = role.canWrite(Lens.PLAN)

        fun mayRead(role: Role): Boolean = role.canRead(Lens.PLAN)

        private const val ENTITY = "submittal"
    }
}
