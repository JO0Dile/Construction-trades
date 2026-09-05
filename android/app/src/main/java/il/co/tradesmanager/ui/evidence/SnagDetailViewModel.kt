package il.co.tradesmanager.ui.evidence

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.local.entity.SnagEntity
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SnagDetailViewModel(
    private val container: AppContainer,
    private val snagId: String,
) : ViewModel() {

    val snag: StateFlow<SnagEntity?> = container.evidence.observeSnag(snagId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val raisedPhotos: StateFlow<List<PhotoEntity>> =
        container.photos.observeFor(PhotoRepository.Owner.SNAG_RAISED, snagId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val fixedPhotos: StateFlow<List<PhotoEntity>> =
        container.photos.observeFor(PhotoRepository.Owner.SNAG_FIXED, snagId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    fun newCameraTarget(): Pair<String, Uri> = container.photos.newCameraTarget()

    fun recordCameraPhoto(photoId: String, ownerType: String) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.photos.recordCameraPhoto(photoId, ownerType, snagId, actor)
    }

    fun importPhoto(source: Uri, ownerType: String) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.photos.importPhoto(source, ownerType, snagId, actor)
    }

    fun markFixed() = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.evidence.markSnagFixed(snagId, actor)
    }

    fun verify(accepted: Boolean, notes: String?) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.evidence.verifySnag(snagId, accepted, notes, actor)
    }
}
