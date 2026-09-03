package il.co.tradesmanager.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InventoryViewModel(private val container: AppContainer) : ViewModel() {

    data class Filters(
        val query: String = "",
        val kind: String? = null,
        val lowStockOnly: Boolean = false,
    )

    private val _filters = MutableStateFlow(Filters())
    val filters: StateFlow<Filters> = _filters.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<InventoryItemEntity>> = _filters
        .flatMapLatest { container.inventory.observe(it.query, it.kind, it.lowStockOnly) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Low-stock count independent of the current filter, for the chip badge. */
    val lowStockCount: StateFlow<Int> = combine(
        container.inventory.observeLowStock(),
        _filters,
    ) { low, _ -> low.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setQuery(value: String) { _filters.value = _filters.value.copy(query = value) }

    fun setKind(kind: String?) { _filters.value = _filters.value.copy(kind = kind) }

    fun toggleLowStockOnly() {
        _filters.value = _filters.value.copy(lowStockOnly = !_filters.value.lowStockOnly)
    }

    /** Looks a scanned label up in this user's own stock. */
    suspend fun findByBarcode(code: String): InventoryItemEntity? =
        container.inventory.findByBarcode(code)

    fun adjustStock(itemId: String, delta: Double, reason: String) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.inventory.adjustStock(itemId, delta, reason, actor)
    }

    fun delete(itemId: String) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.inventory.delete(itemId, actor)
    }
}
