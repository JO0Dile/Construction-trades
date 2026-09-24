package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.evidence.Inspections
import il.co.tradesmanager.data.local.dao.InspectionDao
import il.co.tradesmanager.data.local.entity.InspectionEntity
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A job's inspection requests, with the rules in `core.evidence.Inspections`
 * applied on the write. Numbering is under a lock, so two requests made in
 * the same second cannot both be IR-004.
 *
 * [pourOf] finds a pour's job and reference, which is all this needs of the
 * concrete register: enough to refuse clearing a pour on another job, and to
 * name the pour in the audit trail.
 */
class InspectionRepository(
    private val dao: InspectionDao,
    private val audit: AuditTrail,
    private val pourOf: suspend (pourId: String) -> PourRef?,
) {

    data class PourRef(val projectId: String, val reference: String)

    enum class Refusal {
        NOT_ALLOWED,
        BLANK_ELEMENT,
        NOBODY_ASKED,
        WANTED_TOO_EARLY,
        ALREADY_DECIDED,
        NO_INSPECTOR,
        FAILED_WITHOUT_REASON,
        COMMENTS_MISSING,
        NOT_FAILED,
        ALREADY_REINSPECTED,
        NOT_PASSED,
        NOT_FOR_A_POUR,
        ALREADY_CLEARED_A_POUR,
        OTHER_JOB,
        UNKNOWN,
    }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    private val numbering = Mutex()

    /** Every job's rows, recent first, for the search box. */
    suspend fun all(): List<InspectionEntity> = dao.all()

    fun observeForProject(projectId: String): Flow<List<InspectionEntity>> = dao.observeForProject(projectId)

    suspend fun request(
        role: Role,
        projectId: String,
        kind: Inspections.Kind,
        element: String,
        requestedOf: String,
        wantedOn: Long?,
        byName: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Result<InspectionEntity> = numbering.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        Inspections.requestRefusal(element, requestedOf, now, wantedOn, zone)?.let {
            return@withLock Result.failure(Refused(it.asRefusal()))
        }
        runCatching {
            val inspection = InspectionEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                reference = Inspections.reference(dao.countForProject(projectId)),
                kind = kind.name,
                element = element.trim(),
                requestedOf = requestedOf.trim(),
                requestedAt = now,
                wantedOn = wantedOn,
                requestedByName = byName,
            )
            dao.upsert(inspection)
            audit.record(
                ENTITY, inspection.id, AuditTrail.Action.CREATE, byName,
                Summary.of(Summaries.IR_REQUESTED, inspection.reference, inspection.requestedOf),
            )
            inspection
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /**
     * Asking again after a failure: the same element, of the same person,
     * under a new number that points back at the failed one.
     */
    suspend fun reinspect(
        role: Role,
        failedId: String,
        wantedOn: Long?,
        byName: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Result<InspectionEntity> = numbering.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val failed = dao.inspection(failedId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        Inspections.reinspectRefusal(Inspections.resultOf(failed.result), dao.reinspectionCount(failedId) > 0)?.let {
            return@withLock Result.failure(Refused(it.asRefusal()))
        }
        Inspections.requestRefusal(failed.element, failed.requestedOf, now, wantedOn, zone)?.let {
            return@withLock Result.failure(Refused(it.asRefusal()))
        }
        runCatching {
            val again = InspectionEntity(
                id = UUID.randomUUID().toString(),
                projectId = failed.projectId,
                reference = Inspections.reference(dao.countForProject(failed.projectId)),
                kind = failed.kind,
                element = failed.element,
                requestedOf = failed.requestedOf,
                requestedAt = now,
                wantedOn = wantedOn,
                requestedByName = byName,
                reinspectionOf = failed.id,
            )
            dao.upsert(again)
            audit.record(
                ENTITY, again.id, AuditTrail.Action.CREATE, byName,
                Summary.of(Summaries.IR_REQUESTED_AGAIN, failed.reference, again.reference),
            )
            again
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** What the inspector found. Written once; a failure is followed by [reinspect], not an edit. */
    suspend fun decide(
        role: Role,
        inspectionId: String,
        result: Inspections.Result,
        inspectorName: String,
        comments: String,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<InspectionEntity> = numbering.withLock {
        // Under the lock, so two phones' taps in the same second cannot both
        // be the one result.
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val inspection = dao.inspection(inspectionId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        Inspections.resultRefusal(result, inspectorName, comments, inspection.decidedAt)?.let {
            return@withLock Result.failure(Refused(it.asRefusal()))
        }
        val decided = inspection.copy(
            result = result.name,
            inspectorName = inspectorName.trim(),
            comments = comments.trim().ifBlank { null },
            decidedAt = now,
            resultRecordedByName = byName,
        )
        val phrase = when (result) {
            Inspections.Result.PASSED -> Summaries.IR_PASSED
            Inspections.Result.PASSED_WITH_COMMENTS -> Summaries.IR_PASSED_WITH_COMMENTS
            Inspections.Result.FAILED -> Summaries.IR_FAILED
        }
        runCatching {
            dao.upsert(decided)
            audit.record(
                ENTITY, inspection.id, AuditTrail.Action.UPDATE, byName,
                Summary.of(phrase, inspection.reference, decided.inspectorName.orEmpty()),
            )
            decided
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** A passed inspection of the steel or the forms, set against the pour it let go ahead. */
    suspend fun clearPour(
        role: Role,
        inspectionId: String,
        pourId: String,
        byName: String,
    ): Result<InspectionEntity> = numbering.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val inspection = dao.inspection(inspectionId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        val pour = pourOf(pourId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        Inspections.clearPourRefusal(
            kind = Inspections.kindOf(inspection.kind),
            result = Inspections.resultOf(inspection.result),
            clearedPourId = inspection.clearedPourId,
            inspectionProjectId = inspection.projectId,
            pourProjectId = pour.projectId,
        )?.let { return@withLock Result.failure(Refused(it.asRefusal())) }
        val cleared = inspection.copy(clearedPourId = pourId)
        runCatching {
            dao.upsert(cleared)
            audit.record(
                ENTITY, inspection.id, AuditTrail.Action.UPDATE, byName,
                Summary.of(Summaries.IR_CLEARED_POUR, inspection.reference, pour.reference),
            )
            cleared
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    private fun Inspections.Refusal.asRefusal(): Refusal = when (this) {
        Inspections.Refusal.BLANK_ELEMENT -> Refusal.BLANK_ELEMENT
        Inspections.Refusal.NOBODY_ASKED -> Refusal.NOBODY_ASKED
        Inspections.Refusal.WANTED_TOO_EARLY -> Refusal.WANTED_TOO_EARLY
        Inspections.Refusal.ALREADY_DECIDED -> Refusal.ALREADY_DECIDED
        Inspections.Refusal.NO_INSPECTOR -> Refusal.NO_INSPECTOR
        Inspections.Refusal.FAILED_WITHOUT_REASON -> Refusal.FAILED_WITHOUT_REASON
        Inspections.Refusal.COMMENTS_MISSING -> Refusal.COMMENTS_MISSING
        Inspections.Refusal.NOT_FAILED -> Refusal.NOT_FAILED
        Inspections.Refusal.ALREADY_REINSPECTED -> Refusal.ALREADY_REINSPECTED
        Inspections.Refusal.NOT_PASSED -> Refusal.NOT_PASSED
        Inspections.Refusal.NOT_FOR_A_POUR -> Refusal.NOT_FOR_A_POUR
        Inspections.Refusal.ALREADY_CLEARED_A_POUR -> Refusal.ALREADY_CLEARED_A_POUR
        Inspections.Refusal.OTHER_JOB -> Refusal.OTHER_JOB
    }

    companion object {
        /** Whoever keeps the site's record asks for inspections and writes in what was found. */
        fun mayWrite(role: Role): Boolean = role.canWrite(Lens.EVIDENCE)

        fun mayRead(role: Role): Boolean = role.canRead(Lens.EVIDENCE)

        private const val ENTITY = "inspection"
    }
}
