package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An order placed with a supplier.
 *
 * [projectId] is nullable because not every order is for a job — a van
 * restock belongs to the yard, and forcing it onto a project would put the
 * cost somewhere it does not belong.
 *
 * [supplierName] is stored rather than only referenced. Suppliers on a site
 * are often a phone number and a name shouted across a yard; requiring a
 * supplier record before anyone can raise an order would mean orders do not
 * get raised.
 */
@Entity(
    tableName = "purchase_orders",
    indices = [Index("projectId"), Index("supplierId"), Index("status")],
)
data class PurchaseOrderEntity(
    @PrimaryKey val id: String,
    /** Site reference people say out loud: PO-01. */
    val reference: String,
    val projectId: String?,
    val supplierId: String?,
    val supplierName: String,
    /** DRAFT, ORDERED, PART_RECEIVED, RECEIVED or CANCELLED. */
    val status: String,
    val orderedOn: Long?,
    val expectedOn: Long?,
    val notes: String?,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * A line on an order.
 *
 * [quantityReceived] is a running total rather than a flag, because deliveries
 * arrive in parts: forty of the fifty lengths turn up on Tuesday and the rest
 * on Friday, and an order that can only be "received" or not cannot say that.
 *
 * [catalogItemId] is what stops the Money lens counting the same materials
 * twice — see MoneyDao.observeCommittedMaterials.
 */
@Entity(
    tableName = "purchase_order_lines",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseOrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("orderId"), Index("catalogItemId")],
)
data class PurchaseOrderLineEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val catalogItemId: String?,
    val inventoryItemId: String?,
    val label: String,
    val unit: String,
    val quantityOrdered: Double,
    val quantityReceived: Double,
    /** Net of VAT, like every other figure the Money lens reads. */
    val unitPrice: Double,
    val sortOrder: Int,
)
