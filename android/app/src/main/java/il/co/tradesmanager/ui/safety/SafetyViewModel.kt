package il.co.tradesmanager.ui.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.local.entity.ChecklistTemplateEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SafetyViewModel(private val container: AppContainer) : ViewModel() {

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )


    /**
     * Whether a roll call is running right now.
     *
     * On this screen rather than only on the roll call screen, because the
     * person who started it walks away from their phone and comes back to
     * whichever page it was left on. An evacuation that is still open has to
     * be visible from the safety lens, not only from inside itself.
     */
    val rollCallRunning: StateFlow<Boolean> = container.musters.observeLive()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val tradeIds = MutableStateFlow<List<String>>(emptyList())

    init {
        viewModelScope.launch { tradeIds.value = container.catalogDao.selectedTradeIds() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val templates: StateFlow<List<ChecklistTemplateEntity>> = tradeIds
        .flatMapLatest { ids ->
            // Emit an empty list rather than nothing, so the screen can show its
            // "no checklists for your trades" state instead of staying blank.
            if (ids.isEmpty()) flowOf(emptyList()) else container.safety.observeTemplates(ids)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
