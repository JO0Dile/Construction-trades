package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Commercial
import il.co.tradesmanager.core.access.Party
import il.co.tradesmanager.core.money.Payments
import il.co.tradesmanager.core.work.Amendment
import il.co.tradesmanager.core.work.Assignment
import il.co.tradesmanager.data.local.dao.EngagementDao
import il.co.tradesmanager.data.local.entity.AssignmentEntity
import il.co.tradesmanager.data.local.entity.ContractAmendmentEntity
import il.co.tradesmanager.data.local.entity.ContractEntity
import il.co.tradesmanager.data.local.entity.EngagementEntity
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Who is on a job, what they agreed, and what they have been asked to do.
 *
 * Every write here goes through the rule in `core` before it touches the
 * database rather than after, and every one of them returns a [Result] saying
 * which rule refused. A repository that let the screen decide would be a
 * repository whose rules an API call walks straight past, and this data is
 * eventually going to have an API in front of it — see docs/SERVER.md.
 */
class EngagementRepository(
    private val dao: EngagementDao,
    private val audit: AuditTrail,
) {

    /** Why a write was refused, in terms somebody can act on. */
    enum class Refusal {
        /** The position doing the engaging cannot subcontract to that position. */
        NOT_YOURS_TO_ENGAGE,

        /** The move is not one this status allows. */
        WRONG_STATE,

        /** The right move, by the wrong side of the agreement. */
        WRONG_SIDE,

        /** A rejection with no reason, or an amendment with no figure. */
        INCOMPLETE,

        /** Not a party to this contract. */
        NOT_A_PARTY,

        /** Another amendment is already open on this contract. */
        ALREADY_OPEN,

        /** Nothing to submit against: no photograph. */
        NO_EVIDENCE,
    }

    // ---- who is on the job ---------------------------------------------

    fun observeEngagements(projectId: String): Flow<List<EngagementEntity>> =
        dao.observeForProject(projectId)

    /**
     * Brings a firm onto a job.
     *
     * The engaging firm's own position decides who it may bring on, so a
     * second-tier crew cannot quietly appoint a general contractor beneath
     * itself and invert the chain.
     */
    suspend fun engage(
        projectId: String,
        orgId: String,
        orgName: String,
        party: Party,
        engagedByOrgId: String?,
        engagedByParty: Party?,
        scopeSummary: String?,
        actorName: String,
    ): Result<EngagementEntity> {
        if (engagedByParty != null && party !in engagedByParty.mayEngage()) {
            return Result.failure(Refused(Refusal.NOT_YOURS_TO_ENGAGE))
        }
        val now = System.currentTimeMillis()
        val row = EngagementEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            orgId = orgId,
            orgName = orgName.trim(),
            party = party.name,
            engagedByOrgId = engagedByOrgId,
            scopeSummary = scopeSummary?.trim()?.takeIf { it.isNotEmpty() },
            startedAt = now,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(row)
        audit.record(
            ENGAGEMENT, row.id, AuditTrail.Action.CREATE, actorName,
            "${row.orgName} as ${party.name}",
        )
        return Result.success(row)
    }

    // ---- contracts -------------------------------------------------------

    fun observeContracts(projectId: String, orgId: String): Flow<List<ContractEntity>> =
        dao.observeContractsFor(projectId, orgId)

    suspend fun createContract(
        projectId: String,
        payerOrgId: String,
        payeeOrgId: String,
        title: String,
        amount: Double,
        terms: Payments.Terms,
        retentionRate: Double,
        retentionLimit: Double,
        actorName: String,
    ): ContractEntity {
        val now = System.currentTimeMillis()
        val row = ContractEntity(
            id = UUID.randomUUID().toString(),
            reference = String.format(Locale.ROOT, "CT-%04d", dao.contractCount() + 1),
            projectId = projectId,
            payerOrgId = payerOrgId,
            payeeOrgId = payeeOrgId,
            title = title.trim(),
            amount = amount,
            terms = terms.name,
            retentionRate = retentionRate,
            retentionLimit = retentionLimit,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(row)
        audit.record(CONTRACT, row.id, AuditTrail.Action.CREATE, actorName, row.reference)
        return row
    }

    /**
     * Both signatures, then the figure stops being editable.
     *
     * [signedAt] is set only once both names are on it, because a contract
     * signed by one side is an offer.
     */
    suspend fun sign(
        contract: ContractEntity,
        orgId: String,
        actorName: String,
    ): Result<ContractEntity> {
        if (!Commercial.isParty(contract.asAgreement(), orgId)) {
            return Result.failure(Refused(Refusal.NOT_A_PARTY))
        }
        val now = System.currentTimeMillis()
        val payer = if (orgId == contract.payerOrgId) actorName else contract.signedByPayerName
        val payee = if (orgId == contract.payeeOrgId) actorName else contract.signedByPayeeName
        val updated = contract.copy(
            signedByPayerName = payer,
            signedByPayeeName = payee,
            signedAt = if (payer != null && payee != null) contract.signedAt ?: now else null,
            updatedAt = now,
        )
        dao.upsert(updated)
        audit.record(
            CONTRACT, contract.id, AuditTrail.Action.SIGN_OFF, actorName,
            "${contract.reference} signed",
        )
        return Result.success(updated)
    }

    /** The figure in force: the original, plus every accepted amendment. */
    suspend fun currentAmount(contract: ContractEntity): Double =
        Amendment.currentAmount(contract.amount, dao.amendments(contract.id).accepted())

    fun observeAmendments(contractId: String): Flow<List<ContractAmendmentEntity>> =
        dao.observeAmendments(contractId)

    suspend fun proposeAmendment(
        contract: ContractEntity,
        newAmount: Double?,
        reason: String?,
        byOrgId: String,
        actorName: String,
    ): Result<ContractAmendmentEntity> {
        if (!Commercial.isParty(contract.asAgreement(), byOrgId)) {
            return Result.failure(Refused(Refusal.NOT_A_PARTY))
        }
        if (!Amendment.isComplete(newAmount, reason)) {
            return Result.failure(Refused(Refusal.INCOMPLETE))
        }
        val open = dao.openAmendments(contract.id)
        if (!Amendment.canPropose(contract.signedAt != null, open)) {
            val why = if (open > 0) Refusal.ALREADY_OPEN else Refusal.WRONG_STATE
            return Result.failure(Refused(why))
        }
        val now = System.currentTimeMillis()
        val row = ContractAmendmentEntity(
            id = UUID.randomUUID().toString(),
            contractId = contract.id,
            version = dao.lastAmendmentVersion(contract.id) + 1,
            previousAmount = currentAmount(contract),
            newAmount = newAmount!!,
            reason = reason!!.trim(),
            status = Amendment.Status.PROPOSED,
            proposedByOrgId = byOrgId,
            proposedByName = actorName,
            proposedAt = now,
        )
        dao.upsert(row)
        audit.record(
            AMENDMENT, row.id, AuditTrail.Action.CREATE, actorName,
            "${contract.reference} v${row.version}: ${row.previousAmount} -> ${row.newAmount}",
        )
        return Result.success(row)
    }

    suspend fun decideAmendment(
        contract: ContractEntity,
        amendment: ContractAmendmentEntity,
        accept: Boolean,
        byOrgId: String,
        actorName: String,
    ): Result<ContractAmendmentEntity> {
        if (amendment.status != Amendment.Status.PROPOSED) {
            return Result.failure(Refused(Refusal.WRONG_STATE))
        }
        val allowed = Amendment.canAccept(
            proposedByOrgId = amendment.proposedByOrgId,
            payerOrgId = contract.payerOrgId,
            payeeOrgId = contract.payeeOrgId,
            orgId = byOrgId,
        )
        if (!allowed) return Result.failure(Refused(Refusal.WRONG_SIDE))
        val now = System.currentTimeMillis()
        val updated = amendment.copy(
            status = if (accept) Amendment.Status.ACCEPTED else Amendment.Status.REJECTED,
            decidedByOrgId = byOrgId,
            decidedByName = actorName,
            decidedAt = now,
        )
        dao.upsert(updated)
        audit.record(
            AMENDMENT, amendment.id, AuditTrail.Action.SIGN_OFF, actorName,
            "${contract.reference} v${amendment.version} ${updated.status}",
        )
        return Result.success(updated)
    }

    // ---- assignments -----------------------------------------------------

    fun observeAssignments(projectId: String, orgId: String): Flow<List<AssignmentEntity>> =
        dao.observeAssignments(projectId, orgId)

    fun observeAssignment(id: String): Flow<AssignmentEntity?> = dao.observeAssignment(id)

    suspend fun createAssignment(
        projectId: String,
        contractId: String?,
        payerOrgId: String,
        payeeOrgId: String,
        title: String,
        stageId: String?,
        scopeId: String?,
        location: String?,
        amount: Double,
        actorName: String,
    ): AssignmentEntity {
        val now = System.currentTimeMillis()
        val row = AssignmentEntity(
            id = UUID.randomUUID().toString(),
            reference = String.format(Locale.ROOT, "WP-%04d", dao.assignmentCount() + 1),
            projectId = projectId,
            contractId = contractId,
            payerOrgId = payerOrgId,
            payeeOrgId = payeeOrgId,
            title = title.trim(),
            stageId = stageId,
            scopeId = scopeId,
            location = location?.trim()?.takeIf { it.isNotEmpty() },
            amount = amount,
            status = Assignment.Status.DRAFT,
            createdByName = actorName,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(row)
        audit.record(ASSIGNMENT, row.id, AuditTrail.Action.CREATE, actorName, row.reference)
        return row
    }

    /**
     * Moves a package along, if this side is entitled to make that move.
     *
     * The one function every transition goes through. Splitting it into
     * accept/submit/approve/reject would put the same three checks in four
     * places, and the fourth would be the one that forgot the side check —
     * which is how a crew ends up able to approve its own work.
     */
    suspend fun move(
        assignment: AssignmentEntity,
        to: String,
        byOrgId: String,
        actorName: String,
        reason: String? = null,
        photoCount: Int = 0,
    ): Result<AssignmentEntity> {
        val side = when (byOrgId) {
            assignment.payerOrgId -> Assignment.Side.PAYER
            assignment.payeeOrgId -> Assignment.Side.PAYEE
            else -> return Result.failure(Refused(Refusal.NOT_A_PARTY))
        }
        if (!Assignment.canMove(assignment.status, to)) {
            return Result.failure(Refused(Refusal.WRONG_STATE))
        }
        if (!Assignment.mayMove(assignment.status, to, side)) {
            return Result.failure(Refused(Refusal.WRONG_SIDE))
        }
        if (to == Assignment.Status.REJECTED && !Assignment.canReject(reason)) {
            return Result.failure(Refused(Refusal.INCOMPLETE))
        }
        if (to == Assignment.Status.SUBMITTED &&
            !Assignment.canSubmit(assignment.status, photoCount)
        ) {
            return Result.failure(Refused(Refusal.NO_EVIDENCE))
        }

        val now = System.currentTimeMillis()
        val updated = assignment.copy(
            status = to,
            offeredAt = if (to == Assignment.Status.OFFERED) now else assignment.offeredAt,
            acceptedAt = if (to == Assignment.Status.ACCEPTED) now else assignment.acceptedAt,
            submittedAt = if (to == Assignment.Status.SUBMITTED) now else assignment.submittedAt,
            decidedAt = if (to in DECISIONS) now else assignment.decidedAt,
            decidedByName = if (to in DECISIONS) actorName else assignment.decidedByName,
            // Cleared on the way back to work, so a rejection that was fixed
            // does not keep showing last month's complaint.
            rejectionReason = when (to) {
                Assignment.Status.REJECTED -> reason?.trim()
                Assignment.Status.IN_PROGRESS -> null
                else -> assignment.rejectionReason
            },
            updatedAt = now,
        )
        dao.upsert(updated)
        audit.record(
            ASSIGNMENT, assignment.id,
            if (to in DECISIONS) AuditTrail.Action.SIGN_OFF else AuditTrail.Action.UPDATE,
            actorName,
            "${assignment.reference} ${assignment.status} -> $to" +
                (reason?.let { ": $it" } ?: ""),
        )
        return Result.success(updated)
    }

    /**
     * Records that an approved package has had its application raised.
     *
     * The crew's move, not the payer's: the party being paid is the one that
     * raises the claim. Checked here rather than left to the screen that hides
     * the button, because the screen is not the enforcement — this is the same
     * repository whose whole point is that a rule an interface holds is a rule
     * an API call walks past.
     */
    suspend fun markInvoiced(
        assignment: AssignmentEntity,
        byOrgId: String,
        actorName: String,
    ): Result<AssignmentEntity> {
        if (byOrgId.isBlank() || byOrgId != assignment.payeeOrgId) {
            return Result.failure(
                Refused(
                    if (byOrgId == assignment.payerOrgId) {
                        Refusal.WRONG_SIDE
                    } else {
                        Refusal.NOT_A_PARTY
                    },
                ),
            )
        }
        if (!Assignment.canInvoice(assignment.status, assignment.invoicedAt != null)) {
            return Result.failure(Refused(Refusal.WRONG_STATE))
        }
        val now = System.currentTimeMillis()
        val updated = assignment.copy(invoicedAt = now, updatedAt = now)
        dao.upsert(updated)
        audit.record(
            ASSIGNMENT, assignment.id, AuditTrail.Action.UPDATE, actorName,
            "${assignment.reference} invoiced",
        )
        return Result.success(updated)
    }

    /** A refusal, carrying which rule said no. */
    class Refused(val refusal: Refusal) : IllegalStateException(refusal.name)

    private companion object {
        const val ENGAGEMENT = "engagement"
        const val CONTRACT = "contract"
        const val AMENDMENT = "contract_amendment"
        const val ASSIGNMENT = "assignment"
        val DECISIONS = setOf(Assignment.Status.APPROVED, Assignment.Status.REJECTED)
    }
}

/** The contract, as the confidentiality rule sees it. */
fun ContractEntity.asAgreement(): Commercial.Agreement = Commercial.Agreement(
    id = id,
    payerOrgId = payerOrgId,
    payeeOrgId = payeeOrgId,
    disclosedToOrgIds = disclosedToOrgIds
        .split(',')
        .map { it.trim() }
        .filterTo(mutableSetOf()) { it.isNotEmpty() },
)

private fun List<ContractAmendmentEntity>.accepted(): List<Amendment.Version> =
    filter { it.status == Amendment.Status.ACCEPTED }.map {
        Amendment.Version(
            version = it.version,
            previousAmount = it.previousAmount,
            newAmount = it.newAmount,
            reason = it.reason,
            proposedByOrgId = it.proposedByOrgId,
            acceptedByOrgId = it.decidedByOrgId.orEmpty(),
            acceptedAt = it.decidedAt ?: it.proposedAt,
        )
    }
