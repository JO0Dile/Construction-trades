package il.co.tradesmanager.data.repository

import il.co.tradesmanager.data.local.dao.InventoryDao
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import il.co.tradesmanager.data.local.entity.StockMovementEntity
import il.co.tradesmanager.data.local.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.yield

/**
 * The stock tables, in memory.
 *
 * [item] yields before it answers, on purpose. Reading the count and writing
 * it back are two suspending calls with a suspension point between them, and
 * that gap is where two taps on the plus button used to overwrite each other.
 * A fake that answered instantly would never let the two coroutines interleave
 * and the test would pass over a repository that still lost half the taps.
 */
class FakeInventoryDao : InventoryDao {

    // Named rows, not items, so the list parameter of upsertAll cannot shadow it.
    private val rows = MutableStateFlow<List<InventoryItemEntity>>(emptyList())
    val movements = mutableListOf<StockMovementEntity>()

    override suspend fun item(id: String): InventoryItemEntity? {
        yield()
        return rows.value.firstOrNull { it.id == id }
    }

    override suspend fun setQuantity(id: String, quantity: Double, now: Long) {
        yield()
        rows.value = rows.value.map {
            if (it.id == id) it.copy(quantity = quantity, updatedAt = now) else it
        }
    }

    override suspend fun insertMovement(movement: StockMovementEntity) {
        movements += movement
    }

    override suspend fun upsert(item: InventoryItemEntity) {
        rows.value = rows.value.filterNot { it.id == item.id } + item
    }

    override suspend fun upsertAll(items: List<InventoryItemEntity>) {
        items.forEach { upsert(it) }
    }

    override suspend fun softDelete(id: String, now: Long) {
        rows.value = rows.value.map {
            if (it.id == id) it.copy(deletedAt = now, updatedAt = now) else it
        }
    }

    override fun observeItem(id: String): Flow<InventoryItemEntity?> =
        rows.map { all -> all.firstOrNull { it.id == id } }

    override fun observeItems(
        kind: String?,
        lowStockOnly: Boolean,
        stageId: String?,
    ): Flow<List<InventoryItemEntity>> = rows.map { all ->
        all.filter { it.deletedAt == null }
            .filter { kind == null || it.kind == kind }
            .filter { !lowStockOnly || (it.minStock > 0.0 && it.quantity <= it.minStock) }
    }

    override fun observeLowStock(): Flow<List<InventoryItemEntity>> = rows.map { all ->
        all.filter { it.deletedAt == null && it.minStock > 0.0 && it.quantity <= it.minStock }
    }

    override fun observeMovements(itemId: String): Flow<List<StockMovementEntity>> =
        MutableStateFlow(movements.filter { it.itemId == itemId })

    override suspend fun itemByBarcode(barcode: String): InventoryItemEntity? =
        rows.value.firstOrNull { it.barcode == barcode && it.deletedAt == null }

    override suspend fun seededCatalogItemIds(): List<String> =
        rows.value.mapNotNull { it.catalogItemId }

    override suspend fun upsertSupplier(supplier: SupplierEntity) = Unit

    override fun observeSuppliers(): Flow<List<SupplierEntity>> = MutableStateFlow(emptyList())
}
