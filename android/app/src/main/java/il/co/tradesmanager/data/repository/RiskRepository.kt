package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.safety.Risks
import il.co.tradesmanager.data.local.dao.RiskDao
import il.co.tradesmanager.data.local.entity.RiskAssessmentEntity
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A job's risk assessment, with the rules in `core.safety.Risks` applied on
 * every write. A review is recorded in the audit trail with the scores it
 * left, so the row can change and its history cannot.
 */
class RiskRepository(
    private val dao: RiskDao,
    private val audit: AuditTrail,
) {

    enum class Refusal {
        NOT_ALLOWED,
        BLANK_ACTIVITY,
        BLANK_HAZARD,
        OUT_OF_RANGE,
        NO_CONTROLS,
        RESIDUAL_ABOVE_INITIAL,
        REVIEW_IN_PAST,
        ALREADY_CLOSED,
        UNKNOWN,
    }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    /** What a person types for one row: the same fields when it is first assessed and when it is reviewed. */
    data class Assessment(
        val activity: String,
        val hazard: String,
        val whoAtRisk: String,
        val likelihoodBefore: Int,
        val severityBefore: Int,
        val controls: String,
        val likelihoodAfter: Int,
        val severityAfter: Int,
        val ownerName: String,
        val reviewOn: LocalDate?,
    )

    private val lock = Mutex()

    /** Every job's rows, recent first, for the search box. */
    suspend fun all(): List<RiskAssessmentEntity> = dao.all()

    fun observeForProject(projectId: String): Flow<List<RiskAssessmentEntity>> = dao.observeForProject(projectId)

    suspend fun assess(
        role: Role,
        projectId: String,
        assessment: Assessment,
        byName: String,
        today: LocalDate = LocalDate.now(),
        now: Long = System.currentTimeMillis(),
    ): Result<RiskAssessmentEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        refusalOf(assessment, today)?.let { return@withLock Result.failure(Refused(it)) }
        runCatching {
            val risk = RiskAssessmentEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                reference = Risks.reference(dao.countForProject(projectId)),
                activity = assessment.activity.trim(),
                hazard = assessment.hazard.trim(),
                whoAtRisk = assessment.whoAtRisk.trim().ifBlank { null },
                likelihoodBefore = assessment.likelihoodBefore,
                severityBefore = assessment.severityBefore,
                controls = assessment.controls.trim().ifBlank { null },
                likelihoodAfter = assessment.likelihoodAfter,
                severityAfter = assessment.severityAfter,
                ownerName = assessment.ownerName.trim().ifBlank { null },
                reviewOnDay = assessment.reviewOn?.toEpochDay(),
                recordedByName = byName,
                createdAt = now,
            )
            dao.upsert(risk)
            audit.record(
                ENTITY, risk.id, AuditTrail.Action.CREATE, byName,
                Summary.of(Summaries.RA_ASSESSED, risk.reference, residualOf(risk).toString()),
            )
            risk
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** Looked at again: rescored, the controls brought up to date, the next review set. */
    suspend fun review(
        role: Role,
        riskId: String,
        assessment: Assessment,
        byName: String,
        today: LocalDate = LocalDate.now(),
        now: Long = System.currentTimeMillis(),
    ): Result<RiskAssessmentEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val risk = dao.risk(riskId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        if (risk.closed) return@withLock Result.failure(Refused(Refusal.ALREADY_CLOSED))
        refusalOf(assessment, today)?.let { return@withLock Result.failure(Refused(it)) }
        val reviewed = risk.copy(
            activity = assessment.activity.trim(),
            hazard = assessment.hazard.trim(),
            whoAtRisk = assessment.whoAtRisk.trim().ifBlank { null },
            likelihoodBefore = assessment.likelihoodBefore,
            severityBefore = assessment.severityBefore,
            controls = assessment.controls.trim().ifBlank { null },
            likelihoodAfter = assessment.likelihoodAfter,
            severityAfter = assessment.severityAfter,
            ownerName = assessment.ownerName.trim().ifBlank { null },
            reviewOnDay = assessment.reviewOn?.toEpochDay(),
            lastReviewedAt = now,
            lastReviewedByName = byName,
        )
        runCatching {
            dao.upsert(reviewed)
            audit.record(
                ENTITY, risk.id, AuditTrail.Action.UPDATE, byName,
                Summary.of(Summaries.RA_REVIEWED, risk.reference, residualOf(risk).toString(), residualOf(reviewed).toString()),
            )
            reviewed
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** The activity is over; the risk goes with it, and stays on the record. */
    suspend fun close(role: Role, riskId: String, byName: String): Result<RiskAssessmentEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val risk = dao.risk(riskId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        if (risk.closed) return@withLock Result.failure(Refused(Refusal.ALREADY_CLOSED))
        val closed = risk.copy(closed = true)
        runCatching {
            dao.upsert(closed)
            audit.record(ENTITY, risk.id, AuditTrail.Action.UPDATE, byName, Summary.of(Summaries.RA_CLOSED, risk.reference))
            closed
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    private fun refusalOf(assessment: Assessment, today: LocalDate): Refusal? =
        Risks.refusal(
            activity = assessment.activity,
            hazard = assessment.hazard,
            likelihoodBefore = assessment.likelihoodBefore,
            severityBefore = assessment.severityBefore,
            controls = assessment.controls,
            likelihoodAfter = assessment.likelihoodAfter,
            severityAfter = assessment.severityAfter,
            reviewOn = assessment.reviewOn,
            today = today,
        )?.let {
            when (it) {
                Risks.Refusal.BLANK_ACTIVITY -> Refusal.BLANK_ACTIVITY
                Risks.Refusal.BLANK_HAZARD -> Refusal.BLANK_HAZARD
                Risks.Refusal.OUT_OF_RANGE -> Refusal.OUT_OF_RANGE
                Risks.Refusal.NO_CONTROLS -> Refusal.NO_CONTROLS
                Risks.Refusal.RESIDUAL_ABOVE_INITIAL -> Refusal.RESIDUAL_ABOVE_INITIAL
                Risks.Refusal.REVIEW_IN_PAST -> Refusal.REVIEW_IN_PAST
                Risks.Refusal.ALREADY_CLOSED -> Refusal.ALREADY_CLOSED
            }
        }

    companion object {
        /** Whoever keeps the site's record keeps its risk assessment. */
        fun mayWrite(role: Role): Boolean = role.canWrite(Lens.EVIDENCE)

        fun mayRead(role: Role): Boolean = role.canRead(Lens.EVIDENCE)

        fun residualOf(risk: RiskAssessmentEntity): Int = Risks.score(risk.likelihoodAfter, risk.severityAfter)

        fun initialOf(risk: RiskAssessmentEntity): Int = Risks.score(risk.likelihoodBefore, risk.severityBefore)

        private const val ENTITY = "risk_assessment"
    }
}
