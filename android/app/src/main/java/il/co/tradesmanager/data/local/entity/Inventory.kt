package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import il.co.tradesmanager.core.i18n.LocalizedText

/**
 * A row in this user's own stock list.
 *
 * [catalogItemId] links back to the shipped catalogue when the row came from
 * it, which is what stops a re-seed creating a second "NYY cable 3×1.5": the
 * seeder skips any catalogue id already present. A hand-typed item has a null
 * link and is never touched by seeding.
 */
@Entity(
    tableName = "inventory_items",
    indices = [Index("catalogItemId"), Index("tradeId"), Index("barcode"), Index("searchIndex")],
)
data class InventoryItemEntity(
    @PrimaryKey val id: String,
    val catalogItemId: String?,
    val tradeId: String?,
    val kind: String,
    val category: String,
    val unit: String,
    val names: LocalizedText,
    val spec: LocalizedText,
    val attributes: Map<String, String>,
    val tags: List<String>,
    val quantity: Double,
    val minStock: Double,
    val supplierId: String?,
    val purchasePrice: Double?,
    val barcode: String?,
    val searchIndex: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Soft delete: an audited app never loses the row that explains a movement. */
    val deletedAt: Long? = null,
) {
    val isLowStock: Boolean get() = minStock > 0.0 && quantity <= minStock
    val isCustom: Boolean get() = catalogItemId == null
}

/** Append-only record of every stock change: who, when, how much and why. */
@Entity(tableName = "stock_movements", indices = [Index("itemId"), Index("occurredAt")])
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val delta: Double,
    val resultingQuantity: Double,
    val reason: String,
    val projectId: String?,
    val actorId: String?,
    val actorName: String,
    val occurredAt: Long,
)

@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val notes: String? = null,
)

/**
 * A photo attached to an item, a project, a checklist answer or an incident.
 * [uri] points at app-private storage; nothing is written to shared media.
 */
@Entity(tableName = "photos", indices = [Index("ownerType", "ownerId")])
data class PhotoEntity(
    @PrimaryKey val id: String,
    val ownerType: String,
    val ownerId: String,
    val uri: String,
    val capturedAt: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val note: String? = null,
)
