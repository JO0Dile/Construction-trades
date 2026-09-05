package il.co.tradesmanager.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.i18n.resolve
import android.net.Uri
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.di.AppContainer
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Editing an item.
 *
 * A user's edit writes into the *active* language only. A catalogue item
 * therefore keeps its Hebrew and Arabic names when someone renames it in
 * English: the other translations are preserved rather than replaced by a
 * single-language string, which is what would otherwise happen the first time
 * anyone corrected a name.
 */
class InventoryEditViewModel(
    private val container: AppContainer,
    private val itemId: String?,
    private val languageTag: String,
) : ViewModel() {

    data class Form(
        val name: String = "",
        val spec: String = "",
        val category: String = "",
        val quantity: String = "0",
        val minStock: String = "0",
        val unit: String = "PCS",
        val barcode: String = "",
        val price: String = "",
        val tags: String = "",
        val nameError: Boolean = false,
    )

    private val _form = MutableStateFlow(Form())
    val form: StateFlow<Form> = _form.asStateFlow()

    private var loaded: InventoryItemEntity? = null

    /**
     * Settled the moment the editor opens, including for a new item, so a
     * photo can be attached before the item has ever been saved. Without this
     * the user would have to save, reopen, and only then add the picture.
     */
    private val editingId: String = itemId ?: UUID.randomUUID().toString()

    val photos: StateFlow<List<PhotoEntity>> =
        container.photos.observeFor(PhotoRepository.Owner.INVENTORY_ITEM, editingId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun newCameraTarget(): Pair<String, Uri> = container.photos.newCameraTarget()

    fun onCaptured(photoId: String) = viewModelScope.launch {
        container.photos.recordCameraPhoto(
            id = photoId,
            ownerType = PhotoRepository.Owner.INVENTORY_ITEM,
            ownerId = editingId,
            actorName = container.settings.settings.first().actorName,
        )
    }

    fun onPicked(uri: Uri) = viewModelScope.launch {
        container.photos.importPhoto(
            source = uri,
            ownerType = PhotoRepository.Owner.INVENTORY_ITEM,
            ownerId = editingId,
            actorName = container.settings.settings.first().actorName,
        )
    }

    fun deletePhoto(photo: PhotoEntity) = viewModelScope.launch {
        container.photos.delete(photo, container.settings.settings.first().actorName)
    }

    init {
        if (itemId != null) {
            viewModelScope.launch {
                container.inventory.observeItem(itemId).first()?.let { item ->
                    loaded = item
                    _form.value = Form(
                        name = item.names.resolve(languageTag),
                        spec = item.spec.resolve(languageTag),
                        category = item.category,
                        quantity = trimNumber(item.quantity),
                        minStock = trimNumber(item.minStock),
                        unit = item.unit,
                        barcode = item.barcode.orEmpty(),
                        price = item.purchasePrice?.let(::trimNumber).orEmpty(),
                        tags = item.tags.joinToString(", "),
                    )
                }
            }
        }
    }

    fun setName(value: String) { _form.value = _form.value.copy(name = value, nameError = value.isBlank()) }
    fun setSpec(value: String) { _form.value = _form.value.copy(spec = value) }
    fun setCategory(value: String) { _form.value = _form.value.copy(category = value) }
    fun setQuantity(value: String) { _form.value = _form.value.copy(quantity = value) }
    fun setMinStock(value: String) { _form.value = _form.value.copy(minStock = value) }
    fun setUnit(value: String) { _form.value = _form.value.copy(unit = value) }
    fun setBarcode(value: String) { _form.value = _form.value.copy(barcode = value) }
    fun setPrice(value: String) { _form.value = _form.value.copy(price = value) }
    fun setTags(value: String) { _form.value = _form.value.copy(tags = value) }

    /**
     * Saves, then hands back which item it was.
     *
     * The list needs the id to find the thing you just typed: with a
     * search or a filter still on, or with low-stock rows above it, a new
     * item lands somewhere off screen and the screen looks like it did
     * nothing.
     */
    fun save(onDone: (String) -> Unit) {
        val form = _form.value
        if (form.name.isBlank()) {
            _form.value = form.copy(nameError = true)
            return
        }
        viewModelScope.launch {
            val existing = loaded
            val now = System.currentTimeMillis()
            val item = InventoryItemEntity(
                id = existing?.id ?: editingId,
                catalogItemId = existing?.catalogItemId,
                tradeId = existing?.tradeId,
                kind = existing?.kind ?: "MATERIAL",
                category = form.category.ifBlank { existing?.category.orEmpty() },
                unit = form.unit.ifBlank { "PCS" },
                names = existing?.names.orEmpty() + (languageTag.substringBefore('-') to form.name),
                spec = existing?.spec.orEmpty() + (languageTag.substringBefore('-') to form.spec),
                attributes = existing?.attributes.orEmpty(),
                tags = form.tags.split(',').map { it.trim() }.filter { it.isNotBlank() },
                quantity = form.quantity.toDoubleOrNull() ?: 0.0,
                minStock = form.minStock.toDoubleOrNull() ?: 0.0,
                supplierId = existing?.supplierId,
                purchasePrice = form.price.toDoubleOrNull(),
                barcode = form.barcode.takeIf { it.isNotBlank() },
                searchIndex = "",
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
            val actor = container.settings.settings.first().actorName
            container.inventory.save(item, actor)
            onDone(item.id)
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = itemId ?: return onDone()
        viewModelScope.launch {
            container.inventory.delete(id, container.settings.settings.first().actorName)
            onDone()
        }
    }

    private fun trimNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
