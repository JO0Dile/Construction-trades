package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.safety.Violations
import il.co.tradesmanager.data.local.dao.ViolationDao
import il.co.tradesmanager.data.local.entity.ViolationEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Where the violation rules are actually enforced.
 *
 * Every check here is repeated from `core.safety.Violations` on purpose. The
 * screen greys out a button; this refuses. A screen can be wrong — the
 * submit-needs-a-photograph rule on work packages passed for weeks because
 * the caller handed it a count of one — and a repository that trusted the
 * screen would let a violation be confirmed against nobody, with nothing to
 * show for it, because of a bug three files away.
 */
class ViolationRepository(
    private val dao: ViolationDao,
    private val photos: PhotoRepository,
    private val audit: AuditTrail,
) {

    /** Why a write was refused. */
    enum class Refusal {
        /** The person writing it is not a safety officer here. */
        NOT_AN_OFFICER,

        /** No such violation. */
        UNKNOWN,

        /** Already confirmed or already cancelled. */
        NOT_A_DRAFT,

        /** Nobody named, nothing to show, or nothing written down. */
        INCOMPLETE,

        /** A negative charge. That is a credit, and this is not one. */
        BAD_COST,
    }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    fun observeForCompany(companyId: String): Flow<List<ViolationEntity>> =
        dao.observeForCompany(companyId)

    /**
     * What is on somebody's name, for them and for their management.
     *
     * Confirmed only, and that is deliberate: a draft is an officer still
     * deciding, and showing a person one would be worse than showing nothing.
     */
    fun observeConfirmedAgainst(accountId: String): Flow<List<ViolationEntity>> =
        dao.observeConfirmedAgainst(accountId)

    fun observeDraftsBy(accountId: String): Flow<List<ViolationEntity>> =
        dao.observeDraftsBy(accountId)

    fun observe(id: String): Flow<ViolationEntity?> = dao.observe(id)

    /**
     * Starts one. Incomplete by design — the officer is standing in front of
     * the thing and will attach the evidence next.
     *
     * The name and ID number are copied in now, so the record says who was
     * named at the time even if that account is later renamed.
     */
    suspend fun startDraft(
        role: Role,
        companyId: String,
        projectId: String?,
        againstAccountId: String,
        againstName: String,
        againstIdNumber: String,
        recordedByAccountId: String,
        recordedByName: String,
    ): Result<ViolationEntity> {
        if (!mayWrite(role)) return Result.failure(Refused(Refusal.NOT_AN_OFFICER))
        val now = System.currentTimeMillis()
        val violation = ViolationEntity(
            id = UUID.randomUUID().toString(),
            companyId = companyId,
            projectId = projectId,
            againstAccountId = againstAccountId,
            againstName = againstName,
            againstIdNumber = againstIdNumber,
            description = "",
            status = Violations.Status.DRAFT.name,
            recordedByAccountId = recordedByAccountId,
            recordedByName = recordedByName,
            recordedAt = now,
        )
        dao.upsert(violation)
        // Not audited. A draft is not an accusation yet, and an audit row
        // naming somebody the officer then thought better of is the record
        // this feature exists to avoid making.
        return Result.success(violation)
    }

    /** Edits a draft. Refuses once it is not one. */
    suspend fun updateDraft(
        violation: ViolationEntity,
        description: String,
        costAmount: Double?,
    ): Result<ViolationEntity> {
        if (violation.status != Violations.Status.DRAFT.name) {
            return Result.failure(Refused(Refusal.NOT_A_DRAFT))
        }
        if (!Violations.acceptableCost(costAmount)) {
            return Result.failure(Refused(Refusal.BAD_COST))
        }
        val updated = violation.copy(description = description.trim(), costAmount = costAmount)
        dao.upsert(updated)
        return Result.success(updated)
    }

    /**
     * Stands behind it. From here it cannot be edited or withdrawn.
     *
     * The evidence is counted from the photo table rather than taken as a
     * parameter. A caller passing its own count is a caller that can be wrong,
     * and this is the check the whole feature rests on.
     */
    suspend fun confirm(
        violation: ViolationEntity,
        actorName: String,
    ): Result<ViolationEntity> {
        val evidence = photos.countFor(PhotoRepository.Owner.VIOLATION, violation.id)
        val draft = Violations.Draft(
            status = Violations.Status.valueOf(violation.status),
            againstAccountId = violation.againstAccountId,
            evidenceCount = evidence,
            description = violation.description,
        )
        Violations.blocksConfirming(draft)?.let { blocker ->
            return Result.failure(
                Refused(
                    if (blocker == Violations.Blocker.NOT_A_DRAFT) {
                        Refusal.NOT_A_DRAFT
                    } else {
                        Refusal.INCOMPLETE
                    },
                ),
            )
        }
        val confirmed = violation.copy(
            status = Violations.Status.CONFIRMED.name,
            confirmedAt = System.currentTimeMillis(),
        )
        dao.upsert(confirmed)
        audit.record(
            entityType = ENTITY,
            entityId = confirmed.id,
            action = AuditTrail.Action.SIGN_OFF,
            actorName = actorName,
            summary = "Violation confirmed against ${confirmed.againstName}",
        )
        return Result.success(confirmed)
    }

    /** Drops a draft. Kept and marked rather than deleted. */
    suspend fun cancel(violation: ViolationEntity): Result<ViolationEntity> {
        if (!Violations.canCancel(Violations.Status.valueOf(violation.status))) {
            return Result.failure(Refused(Refusal.NOT_A_DRAFT))
        }
        val cancelled = violation.copy(
            status = Violations.Status.CANCELLED.name,
            cancelledAt = System.currentTimeMillis(),
        )
        dao.upsert(cancelled)
        return Result.success(cancelled)
    }

    private fun mayWrite(role: Role): Boolean = role.canWrite(Lens.EVIDENCE)

    private companion object {
        const val ENTITY = "violation"
    }
}
