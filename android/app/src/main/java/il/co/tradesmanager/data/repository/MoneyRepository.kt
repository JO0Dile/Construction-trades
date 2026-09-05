package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.money.JobFinancials
import il.co.tradesmanager.data.local.dao.CostByCategory
import il.co.tradesmanager.data.local.dao.MoneyDao
import kotlinx.coroutines.flow.map
import il.co.tradesmanager.data.local.entity.CostEntryEntity
import il.co.tradesmanager.data.local.entity.InvoiceEntity
import il.co.tradesmanager.data.local.entity.JobBudgetEntity
import il.co.tradesmanager.data.local.entity.VariationEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The Money lens: what a job is worth, what it has cost, what is still owed.
 *
 * Every amount stored here is net of VAT. That is the number that decides
 * whether a job made anything, and it is the number a supplier's statement is
 * reconciled against. VAT is added once, when a document is printed, from the
 * rate that document was issued under — never accumulated as you go, which is
 * how rounding errors turn into an invoice that is four agorot off.
 */
class MoneyRepository(
    private val dao: MoneyDao,
    private val equipment: EquipmentRepository,
    private val audit: AuditTrail,
) {

    /**
     * Everything the Money screen needs, as one flow.
     *
     * The totals come from SQL, the committed figure from the job's own
     * material lines, and the arithmetic from [JobFinancials], which knows
     * nothing about databases and is therefore the part that can be tested
     * properly.
     */
    fun observeFinancials(projectId: String): Flow<JobFinancials> = combine(
        dao.observeBudget(projectId),
        dao.observeTotals(projectId),
        dao.observeCommittedMaterials(projectId),
        equipment.observeCommittedHire(projectId),
    ) { budget, totals, materials, hire ->
        JobFinancials(
            contractValue = budget?.contractValue ?: 0.0,
            approvedVariations = totals.approvedVariations,
            proposedVariations = totals.proposedVariations,
            costToDate = totals.costToDate,
            // Priced materials still to buy, plus what hired plant has run up.
            // Both are commitments the job has made and neither is a cost line
            // yet; the supplier's invoice is what becomes one.
            committedCosts = materials + hire,
            invoiced = totals.invoiced,
            paid = totals.paid,
            vatRate = budget?.vatRate ?: JobFinancials.ISRAELI_VAT,
        )
    }

    /**
     * The whole book of work as one [JobFinancials], so the dashboard and a
     * single job are read with the same tested arithmetic rather than two
     * versions of it that drift apart.
     *
     * Committed costs are left out here: they are a per-job forecast, and
     * summing forecasts across a portfolio produces a number nobody can act on.
     */
    fun observePortfolio(): Flow<JobFinancials> = dao.observePortfolio().map { totals ->
        JobFinancials(
            contractValue = totals.contractValue,
            approvedVariations = totals.approvedVariations,
            costToDate = totals.costToDate,
            invoiced = totals.invoiced,
            paid = totals.paid,
        )
    }

    fun observeBudget(projectId: String): Flow<JobBudgetEntity?> = dao.observeBudget(projectId)
    fun observeCosts(projectId: String): Flow<List<CostEntryEntity>> = dao.observeCosts(projectId)
    fun observeVariations(projectId: String): Flow<List<VariationEntity>> =
        dao.observeVariations(projectId)
    fun observeInvoices(projectId: String): Flow<List<InvoiceEntity>> = dao.observeInvoices(projectId)
    fun observeCostsByCategory(projectId: String): Flow<List<CostByCategory>> =
        dao.observeCostsByCategory(projectId)

    suspend fun setBudget(
        projectId: String,
        contractValue: Double,
        vatRate: Double,
        notes: String?,
        actorName: String,
    ) {
        dao.upsertBudget(
            JobBudgetEntity(
                projectId = projectId,
                contractValue = contractValue,
                vatRate = vatRate,
                currency = CURRENCY,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = System.currentTimeMillis(),
            ),
        )
        audit.record("job_budget", projectId, AuditTrail.Action.UPDATE, actorName, "Contract value set")
    }

    suspend fun addCost(
        projectId: String,
        category: String,
        description: String,
        amount: Double,
        incurredOn: Long,
        supplierInvoiceRef: String?,
        actorName: String,
    ): CostEntryEntity {
        val cost = CostEntryEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            category = category,
            description = description.trim(),
            amount = amount,
            incurredOn = incurredOn,
            supplierId = null,
            supplierInvoiceRef = supplierInvoiceRef?.trim()?.takeIf { it.isNotEmpty() },
            recordedBy = actorName,
            createdAt = System.currentTimeMillis(),
        )
        dao.upsertCost(cost)
        audit.record("cost_entry", cost.id, AuditTrail.Action.CREATE, actorName, cost.description)
        return cost
    }

    suspend fun removeCost(cost: CostEntryEntity, actorName: String) {
        dao.deleteCost(cost)
        audit.record("cost_entry", cost.id, AuditTrail.Action.DELETE, actorName, cost.description)
    }

    /**
     * Raises a variation. It starts [JobFinancials.VariationStatus.PROPOSED],
     * because until the client has said yes it changes nothing — counting it
     * early is how a job looks profitable right up to the moment it is not.
     *
     * The reference is generated in site form (VO-01) rather than left blank:
     * variations get argued about over the phone, and an argument needs a name.
     */
    suspend fun raiseVariation(
        projectId: String,
        title: String,
        amount: Double,
        notes: String?,
        actorName: String,
    ): VariationEntity {
        val next = dao.variationCount(projectId) + 1
        val variation = VariationEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            reference = "VO-%02d".format(next),
            title = title.trim(),
            amount = amount,
            status = JobFinancials.VariationStatus.PROPOSED,
            raisedOn = System.currentTimeMillis(),
            decidedOn = null,
            raisedBy = actorName,
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
        )
        dao.upsertVariation(variation)
        audit.record(
            "variation", variation.id, AuditTrail.Action.CREATE, actorName,
            "${variation.reference} ${variation.title}",
        )
        return variation
    }

    suspend fun decideVariation(variation: VariationEntity, approved: Boolean, actorName: String) {
        val status = if (approved) {
            JobFinancials.VariationStatus.APPROVED
        } else {
            JobFinancials.VariationStatus.REJECTED
        }
        dao.upsertVariation(
            variation.copy(status = status, decidedOn = System.currentTimeMillis()),
        )
        audit.record(
            "variation", variation.id, AuditTrail.Action.UPDATE, actorName,
            "${variation.reference} ${status.lowercase()}",
        )
    }

    suspend fun removeVariation(variation: VariationEntity, actorName: String) {
        dao.deleteVariation(variation)
        audit.record("variation", variation.id, AuditTrail.Action.DELETE, actorName, variation.reference)
    }

    /**
     * Raises an invoice. The VAT rate is copied from the budget rather than
     * referenced, because an invoice has to keep showing the rate it was
     * issued under even after the rate changes.
     */
    suspend fun addInvoice(
        projectId: String,
        number: String,
        amount: Double,
        vatRate: Double,
        issuedOn: Long,
        dueOn: Long?,
        actorName: String,
    ): InvoiceEntity {
        val invoice = InvoiceEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            number = number.trim(),
            amount = amount,
            vatRate = vatRate,
            issuedOn = issuedOn,
            dueOn = dueOn,
            paidOn = null,
            status = JobFinancials.InvoiceStatus.SENT,
            notes = null,
        )
        dao.upsertInvoice(invoice)
        audit.record("invoice", invoice.id, AuditTrail.Action.CREATE, actorName, invoice.number)
        return invoice
    }

    suspend fun markInvoicePaid(invoice: InvoiceEntity, actorName: String) {
        dao.upsertInvoice(
            invoice.copy(
                status = JobFinancials.InvoiceStatus.PAID,
                paidOn = System.currentTimeMillis(),
            ),
        )
        audit.record("invoice", invoice.id, AuditTrail.Action.UPDATE, actorName, "${invoice.number} paid")
    }

    suspend fun removeInvoice(invoice: InvoiceEntity, actorName: String) {
        dao.deleteInvoice(invoice)
        audit.record("invoice", invoice.id, AuditTrail.Action.DELETE, actorName, invoice.number)
    }

    private companion object {
        const val CURRENCY = "ILS"
    }
}
