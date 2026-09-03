package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.i18n.LocalizedText
import il.co.tradesmanager.core.i18n.searchable
import il.co.tradesmanager.data.local.dao.InventoryDao
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import il.co.tradesmanager.data.local.entity.StockMovementEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class InventoryRepository(
    private val dao: InventoryDao,
    private val audit: AuditTrail,
) {

    fun observe(query: String, kind: String?, lowStockOnly: Boolean): Flow<List<InventoryItemEntity>> =
        dao.observeItems(query.trim().lowercase(), kind, lowStockOnly)

    fun observeLowStock(): Flow<List<InventoryItemEntity>> = dao.observeLowStock()

    fun observeItem(id: String): Flow<InventoryItemEntity?> = dao.observeItem(id)

    fun observeMovements(itemId: String): Flow<List<StockMovementEntity>> = dao.observeMovements(itemId)

    suspend fun findByBarcode(barcode: String): InventoryItemEntity? = dao.itemByBarcode(barcode.trim())

    suspend fun save(item: InventoryItemEntity, actorName: String): InventoryItemEntity {
        val now = System.currentTimeMillis()
        val existing = dao.item(item.id)
        val toStore = item.copy(
            searchIndex = searchIndexFor(item.names, item.spec, item.tags, item.category, item.barcode),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        dao.upsert(toStore)
        audit.record(
            entityType = ENTITY,
            entityId = toStore.id,
            action = if (existing == null) AuditTrail.Action.CREATE else AuditTrail.Action.UPDATE,
            actorName = actorName,
            summary = toStore.names.values.firstOrNull().orEmpty(),
        )
        return toStore
    }

    suspend fun delete(id: String, actorName: String) {
        val now = System.currentTimeMillis()
        dao.softDelete(id, now)
        audit.record(ENTITY, id, AuditTrail.Action.DELETE, actorName, "Item removed from inventory")
    }

    /**
     * Moves stock and writes the movement in the same call, so a quantity can
     * never change without a row saying who changed it and why. Stock is
     * clamped at zero: a van cannot hold minus three sockets, and a negative
     * figure would quietly corrupt every cost report built on it.
     */
    suspend fun adjustStock(
        itemId: String,
        delta: Double,
        reason: String,
        actorName: String,
        projectId: String? = null,
    ): Double {
        val item = dao.item(itemId) ?: return 0.0
        val now = System.currentTimeMillis()
        val resulting = (item.quantity + delta).coerceAtLeast(0.0)

        dao.setQuantity(itemId, resulting, now)
        dao.insertMovement(
            StockMovementEntity(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                delta = resulting - item.quantity,
                resultingQuantity = resulting,
                reason = reason,
                projectId = projectId,
                actorId = null,
                actorName = actorName.ifBlank { "unknown" },
                occurredAt = now,
            ),
        )
        audit.record(
            entityType = ENTITY,
            entityId = itemId,
            action = AuditTrail.Action.STOCK_CHANGE,
            actorName = actorName,
            summary = "${item.quantity} -> $resulting ($reason)",
        )
        return resulting
    }

    private fun searchIndexFor(
        names: LocalizedText,
        spec: LocalizedText,
        tags: List<String>,
        category: String,
        barcode: String?,
    ): String = buildString {
        append(names.searchable()).append(' ')
        append(spec.searchable()).append(' ')
        append(tags.joinToString(" ") { it.lowercase() }).append(' ')
        append(category.lowercase()).append(' ')
        append(barcode.orEmpty().lowercase())
    }.trim()

    private companion object {
        const val ENTITY = "inventory_item"
    }
}
