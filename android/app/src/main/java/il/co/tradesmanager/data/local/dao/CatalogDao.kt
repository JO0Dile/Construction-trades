package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.CatalogItemEntity
import il.co.tradesmanager.data.local.entity.ChecklistTemplateEntity
import il.co.tradesmanager.data.local.entity.ChecklistTemplateItemEntity
import il.co.tradesmanager.data.local.entity.TradeEntity
import kotlinx.coroutines.flow.Flow

/** Projection for [CatalogDao.observeCategories]. */
data class CatalogCategory(val id: String, val category: String)

@Dao
interface CatalogDao {

    @Upsert
    suspend fun upsertTrades(trades: List<TradeEntity>)

    @Query("SELECT * FROM trades ORDER BY sortOrder")
    fun observeTrades(): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE isSelected = 1 ORDER BY sortOrder")
    fun observeSelectedTrades(): Flow<List<TradeEntity>>

    @Query("SELECT id FROM trades WHERE isSelected = 1")
    suspend fun selectedTradeIds(): List<String>

    @Query("UPDATE trades SET isSelected = :selected WHERE id = :tradeId")
    suspend fun setTradeSelected(tradeId: String, selected: Boolean)

    @Upsert
    suspend fun upsertCatalogItems(items: List<CatalogItemEntity>)

    @Query("SELECT * FROM catalog_items WHERE tradeId IN (:tradeIds) ORDER BY category, id")
    fun observeCatalogItems(tradeIds: List<String>): Flow<List<CatalogItemEntity>>

    @Query(
        """
        SELECT * FROM catalog_items
        WHERE tradeId IN (:tradeIds) AND searchIndex LIKE '%' || :query || '%'
        ORDER BY category, id
        LIMIT 200
        """,
    )
    suspend fun searchCatalogItems(tradeIds: List<String>, query: String): List<CatalogItemEntity>

    @Query("SELECT COUNT(*) FROM catalog_items")
    suspend fun catalogItemCount(): Int

    /**
     * Just the id and category of every catalogue row. A project's material
     * lines store only the catalogue id, and the screen needs the category to
     * pick an icon; pulling two columns is cheaper than the whole table.
     */
    @Query("SELECT id, category FROM catalog_items")
    fun observeCategories(): Flow<List<CatalogCategory>>

    @Upsert
    suspend fun upsertChecklistTemplates(templates: List<ChecklistTemplateEntity>)

    @Upsert
    suspend fun upsertChecklistTemplateItems(items: List<ChecklistTemplateItemEntity>)

    @Query("SELECT * FROM checklist_templates WHERE tradeId IN (:tradeIds)")
    fun observeChecklistTemplates(tradeIds: List<String>): Flow<List<ChecklistTemplateEntity>>

    @Query("SELECT * FROM checklist_templates WHERE id = :templateId")
    suspend fun checklistTemplate(templateId: String): ChecklistTemplateEntity?

    @Query("SELECT * FROM checklist_template_items WHERE templateId = :templateId ORDER BY sortOrder")
    suspend fun checklistTemplateItems(templateId: String): List<ChecklistTemplateItemEntity>

    @Transaction
    suspend fun replaceSelection(selectedIds: Set<String>, allIds: List<String>) {
        allIds.forEach { setTradeSelected(it, it in selectedIds) }
    }
}
