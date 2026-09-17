package il.co.tradesmanager.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.catalog.WorkStage
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
        /**
         * The stage of the job being worked, or null for all of them.
         *
         * Null by default: somebody who has not said which stage they are on
         * should see their whole van, not a guess about it.
         */
        val stageId: String? = null,
    )

    private val _filters = MutableStateFlow(Filters())
    val filters: StateFlow<Filters> = _filters.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<InventoryItemEntity>> = _filters
        .flatMapLatest {
            container.inventory.observe(it.query, it.kind, it.lowStockOnly, it.stageId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Low-stock count independent of the current filter, for the chip badge. */
    val lowStockCount: StateFlow<Int> = combine(
        container.inventory.observeLowStock(),
        _filters,
    ) { low, _ -> low.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Item id -> photo uri, so a row can show the user's own picture. */
    val photoByItem: StateFlow<Map<String, String>> = container.photos.observeItemThumbnails()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Drops every filter.
     *
     * Used when somebody has just added an item the current view would
     * hide — searching for "cable", finding none, adding one called
     * something else, and being shown the same empty search. Showing them
     * the thing they just made matters more than keeping a search they
     * have already acted on.
     */
    fun clearFilters() { _filters.value = Filters() }

    fun setQuery(value: String) { _filters.value = _filters.value.copy(query = value) }

    fun setKind(kind: String?) { _filters.value = _filters.value.copy(kind = kind) }

    /**
     * Tapping the stage you are already on clears it.
     *
     * The kind chips above carry an explicit "All" instead. Six stages plus a
     * seventh chip to undo them is a scrolling row nobody reaches the end of,
     * and tapping the selected one back off is what a phone user tries first.
     */
    fun setStage(stageId: String?) {
        val current = _filters.value.stageId
        _filters.value = _filters.value.copy(stageId = if (current == stageId) null else stageId)
    }

    /** The stages of a job, in order, for the chip row. Content, not code. */
    val stages: List<WorkStage> get() = container.scopes.stages

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
