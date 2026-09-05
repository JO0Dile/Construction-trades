package il.co.tradesmanager.data.repository

import il.co.tradesmanager.data.local.dao.ProjectDao
import il.co.tradesmanager.data.local.dao.PurchasingDao
import il.co.tradesmanager.data.local.entity.PurchaseOrderEntity
import il.co.tradesmanager.data.local.entity.PurchaseOrderLineEntity
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Ordering materials, and receiving them.
 *
 * The Stuff lens, feeding Money. An order is the point where a job's shopping
 * list stops being an intention and becomes a commitment at a price somebody
 * actually agreed — which is why the Money lens counts an open order at the
 * order's own price rather than at an estimate from stock, and stops counting
 * the estimate for the same materials at the same time.
 *
 * Receiving is where the "entered once" rule does the most work: one delivery
 * moves the order forward, puts the goods into stock, and marks them allocated
 * against the job. Doing those three things by hand in three places is how the
 * three of them end up disagreeing.
 */
class PurchasingRepository(
    private val dao: PurchasingDao,
    private val projects: ProjectDao,
    private val inventory: InventoryRepository,
    private val audit: AuditTrail,
) {

    object Status {
        /** A shopping list. Commits nothing. */
        const val DRAFT = "DRAFT"
        const val ORDERED = "ORDERED"
        const val PART_RECEIVED = "PART_RECEIVED"
        const val RECEIVED = "RECEIVED"
        /** A conversation that ended. Commits nothing. */
        const val CANCELLED = "CANCELLED"
    }

    fun observeOrders(): Flow<List<PurchaseOrderEntity>> = dao.observeOrders()

    fun observeOrdersForProject(projectId: String): Flow<List<PurchaseOrderEntity>> =
        dao.observeOrdersForProject(projectId)

    fun observeOrder(id: String): Flow<PurchaseOrderEntity?> = dao.observeOrder(id)

    fun observeLines(orderId: String): Flow<List<PurchaseOrderLineEntity>> =
        dao.observeLines(orderId)

    /** What is on order for a job and not yet delivered, at the agreed price. */
    fun observeOutstanding(projectId: String): Flow<Double> =
        dao.observeOutstandingForProject(projectId)

    suspend fun raise(
        projectId: String?,
        supplierName: String,
        actorName: String,
    ): PurchaseOrderEntity {
        val now = System.currentTimeMillis()
        val order = PurchaseOrderEntity(
            id = UUID.randomUUID().toString(),
            reference = String.format(Locale.ROOT, "PO-%03d", dao.orderCount() + 1),
            projectId = projectId,
            supplierId = null,
            supplierName = supplierName.trim(),
            status = Status.DRAFT,
            orderedOn = null,
            expectedOn = null,
            notes = null,
            createdBy = actorName,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertOrder(order)
        audit.record(ENTITY, order.id, AuditTrail.Action.CREATE, actorName, order.reference)
        return order
    }

    suspend fun addLine(
        orderId: String,
        label: String,
        unit: String,
        quantity: Double,
        unitPrice: Double,
        catalogItemId: String?,
        inventoryItemId: String?,
        actorName: String,
    ) {
        val line = PurchaseOrderLineEntity(
            id = UUID.randomUUID().toString(),
            orderId = orderId,
            catalogItemId = catalogItemId,
            inventoryItemId = inventoryItemId,
            label = label.trim(),
            unit = unit,
            quantityOrdered = quantity,
            quantityReceived = 0.0,
            unitPrice = unitPrice,
            sortOrder = dao.nextLineSortOrder(orderId),
        )
        dao.upsertLine(line)
        touch(orderId)
        audit.record("purchase_order_line", line.id, AuditTrail.Action.CREATE, actorName, line.label)
    }

    suspend fun removeLine(line: PurchaseOrderLineEntity, actorName: String) {
        dao.deleteLine(line)
        touch(line.orderId)
        audit.record("purchase_order_line", line.id, AuditTrail.Action.DELETE, actorName, line.label)
    }

    /** Sends the order. From here it commits money, so a draft cannot. */
    suspend fun place(order: PurchaseOrderEntity, actorName: String) {
        val now = System.currentTimeMillis()
        dao.upsertOrder(order.copy(status = Status.ORDERED, orderedOn = now, updatedAt = now))
        audit.record(ENTITY, order.id, AuditTrail.Action.UPDATE, actorName, "${order.reference} placed")
    }

    suspend fun cancel(order: PurchaseOrderEntity, actorName: String) {
        dao.upsertOrder(order.copy(status = Status.CANCELLED, updatedAt = System.currentTimeMillis()))
        audit.record(ENTITY, order.id, AuditTrail.Action.UPDATE, actorName, "${order.reference} cancelled")
    }

    suspend fun delete(order: PurchaseOrderEntity, actorName: String) {
        dao.deleteOrder(order)
        audit.record(ENTITY, order.id, AuditTrail.Action.DELETE, actorName, order.reference)
    }

    /**
     * Books a delivery in.
     *
     * One action, three consequences, because they are one event on a site:
     * the line moves forward, the goods go into stock, and the job's material
     * line is marked allocated so the Money lens stops forecasting materials
     * that are now standing on the slab.
     *
     * Over-delivery is allowed — suppliers do send fifty-two when you ordered
     * fifty — but never counts for more than was ordered when the outstanding
     * figure is worked out, or a generous supplier would credit the job.
     */
    suspend fun receive(
        line: PurchaseOrderLineEntity,
        quantity: Double,
        actorName: String,
    ) {
        if (quantity <= 0.0) return
        val order = dao.order(line.orderId) ?: return

        dao.upsertLine(line.copy(quantityReceived = line.quantityReceived + quantity))

        line.inventoryItemId?.let { itemId ->
            inventory.adjustStock(
                itemId = itemId,
                delta = quantity,
                reason = "Delivered on ${order.reference}",
                actorName = actorName,
                projectId = order.projectId,
            )
        }

        order.projectId?.let { projectId -> allocateAgainstJob(projectId, line, quantity) }

        refreshStatus(order.id, actorName)
        audit.record(
            "purchase_order_line", line.id, AuditTrail.Action.UPDATE, actorName,
            "Received $quantity ${line.unit} of ${line.label}",
        )
    }

    /**
     * Marks delivered goods against the job's own material line, matched by
     * catalogue item. Without this the job sheet keeps asking for materials
     * that arrived this morning.
     */
    private suspend fun allocateAgainstJob(
        projectId: String,
        line: PurchaseOrderLineEntity,
        quantity: Double,
    ) {
        val catalogItemId = line.catalogItemId ?: return
        val material = projects.materialsFor(projectId)
            .firstOrNull { it.catalogItemId == catalogItemId } ?: return
        projects.upsertMaterials(
            listOf(material.copy(allocatedQuantity = material.allocatedQuantity + quantity)),
        )
    }

    /**
     * An order is received when every line is, part received when some of it
     * is, and otherwise left alone — so a delivery cannot quietly un-cancel an
     * order somebody cancelled.
     */
    private suspend fun refreshStatus(orderId: String, actorName: String) {
        val order = dao.order(orderId) ?: return
        if (order.status == Status.CANCELLED || order.status == Status.DRAFT) return

        val lines = dao.lines(orderId)
        val complete = lines.isNotEmpty() && lines.all { it.quantityReceived >= it.quantityOrdered }
        val any = lines.any { it.quantityReceived > 0.0 }
        val status = when {
            complete -> Status.RECEIVED
            any -> Status.PART_RECEIVED
            else -> Status.ORDERED
        }
        if (status != order.status) {
            dao.upsertOrder(order.copy(status = status, updatedAt = System.currentTimeMillis()))
            audit.record(
                ENTITY, order.id, AuditTrail.Action.UPDATE, actorName,
                "${order.reference} ${status.lowercase().replace('_', ' ')}",
            )
        }
    }

    private suspend fun touch(orderId: String) {
        dao.order(orderId)?.let { dao.upsertOrder(it.copy(updatedAt = System.currentTimeMillis())) }
    }

    private companion object {
        const val ENTITY = "purchase_order"
    }
}
