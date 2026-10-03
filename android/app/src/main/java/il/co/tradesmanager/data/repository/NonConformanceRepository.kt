package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.evidence.NonConformances
import il.co.tradesmanager.data.local.dao.NonConformanceDao
import il.co.tradesmanager.data.local.entity.NonConformanceEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A job's non-conformance register, with the rules in
 * `core.evidence.NonConformances` applied on every write, under one lock for
 * the numbering and for something being decided or closed twice.
 */
class NonConformanceRepository(
    private val dao: NonConformanceDao,
    private val audit: AuditTrail,
) {

    enum class Refusal {
        NOT_ALLOWED,
        BLANK_ELEMENT,
        BLANK_REQUIREMENT,
        BLANK_FINDING,
        NO_ACCEPTOR,
        BLANK_CORRECTION,
        DUE_BEFORE_RAISED,
        ALREADY_DECIDED,
        NOT_DECIDED,
        BLANK_VERIFICATION,
        ALREADY_CLOSED,
        UNKNOWN,
    }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    private val lock = Mutex()

    /** Every job's rows, recent first, for the search box. */
    suspend fun all(): List<NonConformanceEntity> = dao.all()

    fun observeForProject(projectId: String): Flow<List<NonConformanceEntity>> = dao.observeForProject(projectId)

    suspend fun raise(
        role: Role,
        projectId: String,
        element: String,
        requirement: String,
        finding: String,
        foundBy: NonConformances.FoundBy,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<NonConformanceEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        NonConformances.raiseRefusal(element, requirement, finding)?.let { return@withLock Result.failure(Refused(it.asRefusal())) }
        runCatching {
            val report = NonConformanceEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                reference = NonConformances.reference(dao.countForProject(projectId)),
                element = element.trim(),
                requirement = requirement.trim(),
                finding = finding.trim(),
                foundBy = foundBy.name,
                raisedAt = now,
                raisedByName = byName,
            )
            dao.upsert(report)
            audit.record(
                ENTITY, report.id, AuditTrail.Action.CREATE, byName,
                Summary.of(Summaries.NCR_RAISED, report.reference, report.element),
            )
            report
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** What is to be done about it. Once. */
    suspend fun decide(
        role: Role,
        reportId: String,
        disposition: NonConformances.Disposition,
        acceptedBy: String,
        correction: String,
        dueOn: LocalDate?,
        byName: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Result<NonConformanceEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val report = dao.report(reportId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        val raisedOn = Instant.ofEpochMilli(report.raisedAt).atZone(zone).toLocalDate()
        NonConformances.decideRefusal(
            disposition, acceptedBy, correction, dueOn, raisedOn,
            decided = report.decidedAt != null,
            closed = report.closedAt != null,
        )?.let { return@withLock Result.failure(Refused(it.asRefusal())) }
        val decided = report.copy(
            disposition = disposition.name,
            acceptedBy = acceptedBy.trim().ifBlank { null },
            correction = correction.trim().ifBlank { null },
            dueOnDay = dueOn?.toEpochDay(),
            decidedAt = now,
            decidedByName = byName,
        )
        runCatching {
            dao.upsert(decided)
            audit.record(
                ENTITY, report.id, AuditTrail.Action.UPDATE, byName,
                Summary.of(Summaries.NCR_DECIDED, report.reference, Summary.nest(dispositionKey(disposition))),
            )
            decided
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** Closed with how the result was checked. Once. */
    suspend fun close(
        role: Role,
        reportId: String,
        verification: String,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<NonConformanceEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val report = dao.report(reportId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        NonConformances.closeRefusal(report.decidedAt != null, verification, report.closedAt != null)?.let {
            return@withLock Result.failure(Refused(it.asRefusal()))
        }
        val closed = report.copy(verification = verification.trim(), closedAt = now, closedByName = byName)
        runCatching {
            dao.upsert(closed)
            audit.record(ENTITY, report.id, AuditTrail.Action.UPDATE, byName, Summary.of(Summaries.NCR_CLOSED, report.reference))
            closed
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    private fun NonConformances.Refusal.asRefusal(): Refusal = when (this) {
        NonConformances.Refusal.BLANK_ELEMENT -> Refusal.BLANK_ELEMENT
        NonConformances.Refusal.BLANK_REQUIREMENT -> Refusal.BLANK_REQUIREMENT
        NonConformances.Refusal.BLANK_FINDING -> Refusal.BLANK_FINDING
        NonConformances.Refusal.NO_ACCEPTOR -> Refusal.NO_ACCEPTOR
        NonConformances.Refusal.BLANK_CORRECTION -> Refusal.BLANK_CORRECTION
        NonConformances.Refusal.DUE_BEFORE_RAISED -> Refusal.DUE_BEFORE_RAISED
        NonConformances.Refusal.ALREADY_DECIDED -> Refusal.ALREADY_DECIDED
        NonConformances.Refusal.NOT_DECIDED -> Refusal.NOT_DECIDED
        NonConformances.Refusal.BLANK_VERIFICATION -> Refusal.BLANK_VERIFICATION
        NonConformances.Refusal.ALREADY_CLOSED -> Refusal.ALREADY_CLOSED
    }

    companion object {
        /** The quality record is the site's record, kept by whoever keeps it. */
        fun mayWrite(role: Role): Boolean = role.canWrite(Lens.EVIDENCE)

        fun mayRead(role: Role): Boolean = role.canRead(Lens.EVIDENCE)

        /** The interface string for each disposition, nested into the audit summary so the trail reads it in the reader's language. */
        fun dispositionKey(disposition: NonConformances.Disposition): String = when (disposition) {
            NonConformances.Disposition.REWORK -> "ncr_disp_rework"
            NonConformances.Disposition.REPAIR -> "ncr_disp_repair"
            NonConformances.Disposition.ACCEPT_AS_IS -> "ncr_disp_accept_as_is"
            NonConformances.Disposition.REMOVE -> "ncr_disp_remove"
        }

        private const val ENTITY = "non_conformance"
    }
}
