package il.co.tradesmanager.data.catalog

import il.co.tradesmanager.core.i18n.searchable
import il.co.tradesmanager.data.local.dao.AuditDao
import il.co.tradesmanager.data.local.dao.CatalogDao
import il.co.tradesmanager.data.local.dao.InventoryDao
import il.co.tradesmanager.data.local.entity.AuditLogEntity
import il.co.tradesmanager.data.local.entity.CatalogItemEntity
import il.co.tradesmanager.data.local.entity.ChecklistTemplateEntity
import il.co.tradesmanager.data.local.entity.ChecklistTemplateItemEntity
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import il.co.tradesmanager.data.local.entity.TradeEntity
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns the shipped JSON catalogues into rows the app can work with.
 *
 * Two separate jobs, deliberately kept apart:
 *
 *  - [loadReferenceData] fills the read-only catalogue and safety tables. It
 *    runs on every launch where the catalogue version has moved, and it never
 *    touches anything a user typed.
 *  - [stockTrades] copies catalogue rows into the user's own inventory when a
 *    trade is picked. It skips any catalogue id already stocked, so picking a
 *    trade twice, re-running onboarding, or applying a catalogue update can
 *    never produce a second copy of an item whose quantity someone has been
 *    keeping.
 */
class CatalogSeeder(
    private val source: CatalogSource,
    private val catalogDao: CatalogDao,
    private val inventoryDao: InventoryDao,
    private val auditDao: AuditDao,
) {

    data class SeedReport(val trades: Int, val catalogItems: Int, val checklists: Int, val version: Int)

    suspend fun loadReferenceData(): SeedReport = withContext(Dispatchers.IO) {
        val manifest = source.manifest()

        catalogDao.upsertTrades(
            manifest.trades.mapIndexed { index, trade ->
                TradeEntity(
                    id = trade.id,
                    names = trade.names,
                    icon = trade.icon,
                    colorHex = trade.colorHex,
                    sortOrder = index,
                )
            },
        )

        var itemCount = 0
        var checklistCount = 0

        manifest.trades.forEach { trade ->
            val itemFile = source.decode<CatalogItemFile>(trade.itemsFile)
            catalogDao.upsertCatalogItems(
                itemFile.items.map { it.toEntity(trade.id, itemFile.catalogVersion) },
            )
            itemCount += itemFile.items.size

            trade.safetyFile?.let { path ->
                val safety = source.decode<SafetyFile>(path)
                catalogDao.upsertChecklistTemplates(
                    safety.checklists.map { checklist ->
                        ChecklistTemplateEntity(
                            id = checklist.id,
                            tradeId = trade.id,
                            titles = checklist.titles,
                            mandatoryBeforeWork = checklist.mandatoryBeforeWork,
                            references = checklist.references,
                            isSeeded = true,
                            catalogVersion = safety.catalogVersion,
                        )
                    },
                )
                safety.checklists.forEach { checklist ->
                    catalogDao.upsertChecklistTemplateItems(
                        checklist.items.mapIndexed { index, check ->
                            ChecklistTemplateItemEntity(
                                id = check.id,
                                templateId = checklist.id,
                                texts = check.texts,
                                critical = check.critical,
                                sortOrder = index,
                            )
                        },
                    )
                }
                checklistCount += safety.checklists.size
            }
        }

        record("SEED", "catalog", "manifest", "Loaded catalogue v${manifest.catalogVersion}: $itemCount items")
        SeedReport(manifest.trades.size, itemCount, checklistCount, manifest.catalogVersion)
    }

    /**
     * Stocks the user's inventory from the catalogues of [tradeIds].
     *
     * Returns how many rows were actually added — zero on a repeat run, which
     * is the whole point of the duplicate guard.
     */
    suspend fun stockTrades(tradeIds: Collection<String>): Int = withContext(Dispatchers.IO) {
        if (tradeIds.isEmpty()) return@withContext 0

        val alreadyStocked = inventoryDao.seededCatalogItemIds().toHashSet()
        val manifest = source.manifest()
        val now = System.currentTimeMillis()

        val newRows = manifest.trades
            .filter { it.id in tradeIds }
            .flatMap { trade ->
                source.decode<CatalogItemFile>(trade.itemsFile).items
                    .filterNot { it.id in alreadyStocked }
                    .map { it.toInventoryRow(trade.id, now) }
            }

        if (newRows.isNotEmpty()) {
            inventoryDao.upsertAll(newRows)
            record(
                action = "SEED",
                type = "inventory",
                id = tradeIds.joinToString(","),
                summary = "Stocked ${newRows.size} catalogue items",
            )
        }
        newRows.size
    }

    private suspend fun record(action: String, type: String, id: String, summary: String) {
        auditDao.insert(
            AuditLogEntity(
                id = UUID.randomUUID().toString(),
                entityType = type,
                entityId = id,
                action = action,
                actorId = null,
                actorName = SYSTEM_ACTOR,
                summary = summary,
                occurredAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun CatalogItemDto.toEntity(tradeId: String, version: Int) = CatalogItemEntity(
        id = id,
        tradeId = tradeId,
        kind = kind,
        category = category,
        unit = unit,
        names = names,
        spec = spec,
        attributes = attributes,
        tags = tags,
        catalogVersion = version,
        searchIndex = buildSearchIndex(),
    )

    private fun CatalogItemDto.toInventoryRow(tradeId: String, now: Long) = InventoryItemEntity(
        id = UUID.randomUUID().toString(),
        catalogItemId = id,
        tradeId = tradeId,
        kind = kind,
        category = category,
        unit = unit,
        names = names,
        spec = spec,
        attributes = attributes,
        tags = tags,
        // Seeded rows start empty: the catalogue says what exists in the trade,
        // not what is in this user's van. Counting is theirs to do.
        quantity = 0.0,
        minStock = 0.0,
        supplierId = null,
        purchasePrice = null,
        barcode = null,
        searchIndex = buildSearchIndex(),
        createdAt = now,
        updatedAt = now,
    )

    private fun CatalogItemDto.buildSearchIndex(): String =
        buildString {
            append(names.searchable()).append(' ')
            append(spec.searchable()).append(' ')
            append(tags.joinToString(" ") { it.lowercase() }).append(' ')
            append(category.lowercase()).append(' ')
            append(attributes.values.joinToString(" ") { it.lowercase() })
        }.trim()

    private companion object {
        const val SYSTEM_ACTOR = "system"
    }
}
