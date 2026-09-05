package il.co.tradesmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.i18n.LocaleController
import il.co.tradesmanager.data.local.entity.TradeEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.data.repository.SettingsRepository
import il.co.tradesmanager.data.repository.TradeRepository
import il.co.tradesmanager.data.update.UpdateRepository
import java.io.File
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

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    fun signOut() = viewModelScope.launch { container.session.signOut() }

    private val _reseedResult = MutableStateFlow<Int?>(null)
    val reseedResult: StateFlow<Int?> = _reseedResult.asStateFlow()

    /** Reported in the security section when SQLCipher could not be loaded. */
    val databaseIsEncrypted: Boolean = container.databaseIsEncrypted

    fun setLanguage(tag: String) = LocaleController.apply(tag)

    fun setThemeMode(mode: SettingsRepository.ThemeMode) = viewModelScope.launch {
        container.settings.setThemeMode(mode)
    }

    fun setLargeText(value: Boolean) = viewModelScope.launch { container.settings.setLargeText(value) }

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

    /**
     * The update check, as the screen sees it. One flow rather than four
     * booleans, so the button can never read "check" while a download is
     * running.
     */
    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data object UpToDate : UpdateState

        /** The feed answered and is empty — not the same as no signal. */
        data object NoReleases : UpdateState
        data object Failed : UpdateState
        data class Available(val release: UpdateRepository.Release) : UpdateState
        data class Downloading(val release: UpdateRepository.Release, val fraction: Float) : UpdateState
        data class Ready(val apk: File) : UpdateState
    }

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    fun checkForUpdate() = viewModelScope.launch {
        _update.value = UpdateState.Checking
        _update.value = when (val result = container.updates.check()) {
            is UpdateRepository.Result.Available -> UpdateState.Available(result.release)
            UpdateRepository.Result.UpToDate -> UpdateState.UpToDate
            UpdateRepository.Result.NoReleases -> UpdateState.NoReleases
            UpdateRepository.Result.Unavailable -> UpdateState.Failed
        }
    }

    fun downloadUpdate(release: UpdateRepository.Release) = viewModelScope.launch {
        _update.value = UpdateState.Downloading(release, 0f)
        val apk = container.updates.download(release) { fraction ->
            _update.value = UpdateState.Downloading(release, fraction)
        }
        _update.value = if (apk == null) UpdateState.Failed else UpdateState.Ready(apk)
    }

    fun installIntent(apk: File) = container.updates.installIntent(apk)

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
