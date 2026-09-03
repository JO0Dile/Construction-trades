package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import il.co.tradesmanager.core.i18n.LocalizedText

/** A trade the app ships a catalogue for, and whether this user works in it. */
@Entity(tableName = "trades")
data class TradeEntity(
    @PrimaryKey val id: String,
    val names: LocalizedText,
    val icon: String,
    val colorHex: String,
    val sortOrder: Int,
    val isSelected: Boolean = false,
)

/**
 * A pre-loaded catalogue row: read-only reference data seeded from
 * shared/assets/catalog. The user's own stock lives in [InventoryItemEntity];
 * this table stays the pristine copy so a catalogue update can be applied
 * without touching anything a user typed.
 */
@Entity(
    tableName = "catalog_items",
    foreignKeys = [
        ForeignKey(
            entity = TradeEntity::class,
            parentColumns = ["id"],
            childColumns = ["tradeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tradeId"), Index("kind"), Index("searchIndex")],
)
data class CatalogItemEntity(
    @PrimaryKey val id: String,
    val tradeId: String,
    val kind: String,
    val category: String,
    val unit: String,
    val names: LocalizedText,
    val spec: LocalizedText,
    val attributes: Map<String, String>,
    val tags: List<String>,
    val catalogVersion: Int,
    /** Every translation, spec and tag lowercased into one column: a search in
     *  Hebrew finds an item whose English name is the one the user remembers. */
    val searchIndex: String,
)

/** A safety checklist shipped for a trade, or written by a supervisor. */
@Entity(tableName = "checklist_templates", indices = [Index("tradeId")])
data class ChecklistTemplateEntity(
    @PrimaryKey val id: String,
    val tradeId: String,
    val titles: LocalizedText,
    val mandatoryBeforeWork: Boolean,
    val references: List<String>,
    val isSeeded: Boolean,
    val catalogVersion: Int,
)

@Entity(
    tableName = "checklist_template_items",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("templateId")],
)
data class ChecklistTemplateItemEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val texts: LocalizedText,
    /** A critical check that is not done blocks the sign-off entirely. */
    val critical: Boolean,
    val sortOrder: Int,
)
