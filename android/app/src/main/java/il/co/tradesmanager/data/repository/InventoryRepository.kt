package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.find.Search
import il.co.tradesmanager.core.i18n.LocalizedText
import il.co.tradesmanager.core.i18n.searchable
import il.co.tradesmanager.data.local.dao.InventoryDao
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import il.co.tradesmanager.data.local.entity.StockMovementEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InventoryRepository(
    private val dao: InventoryDao,
    private val audit: AuditTrail,
) {

    /**
     * The stock list, narrowed by what somebody typed into the box on it.
     *
     * The typing is matched here rather than in SQL, and that is the whole
     * point of the change: the query used to be a LIKE over a lowercased
     * index, which folds case for ASCII and nothing else. An item entered with
     * Arabic harakat was invisible to anybody typing it without them, a name
     * with Hebrew points likewise, and a foreman typing the Arabic-Indic
     * digits his own screen had just shown him found nothing at all. Every one
     * of those looked like an empty catalogue rather than a broken search.
     *
     * One letter is enough here. That is not what the whole-app search does —
     * see [Search.terms] for why the two boxes want different floors.
     *
     * Matched on every name an item has rather than the one being displayed,
     * so a storeman searching in Hebrew finds the box somebody labelled in
     * English. Ordering: the best match first when there is something to
     * match, and otherwise exactly what the query returned, which puts low
     * stock at the top. The sort is stable, so items that score alike keep
     * that order between them.
     */
    fun observe(
        query: String,
        kind: String?,
        lowStockOnly: Boolean,
        stageId: String? = null,
    ): Flow<List<InventoryItemEntity>> {
        val terms = Search.terms(query, shortest = 1)
        return dao.observeItems(kind, lowStockOnly, stageId).map { rows ->
            if (terms.isEmpty()) {
                rows
            } else {
                rows.map { item ->
                    item to Search.score(terms, item.names.searchable(), alsoMatch(item))
                }
                    .filter { (_, score) -> score > 0 }
                    .sortedByDescending { (_, score) -> score }
                    .map { (item, _) -> item }
            }
        }
    }

    /** Everything about an item worth matching on beyond its names. */
    private fun alsoMatch(item: InventoryItemEntity): String = listOfNotNull(
        item.spec.searchable(),
        item.searchIndex,
        item.barcode,
    ).joinToString(" ")

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
        audit.record(ENTITY, id, AuditTrail.Action.DELETE, actorName, Summaries.ITEM_REMOVED)
    }

    /**
     * Adjustments are serialised.
     *
     * Reading the count and writing it back are two calls, and every plus and
     * minus in the app is a tap that starts its own coroutine. Two taps a few
     * milliseconds apart both read the same number and both wrote one more
     * than it, so five taps on the plus put three on the shelf -- silently,
     * with a movement row for each tap that agreed with itself and not with
     * the total. That is the bug somebody reports as "the buttons do not
     * work", and no amount of pressing them harder fixes it.
     *
     * A mutex rather than a transaction, for the same reason AuditTrail uses
     * one: the ordering of the read and the write is what matters, and it
     * holds within this process, which is where every writer is.
     */
    private val adjusting = Mutex()

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
    ): Double = adjusting.withLock {
        val item = dao.item(itemId) ?: return@withLock 0.0
        val now = System.currentTimeMillis()
        val resulting = (item.quantity + delta).coerceAtLeast(0.0)

        // Nothing moved, so nothing is recorded. Pressing minus on an empty
        // shelf used to write a movement of zero and an audit line saying the
        // count went from nought to nought -- so the one place a foreman goes
        // to ask where his stock went filled up with rows about nothing, and
        // on the screen the button still looked broken.
        if (resulting == item.quantity) return@withLock item.quantity

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
            summary = Summary.of(
                Summaries.STOCK_MOVED,
                Summary.number(item.quantity),
                Summary.number(resulting),
                Summary.nest(reason),
            ),
        )
        resulting
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
