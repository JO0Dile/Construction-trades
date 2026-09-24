package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.safety.Ppe
import il.co.tradesmanager.data.local.dao.PpeDao
import il.co.tradesmanager.data.local.entity.PpeIssueEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * The protective equipment register, with the rules in `core.safety.Ppe`
 * applied on the write rather than trusted from the screen.
 *
 * Handing something over takes it off the stock list in the same step, so
 * the shelf count and the register cannot tell two different stories about
 * the same box of gloves.
 */
class PpeRepository(
    private val dao: PpeDao,
    private val inventory: InventoryRepository,
    private val audit: AuditTrail,
) {

    enum class Refusal {
        /**
         * Not somebody who hands out equipment. Issuing needs the safety
         * register and the list of who is in the firm: a worker can do the
         * first and is not shown the second.
         */
        NOT_ALLOWED,
        NO_WORKER,
        NO_ITEM,
        BAD_QUANTITY,
        NOT_SIGNED,
        REPLACE_BY_TOO_EARLY,

        /** Handing back something already handed back, or that is not there. */
        NOT_HELD,

        /** Something other than a rule: a full disk, a database that would not write. */
        UNKNOWN,
    }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    /**
     * What an issue did.
     *
     * [stockShortBy] is how far the shelf count was below what was handed
     * over, so the screen can tell the person issuing that the count was
     * wrong. Null when it covered it, or the item was never on the list.
     */
    data class Issued(val issue: PpeIssueEntity, val stockShortBy: Double?)

    fun observeForCompany(companyId: String?): Flow<List<PpeIssueEntity>> =
        dao.observeForCompany(companyId)

    fun observeHeldBy(accountId: String): Flow<List<PpeIssueEntity>> = dao.observeHeldBy(accountId)

    suspend fun issue(
        role: Role,
        companyId: String?,
        accountId: String?,
        holderName: String,
        inventoryItemId: String?,
        itemName: String,
        quantity: Int,
        size: String?,
        replaceBy: Long?,
        signature: String,
        byAccountId: String?,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<Issued> {
        if (!mayIssue(role)) return Result.failure(Refused(Refusal.NOT_ALLOWED))
        Ppe.refusal(holderName, itemName, quantity, signature, now, replaceBy)?.let {
            return Result.failure(Refused(it.asRefusal()))
        }
        val issue = PpeIssueEntity(
            id = UUID.randomUUID().toString(),
            companyId = companyId,
            accountId = accountId,
            holderName = holderName.trim(),
            inventoryItemId = inventoryItemId,
            itemName = itemName.trim(),
            quantity = quantity,
            size = size?.trim()?.ifBlank { null },
            issuedAt = now,
            replaceBy = replaceBy,
            signature = signature,
            issuedByAccountId = byAccountId,
            issuedByName = byName,
        )
        return runCatching {
            // The shelf is read before it is decremented, so the shortfall is
            // what the count said rather than the nought it was clamped to.
            val shortBy = inventoryItemId
                ?.let { inventory.item(it) }
                ?.let { Ppe.shortfall(it.quantity, quantity) }
            dao.upsert(issue)
            inventoryItemId?.let { itemId ->
                inventory.adjustStock(
                    itemId = itemId,
                    delta = -quantity.toDouble(),
                    reason = Summary.of(Summaries.PPE_ISSUED_TO, issue.holderName),
                    actorName = byName,
                )
            }
            audit.record(
                ENTITY, issue.id, AuditTrail.Action.CREATE, byName,
                Summary.of(
                    Summaries.PPE_ISSUED,
                    quantity.toString(),
                    issue.itemName,
                    issue.holderName,
                ),
            )
            Issued(issue, shortBy)
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /**
     * It is no longer theirs: returned, swapped for a new one, or taken off
     * them. Nothing goes back on the shelf -- a used harness is not stock.
     */
    suspend fun handBack(
        role: Role,
        issueId: String,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<PpeIssueEntity> {
        if (!mayIssue(role)) return Result.failure(Refused(Refusal.NOT_ALLOWED))
        val issue = dao.issue(issueId)
        if (issue == null || issue.handedBackAt != null) {
            return Result.failure(Refused(Refusal.NOT_HELD))
        }
        val updated = issue.copy(handedBackAt = now)
        return runCatching {
            dao.upsert(updated)
            audit.record(
                ENTITY, issue.id, AuditTrail.Action.UPDATE, byName,
                Summary.of(Summaries.PPE_HANDED_BACK, issue.itemName, issue.holderName),
            )
            updated
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    private fun Ppe.Refusal.asRefusal(): Refusal = when (this) {
        Ppe.Refusal.NO_WORKER -> Refusal.NO_WORKER
        Ppe.Refusal.NO_ITEM -> Refusal.NO_ITEM
        Ppe.Refusal.BAD_QUANTITY -> Refusal.BAD_QUANTITY
        Ppe.Refusal.NOT_SIGNED -> Refusal.NOT_SIGNED
        Ppe.Refusal.REPLACE_BY_TOO_EARLY -> Refusal.REPLACE_BY_TOO_EARLY
    }

    companion object {
        /** Who hands equipment out and takes it back. */
        fun mayIssue(role: Role): Boolean = role.canWrite(Lens.EVIDENCE) && role.canRead(Lens.PEOPLE)

        /** Who may read the register: it names people and it is safety evidence. */
        fun mayRead(role: Role): Boolean = role.canRead(Lens.EVIDENCE) && role.canRead(Lens.PEOPLE)

        /** A stored row as the rules see it. */
        fun asIssue(row: PpeIssueEntity): Ppe.Issue = Ppe.Issue(
            id = row.id,
            holderKey = row.accountId ?: Ppe.typedHolderKey(row.holderName),
            holderName = row.holderName,
            itemName = row.itemName,
            quantity = row.quantity,
            issuedAt = row.issuedAt,
            replaceBy = row.replaceBy,
            handedBackAt = row.handedBackAt,
        )

        private const val ENTITY = "ppe_issue"
    }
}
