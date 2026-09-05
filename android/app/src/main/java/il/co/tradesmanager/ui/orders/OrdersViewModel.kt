package il.co.tradesmanager.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.PurchaseOrderEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OrdersViewModel(private val container: AppContainer) : ViewModel() {

    val orders: StateFlow<List<PurchaseOrderEntity>> = container.purchasing.observeOrders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val projects: StateFlow<List<ProjectEntity>> = container.projects.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    /** Hands the new order's id back so the caller can open it straight away. */
    fun raise(projectId: String?, supplierName: String, onRaised: (String) -> Unit) =
        viewModelScope.launch {
            val actor = container.settings.settings.first().actorName
            onRaised(container.purchasing.raise(projectId, supplierName, actor).id)
        }
}
