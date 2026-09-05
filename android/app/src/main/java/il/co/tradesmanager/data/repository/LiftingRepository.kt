package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.safety.Lifting
import il.co.tradesmanager.data.local.dao.LiftingDao
import il.co.tradesmanager.data.local.entity.LiftCrewEntity
import il.co.tradesmanager.data.local.entity.LiftPlanEntity
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Lift plans, and the three people every lift legally needs.
 *
 * The gate itself lives in `core.safety.Lifting`, where it can be tested
 * without a database. This is the part that stores what the gate reads.
 */
class LiftingRepository(
    private val dao: LiftingDao,
    private val audit: AuditTrail,
) {

    /** The three roles. Not a list to be extended casually — each is required. */
    enum class Role { OPERATOR, SLINGER, BANKSMAN }

    fun observePlans(projectId: String): Flow<List<LiftPlanEntity>> = dao.observePlans(projectId)

    fun observePlan(id: String): Flow<LiftPlanEntity?> = dao.observePlan(id)

    fun observeCrew(planId: String): Flow<List<LiftCrewEntity>> = dao.observeCrew(planId)

    suspend fun createPlan(
        projectId: String,
        description: String,
        actorName: String,
    ): LiftPlanEntity {
        val now = System.currentTimeMillis()
        val plan = LiftPlanEntity(
            id = UUID.randomUUID().toString(),
            reference = String.format(Locale.ROOT, "LIFT-%03d", dao.planCount() + 1),
            projectId = projectId,
            description = description.trim(),
            loadWeightKg = null,
            riggingWeightKg = null,
            applianceName = null,
            applianceId = null,
            applianceCertificateExpiresOn = null,
            applianceCertificateRequired = true,
            radiusMetres = null,
            capacityAtRadiusKg = null,
            windLimitKmh = Lifting.DEFAULT_WIND_LIMIT_KMH,
            windSpeedKmh = null,
            plannedFor = null,
            approvedByName = null,
            approvedAt = null,
            completedAt = null,
            notes = null,
            createdByName = actorName,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertPlan(plan)
        audit.record(
            PLAN, plan.id, AuditTrail.Action.CREATE, actorName,
            "${plan.reference} ${plan.description}",
        )
        return plan
    }

    /**
     * Saves the numbers.
     *
     * Editing a plan clears its approval. Somebody signed off a lift of four
     * tonnes at twenty metres; changing either number afterwards and keeping
     * the signature would be a plan nobody actually approved.
     */
    suspend fun updatePlan(plan: LiftPlanEntity, actorName: String) {
        val approvalStillHolds = !changesTheLift(plan)
        dao.upsertPlan(
            plan.copy(
                approvedByName = if (approvalStillHolds) plan.approvedByName else null,
                approvedAt = if (approvalStillHolds) plan.approvedAt else null,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        audit.record(PLAN, plan.id, AuditTrail.Action.UPDATE, actorName, plan.reference)
    }

    /**
     * Whether the change being saved is one the approval was about.
     *
     * Always true for now: every field on the edit form is part of the lift.
     * It exists as a named question rather than an inline `false` so that
     * adding a field the approval does not depend on — a note, a photograph —
     * is a change here and not a silent loss of the signature.
     */
    private fun changesTheLift(@Suppress("UNUSED_PARAMETER") plan: LiftPlanEntity): Boolean = true

    suspend fun approve(plan: LiftPlanEntity, actorName: String) {
        val now = System.currentTimeMillis()
        dao.upsertPlan(
            plan.copy(approvedByName = actorName, approvedAt = now, updatedAt = now),
        )
        audit.record(PLAN, plan.id, AuditTrail.Action.SIGN_OFF, actorName, "${plan.reference} approved")
    }

    suspend fun complete(plan: LiftPlanEntity, actorName: String) {
        val now = System.currentTimeMillis()
        dao.upsertPlan(plan.copy(completedAt = now, updatedAt = now))
        audit.record(PLAN, plan.id, AuditTrail.Action.UPDATE, actorName, "${plan.reference} lifted")
    }

    suspend fun deletePlan(plan: LiftPlanEntity, actorName: String) {
        dao.deletePlan(plan)
        audit.record(PLAN, plan.id, AuditTrail.Action.DELETE, actorName, plan.reference)
    }

    /**
     * Puts somebody in a role, replacing whoever was in it.
     *
     * The certificate's expiry is copied onto the crew row: the plan records
     * which ticket it is relying on and what that ticket said, rather than
     * quietly following an edit to the certificate afterwards.
     */
    suspend fun setCrew(
        plan: LiftPlanEntity,
        role: Role,
        accountId: String?,
        name: String,
        certificationId: String?,
        certificateReference: String?,
        certificateExpiresOn: Long?,
        actorName: String,
    ) {
        dao.clearRole(plan.id, role.name)
        dao.upsertCrew(
            LiftCrewEntity(
                id = UUID.randomUUID().toString(),
                planId = plan.id,
                role = role.name,
                accountId = accountId,
                name = name.trim(),
                certificationId = certificationId,
                certificateReference = certificateReference,
                certificateExpiresOn = certificateExpiresOn,
                createdAt = System.currentTimeMillis(),
            ),
        )
        // Naming the crew is part of the lift, so it costs the approval too.
        if (plan.approvedAt != null) {
            dao.upsertPlan(
                plan.copy(
                    approvedByName = null,
                    approvedAt = null,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        audit.record(
            PLAN, plan.id, AuditTrail.Action.UPDATE, actorName,
            "${plan.reference} ${role.name.lowercase(Locale.ROOT)}: ${name.trim()}",
        )
    }

    suspend fun clearCrew(plan: LiftPlanEntity, role: Role, actorName: String) {
        dao.clearRole(plan.id, role.name)
        audit.record(
            PLAN, plan.id, AuditTrail.Action.UPDATE, actorName,
            "${plan.reference} ${role.name.lowercase(Locale.ROOT)} cleared",
        )
    }

    private companion object {
        const val PLAN = "lift_plan"
    }
}
