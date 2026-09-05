package il.co.tradesmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.i18n.LocaleController
import il.co.tradesmanager.data.local.entity.TradeEntity
import il.co.tradesmanager.data.repository.SettingsRepository
import il.co.tradesmanager.data.repository.TradeRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<SettingsRepository.Settings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.Settings())

    val trades: StateFlow<List<TradeEntity>> = container.catalogDao.observeTrades()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _reseedResult = MutableStateFlow<Int?>(null)
    val reseedResult: StateFlow<Int?> = _reseedResult.asStateFlow()

    /** Reported in the security section when SQLCipher could not be loaded. */
    val databaseIsEncrypted: Boolean = container.databaseIsEncrypted

    fun setLanguage(tag: String) = LocaleController.apply(tag)

    fun setThemeMode(mode: SettingsRepository.ThemeMode) = viewModelScope.launch {
        container.settings.setThemeMode(mode)
    }

    fun setLargeText(value: Boolean) = viewModelScope.launch { container.settings.setLargeText(value) }

    fun setActorName(name: String) = viewModelScope.launch { container.settings.setActorName(name) }

    fun toggleTrade(tradeId: String, selected: Boolean) = viewModelScope.launch {
        container.catalogDao.setTradeSelected(tradeId, selected)
        // Turning a trade on stocks its catalogue; turning one off never
        // removes stock, because the quantities are the user's own record.
        if (selected) container.seeder.stockTrades(listOf(tradeId))
    }

    /**
     * A trade the app ships nothing for. It arrives selected and empty, and
     * the user stocks it from their own items — which is the only honest
     * thing to do, since we have no catalogue to give them for it.
     */
    fun addTrade(name: String, languageTag: String) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.trades.createCustom(name, languageTag, actor)
    }

    fun deleteTrade(trade: TradeEntity) = viewModelScope.launch {
        container.trades.delete(trade, container.settings.settings.first().actorName)
    }

    fun isCustom(trade: TradeEntity): Boolean = TradeRepository.isCustom(trade)

    fun reseed() = viewModelScope.launch {
        val ids = container.catalogDao.selectedTradeIds()
        container.seeder.loadReferenceData()
        _reseedResult.value = container.seeder.stockTrades(ids)
    }

    /**
     * Account and data deletion, required in-app by both stores for an account
     * that can be created in-app. Clears preferences and every local table;
     * the encryption key is dropped last so nothing readable is left behind.
     */
    fun deleteEverything(onDone: () -> Unit) = viewModelScope.launch {
        container.database.clearAllTables()
        container.settings.clearAll()
        onDone()
    }
}
