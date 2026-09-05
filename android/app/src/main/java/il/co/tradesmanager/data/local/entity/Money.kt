package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * What a job is worth and what the tax on it is.
 *
 * One row per job, keyed by the job itself — a job has one contract, and a
 * second budget row would only ever be a bug.
 *
 * [vatRate] is stored here rather than read from a constant because Israeli
 * VAT changes: it went from 17% to 18% on 1 January 2025. A job priced under
 * the old rate must keep printing the old rate on its paperwork, so the rate
 * is a property of the job, snapshotted when it is set, not a property of the
 * app version someone happens to be running.
 */
@Entity(tableName = "job_budgets")
data class JobBudgetEntity(
    @PrimaryKey val projectId: String,
    /** What the client agreed to pay, before VAT. */
    val contractValue: Double,
    val vatRate: Double,
    /** ILS today. Stored so a job priced in anything else is not a migration. */
    val currency: String,
    val notes: String?,
    val updatedAt: Long,
)

/**
 * Money that has gone out on a job.
 *
 * Amounts are net of VAT throughout, because that is the number that decides
 * whether a job made anything. VAT is added at the point of printing an
 * invoice, once, from the job's own rate.
 */
@Entity(tableName = "cost_entries", indices = [Index("projectId"), Index("supplierId")])
data class CostEntryEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    /** MATERIAL, LABOUR, PLANT, SUBCONTRACTOR, OTHER — see MoneyRepository. */
    val category: String,
    val description: String,
    val amount: Double,
    val incurredOn: Long,
    val supplierId: String?,
    /** The supplier's own invoice number, for matching against a statement. */
    val supplierInvoiceRef: String?,
    val recordedBy: String,
    val createdAt: Long,
)

/**
 * Extra work, or work taken away.
 *
 * Variations are where construction jobs are won and lost, which is why they
 * are their own thing rather than another cost line: a variation has a state
 * — proposed, approved, rejected — and only an approved one changes what the
 * client owes. [amount] may be negative; an omission is a variation too.
 */
@Entity(tableName = "variations", indices = [Index("projectId")])
data class VariationEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    /** Site reference people say out loud: VO-01, VO-02. */
    val reference: String,
    val title: String,
    val amount: Double,
    /** PROPOSED, APPROVED, REJECTED. */
    val status: String,
    val raisedOn: Long,
    val decidedOn: Long?,
    val raisedBy: String,
    val notes: String?,
)

/**
 * What has been billed to the client, and whether it has been paid.
 *
 * [vatRate] is snapshotted at issue rather than read from the budget, because
 * an invoice is a document that was correct on the day it was sent and must
 * keep showing what it showed then, whatever happens to the rate afterwards.
 */
@Entity(tableName = "invoices", indices = [Index("projectId")])
data class InvoiceEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val number: String,
    val amount: Double,
    val vatRate: Double,
    val issuedOn: Long,
    val dueOn: Long?,
    val paidOn: Long?,
    /** DRAFT, SENT, PAID. */
    val status: String,
    val notes: String?,
)
