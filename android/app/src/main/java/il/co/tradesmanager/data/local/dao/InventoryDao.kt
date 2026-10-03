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
     * One query serves the whole list screen. [kind] filters the chips, and
     * [lowStockOnly] uses the same rule the row badge does, so the filter and
     * the badge can never disagree.
     *
     * Newest first, and nothing else moves.
     *
     * It used to put every low-stock row above everything else and then sort
     * by updatedAt. That had two faults and both were reported. A new item is
     * not low stock, so it landed below the whole low-stock block — forty rows
     * down on a real van, which reads as "it went to the bottom". And sorting
     * on updatedAt meant pressing + on a row rewrote its updatedAt and threw
     * it to the top of the list, under the finger that pressed it.
     *
     * createdAt does neither: a new item is always first, and adjusting a
     * quantity or correcting a name moves nothing. Low stock is still found —
     * it has its own filter and its own badge, which is where urgency belongs
     * rather than in the resting order of the list. The id breaks ties so the
     * seeded catalogue, which shares one timestamp, is at least stable.
     *
     * It does not take what somebody typed. It used to, as a LIKE over the
     * search index, and that was quietly broken for two of the three languages
     * this app ships in: LIKE in SQLite folds case for ASCII and nothing else,
     * so a name stored with Hebrew points or Arabic harakat was never found by
     * anybody typing it without them, and no amount of SQL takes a mark off a
     * letter. The words are matched in Kotlin now, in InventoryRepository,
     * against the same folding the whole-app search uses.
     *
     * [stageId] filters by the stage of the job somebody is on: an electrician
     * doing slab conduit does not want to scroll past ten light fittings to
     * reach the conduit. The stages live on the catalogue row rather than on a
     * copy here, joined through catalogItemId, so a catalogue correction
     * reaches everybody at the next load instead of only new installs.
     *
     * Three ways to be shown under every stage, and all three are deliberate:
     * no catalogue row at all (something this user typed in themselves), an
     * empty stage list (the default, and what every row held before the column
     * existed), or a list naming this stage. The failure mode is a list that
     * is too long, never a plumber's own pipes disappearing on them.
     *
     * The stage id is matched with its quotes because the column holds a JSON
     * array. A bare LIKE would let a future stage id that contains another one
     * match both.
     */
    @Query(
        """
        SELECT i.* FROM inventory_items AS i
        LEFT JOIN catalog_items AS c ON c.id = i.catalogItemId
        WHERE i.deletedAt IS NULL
          AND (:kind IS NULL OR i.kind = :kind)
          AND (:lowStockOnly = 0 OR (i.minStock > 0 AND i.quantity <= i.minStock))
          AND (
            :stageId IS NULL
            OR c.stages IS NULL
            OR c.stages = '[]'
            OR c.stages LIKE '%"' || :stageId || '"%'
          )
        ORDER BY i.createdAt DESC, i.id
        """,
    )
    fun observeItems(
        kind: String?,
        lowStockOnly: Boolean,
        stageId: String?,
    ): Flow<List<InventoryItemEntity>>

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
