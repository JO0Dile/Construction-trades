package il.co.tradesmanager.ui.account

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class InductionViewModel(
    private val container: AppContainer,
    private val accountId: String,
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

    val photo: StateFlow<List<PhotoEntity>> =
        container.photos.observeFor(PhotoRepository.Owner.ACCOUNT_PHOTO, accountId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val idDocument: StateFlow<List<PhotoEntity>> =
        container.photos.observeFor(PhotoRepository.Owner.ACCOUNT_ID_DOCUMENT, accountId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun newCameraTarget(): Pair<String, Uri> = container.photos.newCameraTarget()

    fun recordCameraPhoto(photoId: String, ownerType: String) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.photos.recordCameraPhoto(photoId, ownerType, accountId, actor)
    }

    fun importPhoto(source: Uri, ownerType: String) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.photos.importPhoto(source, ownerType, accountId, actor)
    }

    /**
     * Records the induction. The repository refuses a signature that is only a
     * tap, so this cannot let somebody through by accident — see
     * [il.co.tradesmanager.data.repository.AccountRepository.recordInduction].
     */
    fun sign(signature: String) = viewModelScope.launch {
        if (!container.accounts.recordInduction(accountId, signature)) _notSaved.value = true
    }
}
