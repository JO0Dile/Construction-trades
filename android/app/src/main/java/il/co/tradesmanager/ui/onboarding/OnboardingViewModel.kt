package il.co.tradesmanager.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.i18n.LocaleController
import il.co.tradesmanager.data.local.entity.TradeEntity
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    data class State(
        val selectedTrades: Set<String> = emptySet(),
        val isWorking: Boolean = false,
        val seededCount: Int? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val trades: StateFlow<List<TradeEntity>> = container.catalogDao.observeTrades()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun chooseLanguage(tag: String) = LocaleController.apply(tag)

    fun toggleTrade(tradeId: String) {
        _state.value = _state.value.let { current ->
            val next = current.selectedTrades.toMutableSet()
            if (!next.add(tradeId)) next.remove(tradeId)
            current.copy(selectedTrades = next)
        }
    }

    /**
     * Loads the catalogues for the chosen trades and finishes onboarding.
     *
     * Reference data is loaded first in case the app's very first launch got
     * here before the background load in [il.co.tradesmanager.TradesManagerApp]
     * finished — the seeder is idempotent, so running it twice costs a little
     * work and changes nothing.
     */
    fun finish(onDone: () -> Unit) {
        val chosen = _state.value.selectedTrades
        if (chosen.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isWorking = true)
            val report = container.seeder.loadReferenceData()
            container.catalogDao.replaceSelection(chosen, trades.value.map { it.id })
            val stocked = container.seeder.stockTrades(chosen)
            container.settings.setSeededCatalogVersion(report.version)
            container.settings.setOnboardingComplete(true)
            _state.value = _state.value.copy(isWorking = false, seededCount = stocked)
            onDone()
        }
    }
}
