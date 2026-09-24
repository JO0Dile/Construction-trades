package il.co.tradesmanager.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.money.OrderTotals
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.PurchaseOrderEntity
import il.co.tradesmanager.data.local.entity.PurchaseOrderLineEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OrderDetailViewModel(
    private val container: AppContainer,
    private val orderId: String,
) : ViewModel() {

    /**
     * Set when a write was refused. See NotSavedDialog: the answer used to be
     * thrown away, and a refused write looked like a button that did nothing.
     */
    private val _notSaved = MutableStateFlow(false)
    val notSaved: StateFlow<Boolean> = _notSaved.asStateFlow()

    fun clearNotSaved() {
        _notSaved.value = false
    }

    data class State(
        val order: PurchaseOrderEntity? = null,
        val lines: List<PurchaseOrderLineEntity> = emptyList(),
        val project: ProjectEntity? = null,
    ) {
        /** Net of VAT, like every figure the Money lens reads. */
        val total: Double
            get() = lines.sumOf { OrderTotals.lineValue(it.quantityOrdered, it.unitPrice) }

        val outstanding: Double
            get() = lines.sumOf {
                OrderTotals.outstandingValue(it.quantityOrdered, it.quantityReceived, it.unitPrice)
            }
    }

    val state: StateFlow<State> = combine(
        container.purchasing.observeOrder(orderId),
        container.purchasing.observeLines(orderId),
        container.projects.observeProjects(),
    ) { order, lines, projects ->
        State(order, lines, projects.firstOrNull { it.id == order?.projectId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    private suspend fun actor(): String = container.settings.settings.first().actorName

    fun addLine(label: String, unit: String, quantity: Double, unitPrice: Double) =
        viewModelScope.launch {
            container.purchasing.addLine(
                orderId = orderId,
                label = label,
                unit = unit,
                quantity = quantity,
                unitPrice = unitPrice,
                catalogItemId = null,
                inventoryItemId = null,
                actorName = actor(),
            )
        }

    fun removeLine(line: PurchaseOrderLineEntity) = viewModelScope.launch {
        container.purchasing.removeLine(line, actor())
    }

    /** [expectedOn] is what the supplier said, or null when they did not say. */
    fun place(expectedOn: Long?) = viewModelScope.launch {
        state.value.order?.let { container.purchasing.place(it, actor(), expectedOn) }
    }

    fun setExpected(expectedOn: Long?) = viewModelScope.launch {
        val order = state.value.order ?: return@launch
        if (!container.purchasing.setExpected(order, expectedOn, actor())) _notSaved.value = true
    }

    fun cancel() = viewModelScope.launch {
        state.value.order?.let { container.purchasing.cancel(it, actor()) }
    }

    fun receive(line: PurchaseOrderLineEntity, quantity: Double) = viewModelScope.launch {
        container.purchasing.receive(line, quantity, actor())
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        state.value.order?.let { container.purchasing.delete(it, actor()) }
        onDeleted()
    }
}
