package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import il.co.tradesmanager.data.local.entity.StockMovementEntity
import il.co.tradesmanager.data.local.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    /**
     * One query serves the whole list screen. An empty [query] matches
     * everything, [kind] filters the chips, and [lowStockOnly] uses the same
     * rule the row badge does, so the filter and the badge can never disagree.
     */
    @Query(
        """
        SELECT * FROM inventory_items
        WHERE deletedAt IS NULL
          AND (:query = '' OR searchIndex LIKE '%' || :query || '%')
          AND (:kind IS NULL OR kind = :kind)
          AND (:lowStockOnly = 0 OR (minStock > 0 AND quantity <= minStock))
        ORDER BY (minStock > 0 AND quantity <= minStock) DESC, updatedAt DESC
        """,
    )
    fun observeItems(query: String, kind: String?, lowStockOnly: Boolean): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE deletedAt IS NULL AND minStock > 0 AND quantity <= minStock")
    fun observeLowStock(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items WHERE id = :id")
    fun observeItem(id: String): Flow<InventoryItemEntity?>

    @Query("SELECT * FROM inventory_items WHERE id = :id")
    suspend fun item(id: String): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE barcode = :barcode AND deletedAt IS NULL LIMIT 1")
    suspend fun itemByBarcode(barcode: String): InventoryItemEntity?

    /** Ids already stocked from the catalogue — the seeder's duplicate guard. */
    @Query("SELECT catalogItemId FROM inventory_items WHERE catalogItemId IS NOT NULL")
    suspend fun seededCatalogItemIds(): List<String>

    @Upsert
    suspend fun upsert(item: InventoryItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<InventoryItemEntity>)

    @Query("UPDATE inventory_items SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE inventory_items SET quantity = :quantity, updatedAt = :now WHERE id = :id")
    suspend fun setQuantity(id: String, quantity: Double, now: Long)

    @Insert
    suspend fun insertMovement(movement: StockMovementEntity)

    @Query("SELECT * FROM stock_movements WHERE itemId = :itemId ORDER BY occurredAt DESC LIMIT 100")
    fun observeMovements(itemId: String): Flow<List<StockMovementEntity>>

    @Upsert
    suspend fun upsertSupplier(supplier: SupplierEntity)

    @Query("SELECT * FROM suppliers ORDER BY name")
    fun observeSuppliers(): Flow<List<SupplierEntity>>
}
