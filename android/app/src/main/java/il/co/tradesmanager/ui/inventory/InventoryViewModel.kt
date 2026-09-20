package il.co.tradesmanager.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.catalog.WorkStage
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import il.co.tradesmanager.data.local.entity.StockMovementEntity
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
     * Which item's details are open, or null when the sheet is closed.
     *
     * What is held is the id, and the row itself is observed from the
     * database rather than picked out of [items]. Pressing + in the sheet can
     * carry an item back above its low-stock threshold, and a sheet reading
     * from the filtered list would then empty itself under the finger that
     * was still using it.
     */
    private val _detailsFor = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val details: StateFlow<InventoryItemEntity?> = _detailsFor
        .flatMapLatest { id ->
            if (id == null) flowOf<InventoryItemEntity?>(null) else container.inventory.observeItem(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * What has happened to that item's stock, newest first.
     *
     * Every adjustment has written one of these since the register was built,
     * naming who moved it and why, and nothing has ever read them back. A
     * count that cannot be questioned is a count nobody believes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val detailsMovements: StateFlow<List<StockMovementEntity>> = _detailsFor
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.inventory.observeMovements(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openDetails(itemId: String) { _detailsFor.value = itemId }

    fun closeDetails() { _detailsFor.value = null }

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

    companion object {
        /**
         * Why the plus and the minus say stock moved.
         *
         * Stored on the movement and read back by whatever displays it, so
         * they are keys rather than prose: the person who moved the stock and
         * the person reading the register later may not share a language.
         */
        const val USED_ON_SITE = "used_on_site"
        const val RESTOCKED = "restocked"
    }
}
