package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.i18n.LocalizedText
import il.co.tradesmanager.data.local.dao.CatalogDao
import il.co.tradesmanager.data.local.entity.TradeEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Trades, including the ones the app does not ship a catalogue for.
 *
 * A user's own trade is told apart by its id prefix rather than by a column.
 * That is not a shortcut: seeding upserts by the ids in the manifest, so an id
 * that is not in the manifest is never touched by a catalogue update — the
 * convention *is* the protection, and it needs no migration to add.
 */
class TradeRepository(
    private val dao: CatalogDao,
    private val audit: AuditTrail,
) {

    fun observeAll(): Flow<List<TradeEntity>> = dao.observeTrades()

    suspend fun selectedIds(): List<String> = dao.selectedTradeIds()

    /**
     * Creates a trade the app ships nothing for — roofing, landscaping,
     * glazing. It starts empty and selected, because someone who just typed
     * its name means to work in it.
     */
    suspend fun createCustom(name: String, languageTag: String, actorName: String): TradeEntity? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null

        val language = languageTag.substringBefore('-')
        val trade = TradeEntity(
            id = "$CUSTOM_PREFIX${UUID.randomUUID()}",
            names = mapOf(language to trimmed) as LocalizedText,
            icon = "construction",
            colorHex = "#7F8C8D",
            // After everything shipped, so a new trade lands at the bottom of
            // the list rather than in the middle of the familiar ones.
            sortOrder = SORT_AFTER_SHIPPED + trimmed.hashCode().and(0xFF),
            isSelected = true,
        )
        dao.upsertTrades(listOf(trade))
        audit.record("trade", trade.id, AuditTrail.Action.CREATE, actorName, trimmed)
        return trade
    }

    suspend fun rename(trade: TradeEntity, name: String, languageTag: String, actorName: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || !isCustom(trade)) return
        val language = languageTag.substringBefore('-')
        dao.upsertTrades(listOf(trade.copy(names = trade.names + (language to trimmed))))
        audit.record("trade", trade.id, AuditTrail.Action.UPDATE, actorName, trimmed)
    }

    suspend fun delete(trade: TradeEntity, actorName: String) {
        // Only a user's own trade can be removed; a shipped one would come
        // straight back on the next catalogue load anyway.
        if (!isCustom(trade)) return
        dao.deleteTrade(trade.id)
        audit.record("trade", trade.id, AuditTrail.Action.DELETE, actorName, "Custom trade removed")
    }

    companion object {
        private const val CUSTOM_PREFIX = "custom."
        private const val SORT_AFTER_SHIPPED = 1_000

        fun isCustom(trade: TradeEntity): Boolean = trade.id.startsWith(CUSTOM_PREFIX)
    }
}
