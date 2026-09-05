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
     * What the job sheet says it still needs, priced from stock.
     *
     * This is the whole "data is entered once" promise made concrete: material
     * lines a foreman put on the job in the Stuff lens become a committed cost
     * in the Money lens with nobody typing a number twice. Only the quantity
     * not yet allocated counts — what is already on site has been bought and
     * belongs in a cost line, not a forecast.
     */
    @Query(
        """
        SELECT COALESCE(SUM(
            MAX(pm.requiredQuantity - pm.allocatedQuantity, 0) * i.purchasePrice
        ), 0)
        FROM project_materials pm
        JOIN inventory_items i ON i.id = pm.inventoryItemId
        WHERE pm.projectId = :projectId AND i.purchasePrice IS NOT NULL
        """,
    )
    fun observeCommittedMaterials(projectId: String): Flow<Double>
}
