package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One firm's place on one job.
 *
 * Not a property of the firm. The same company is a first-tier subcontractor
 * on the tower and the general contractor on the villa next door, so the
 * position belongs to the pairing and nowhere else. See
 * [il.co.tradesmanager.core.access.Party].
 *
 * [engagedByOrgId] is who brought them on, and it is what makes the chain a
 * chain rather than a list. Null only for the party at the top.
 */
@Entity(
    tableName = "engagements",
    indices = [Index("projectId"), Index("orgId"), Index("engagedByOrgId")],
)
data class EngagementEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val orgId: String,
    /** Denormalised so a job still reads when the other firm is not on this device. */
    val orgName: String,
    /** A [il.co.tradesmanager.core.access.Party] name. */
    val party: String,
    val engagedByOrgId: String?,
    val scopeSummary: String? = null,
    val startedAt: Long?,
    /** Left the job. Not a delete: who was on site in March is asked in September. */
    val endedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * An agreement between two firms on one job.
 *
 * [amount] is the original figure and it is **never written over** once
 * [signedAt] is set. A change raises a [ContractAmendmentEntity]; the figure
 * in force is worked out from the accepted amendments, so every number the
 * contract has ever been stays readable. See
 * [il.co.tradesmanager.core.work.Amendment].
 *
 * [disclosedToOrgIds] is the deliberate exception to confidentiality — an
 * open-book requirement, or a receiver appointed over an insolvent firm. A
 * list rather than a flag, because every disclosure is to somebody specific.
 */
@Entity(
    tableName = "contracts",
    indices = [Index("projectId"), Index("payerOrgId"), Index("payeeOrgId")],
)
data class ContractEntity(
    @PrimaryKey val id: String,
    val reference: String,
    val projectId: String,
    val payerOrgId: String,
    val payeeOrgId: String,
    val title: String,
    val amount: Double,
    /** A [il.co.tradesmanager.core.money.Payments.Terms] name. */
    val terms: String,
    val retentionRate: Double,
    val retentionLimit: Double,
    val signedAt: Long? = null,
    val signedByPayerName: String? = null,
    val signedByPayeeName: String? = null,
    /**
     * Org ids, comma-delimited **with the outer commas** — `,org.a,org.b,`.
     * Empty is the normal case.
     *
     * The bounding commas are load-bearing: the query matches `,id,`, and a
     * bare substring match would disclose a contract to any firm whose id
     * happened to be a prefix of a disclosed one.
     */
    val disclosedToOrgIds: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

/** One accepted or pending change to a signed contract. */
@Entity(tableName = "contract_amendments", indices = [Index("contractId")])
data class ContractAmendmentEntity(
    @PrimaryKey val id: String,
    val contractId: String,
    val version: Int,
    /**
     * Stored rather than derived from the version before. The one question
     * asked in a dispute is what it went from and to, and the answer should
     * not depend on every earlier row having survived.
     */
    val previousAmount: Double,
    val newAmount: Double,
    val reason: String,
    /** A [il.co.tradesmanager.core.work.Amendment.Status] name. */
    val status: String,
    val proposedByOrgId: String,
    val proposedByName: String,
    val proposedAt: Long,
    val decidedByOrgId: String? = null,
    val decidedByName: String? = null,
    val decidedAt: Long? = null,
)

/**
 * A package of work handed from one firm to another, and where it has got to.
 *
 * [stageId] and [scopeId] come from `shared/assets/catalog/scopes.json`. They
 * are what makes this assignable and payable at all: "electrical, floor 3" is
 * four separate packages months apart, and one that cannot say which it is
 * cannot be inspected or invoiced.
 *
 * See [il.co.tradesmanager.core.work.Assignment] for what may follow what,
 * and which of the two firms is entitled to each move.
 */
@Entity(
    tableName = "assignments",
    indices = [Index("projectId"), Index("contractId"), Index("payeeOrgId"), Index("status")],
)
data class AssignmentEntity(
    @PrimaryKey val id: String,
    val reference: String,
    val projectId: String,
    val contractId: String?,
    val payerOrgId: String,
    val payeeOrgId: String,
    val title: String,
    val stageId: String? = null,
    val scopeId: String? = null,
    /** Free text: "Building A, floors 1-7". */
    val location: String? = null,
    val amount: Double,
    /** An [il.co.tradesmanager.core.work.Assignment.Status] name. */
    val status: String,
    val offeredAt: Long? = null,
    val acceptedAt: Long? = null,
    val submittedAt: Long? = null,
    val decidedAt: Long? = null,
    val decidedByName: String? = null,
    /** Why it was sent back. A rejection without one leaves a crew guessing. */
    val rejectionReason: String? = null,
    /** Set when a payment application has been raised against the approval. */
    val invoicedAt: Long? = null,
    val createdByName: String,
    val createdAt: Long,
    val updatedAt: Long,
)
