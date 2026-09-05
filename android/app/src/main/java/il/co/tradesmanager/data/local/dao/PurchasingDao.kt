package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.PurchaseOrderEntity
import il.co.tradesmanager.data.local.entity.PurchaseOrderLineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchasingDao {

    @Upsert
    suspend fun upsertOrder(order: PurchaseOrderEntity)

    @Delete
    suspend fun deleteOrder(order: PurchaseOrderEntity)

    @Query("SELECT * FROM purchase_orders ORDER BY updatedAt DESC")
    fun observeOrders(): Flow<List<PurchaseOrderEntity>>

    @Query("SELECT * FROM purchase_orders WHERE projectId = :projectId ORDER BY updatedAt DESC")
    fun observeOrdersForProject(projectId: String): Flow<List<PurchaseOrderEntity>>

    @Query("SELECT * FROM purchase_orders WHERE id = :id")
    fun observeOrder(id: String): Flow<PurchaseOrderEntity?>

    @Query("SELECT * FROM purchase_orders WHERE id = :id")
    suspend fun order(id: String): PurchaseOrderEntity?

    @Query("SELECT COUNT(*) FROM purchase_orders")
    suspend fun orderCount(): Int

    @Upsert
    suspend fun upsertLine(line: PurchaseOrderLineEntity)

    @Delete
    suspend fun deleteLine(line: PurchaseOrderLineEntity)

    @Query("SELECT * FROM purchase_order_lines WHERE orderId = :orderId ORDER BY sortOrder")
    fun observeLines(orderId: String): Flow<List<PurchaseOrderLineEntity>>

    @Query("SELECT * FROM purchase_order_lines WHERE orderId = :orderId ORDER BY sortOrder")
    suspend fun lines(orderId: String): List<PurchaseOrderLineEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM purchase_order_lines WHERE orderId = :orderId")
    suspend fun nextLineSortOrder(orderId: String): Int

    /**
     * What is on order for a job and has not arrived, at the price actually
     * agreed with the supplier.
     *
     * A cancelled or draft order commits nothing — a draft is a shopping list
     * and a cancelled order is a conversation that ended.
     */
    @Query(
        """
        SELECT COALESCE(SUM(
            MAX(l.quantityOrdered - l.quantityReceived, 0) * l.unitPrice
        ), 0)
        FROM purchase_order_lines l
        JOIN purchase_orders o ON o.id = l.orderId
        WHERE o.projectId = :projectId AND o.status IN ('ORDERED', 'PART_RECEIVED')
        """,
    )
    fun observeOutstandingForProject(projectId: String): Flow<Double>
}
