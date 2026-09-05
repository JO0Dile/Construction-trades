package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.CostEntryEntity
import il.co.tradesmanager.data.local.entity.InvoiceEntity
import il.co.tradesmanager.data.local.entity.JobBudgetEntity
import il.co.tradesmanager.data.local.entity.VariationEntity
import kotlinx.coroutines.flow.Flow

/** Totals for one job, computed in SQL rather than by pulling every row. */
data class MoneyTotals(
    val costToDate: Double,
    val approvedVariations: Double,
    val proposedVariations: Double,
    val invoiced: Double,
    val paid: Double,
)

/** A cost line grouped by what it was spent on. */
data class CostByCategory(val category: String, val total: Double)

/** The same five numbers as [MoneyTotals], summed over every live job. */
data class PortfolioTotals(
    val contractValue: Double,
    val approvedVariations: Double,
    val costToDate: Double,
    val invoiced: Double,
    val paid: Double,
)

@Dao
interface MoneyDao {

    @Upsert
    suspend fun upsertBudget(budget: JobBudgetEntity)

    @Query("SELECT * FROM job_budgets WHERE projectId = :projectId")
    fun observeBudget(projectId: String): Flow<JobBudgetEntity?>

    @Upsert
    suspend fun upsertCost(cost: CostEntryEntity)

    @Delete
    suspend fun deleteCost(cost: CostEntryEntity)

    @Query("SELECT * FROM cost_entries WHERE projectId = :projectId ORDER BY incurredOn DESC")
    fun observeCosts(projectId: String): Flow<List<CostEntryEntity>>

    @Query(
        """
        SELECT category, SUM(amount) AS total
        FROM cost_entries
        WHERE projectId = :projectId
        GROUP BY category
        ORDER BY total DESC
        """,
    )
    fun observeCostsByCategory(projectId: String): Flow<List<CostByCategory>>

    @Upsert
    suspend fun upsertVariation(variation: VariationEntity)

    @Delete
    suspend fun deleteVariation(variation: VariationEntity)

    @Query("SELECT * FROM variations WHERE projectId = :projectId ORDER BY raisedOn DESC")
    fun observeVariations(projectId: String): Flow<List<VariationEntity>>

    @Query("SELECT COUNT(*) FROM variations WHERE projectId = :projectId")
    suspend fun variationCount(projectId: String): Int

    @Upsert
    suspend fun upsertInvoice(invoice: InvoiceEntity)

    @Delete
    suspend fun deleteInvoice(invoice: InvoiceEntity)

    @Query("SELECT * FROM invoices WHERE projectId = :projectId ORDER BY issuedOn DESC")
    fun observeInvoices(projectId: String): Flow<List<InvoiceEntity>>

    /**
     * Every total for a job in one query.
     *
     * Written as scalar subqueries rather than joins on purpose: joining costs
     * to variations to invoices multiplies the rows together, and the totals
     * come out silently wrong rather than failing — the classic fan-out bug,
     * and an expensive one when the number it corrupts is a margin.
     *
     * A rejected variation counts as neither approved nor proposed, which is
     * why the two sums name their statuses instead of splitting on "approved
     * or not".
     */
    @Query(
        """
        SELECT
            (SELECT COALESCE(SUM(amount), 0) FROM cost_entries
                WHERE projectId = :projectId) AS costToDate,
            (SELECT COALESCE(SUM(amount), 0) FROM variations
                WHERE projectId = :projectId AND status = 'APPROVED') AS approvedVariations,
            (SELECT COALESCE(SUM(amount), 0) FROM variations
                WHERE projectId = :projectId AND status = 'PROPOSED') AS proposedVariations,
            (SELECT COALESCE(SUM(amount), 0) FROM invoices
                WHERE projectId = :projectId AND status != 'DRAFT') AS invoiced,
            (SELECT COALESCE(SUM(amount), 0) FROM invoices
                WHERE projectId = :projectId AND status = 'PAID') AS paid
        """,
    )
    fun observeTotals(projectId: String): Flow<MoneyTotals>

    /**
     * The whole book of work, in one query.
     *
     * Every subquery joins back to projects and excludes deleted ones, because
     * a job someone removed must stop counting the moment they remove it — a
     * portfolio figure that silently includes cancelled work is worse than no
     * portfolio figure.
     */
    @Query(
        """
        SELECT
            (SELECT COALESCE(SUM(b.contractValue), 0) FROM job_budgets b
                JOIN projects p ON p.id = b.projectId
                WHERE p.deletedAt IS NULL) AS contractValue,
            (SELECT COALESCE(SUM(v.amount), 0) FROM variations v
                JOIN projects p ON p.id = v.projectId
                WHERE p.deletedAt IS NULL AND v.status = 'APPROVED') AS approvedVariations,
            (SELECT COALESCE(SUM(c.amount), 0) FROM cost_entries c
                JOIN projects p ON p.id = c.projectId
                WHERE p.deletedAt IS NULL) AS costToDate,
            (SELECT COALESCE(SUM(i.amount), 0) FROM invoices i
                JOIN projects p ON p.id = i.projectId
                WHERE p.deletedAt IS NULL AND i.status != 'DRAFT') AS invoiced,
            (SELECT COALESCE(SUM(i.amount), 0) FROM invoices i
                JOIN projects p ON p.id = i.projectId
                WHERE p.deletedAt IS NULL AND i.status = 'PAID') AS paid
        """,
    )
    fun observePortfolio(): Flow<PortfolioTotals>

    /**
     * What the job sheet still needs and nobody has ordered yet, priced from
     * stock.
     *
     * This is the "data is entered once" promise made concrete: material lines
     * a foreman put on the job in the Stuff lens become a committed cost in the
     * Money lens with nobody typing a number twice.
     *
     * Three things are subtracted, and each one is a way the figure would
     * otherwise be wrong:
     *
     *  - what is already allocated, because it is on site and has been bought;
     *  - what is on an open purchase order, because that is counted separately
     *    at the price actually agreed rather than estimated from stock — this
     *    is the subtraction that stops a job showing the same steel twice, once
     *    as an intention and once as an order;
     *  - anything below zero, so an over-delivery does not credit the job.
     */
    @Query(
        """
        SELECT COALESCE(SUM(
            MAX(
                pm.requiredQuantity - pm.allocatedQuantity - COALESCE(ordered.quantity, 0),
                0
            ) * i.purchasePrice
        ), 0)
        FROM project_materials pm
        JOIN inventory_items i ON i.id = pm.inventoryItemId
        LEFT JOIN (
            SELECT l.catalogItemId AS itemId,
                   o.projectId AS projectId,
                   SUM(MAX(l.quantityOrdered - l.quantityReceived, 0)) AS quantity
            FROM purchase_order_lines l
            JOIN purchase_orders o ON o.id = l.orderId
            WHERE o.status IN ('ORDERED', 'PART_RECEIVED')
            GROUP BY l.catalogItemId, o.projectId
        ) ordered
            ON ordered.itemId = pm.catalogItemId AND ordered.projectId = pm.projectId
        WHERE pm.projectId = :projectId AND i.purchasePrice IS NOT NULL
        """,
    )
    fun observeCommittedMaterials(projectId: String): Flow<Double>
}
