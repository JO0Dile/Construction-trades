package il.co.tradesmanager.ui.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.local.entity.ChecklistTemplateEntity
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SafetyViewModel(private val container: AppContainer) : ViewModel() {

    private val tradeIds = MutableStateFlow<List<String>>(emptyList())

    init {
        viewModelScope.launch { tradeIds.value = container.catalogDao.selectedTradeIds() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val templates: StateFlow<List<ChecklistTemplateEntity>> = tradeIds
        .flatMapLatest { ids ->
            if (ids.isEmpty()) emptyFlow() else container.safety.observeTemplates(ids)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
