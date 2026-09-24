package il.co.tradesmanager.ui.drawings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.evidence.Drawings
import il.co.tradesmanager.data.local.entity.DrawingEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.repository.DrawingRepository
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.SessionRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One job's drawing register: what to build from, and what it replaced. */
class DrawingsViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    private val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionRepository.State.Loading)

    val mayRecord: StateFlow<Boolean> = session
        .map { (it as? SessionRepository.State.SignedIn)?.role?.let { role -> DrawingRepository.mayRecord(role) } == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The register, for somebody who may read the plan, and nothing for anybody else. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val rows: StateFlow<List<DrawingEntity>> = session
        .flatMapLatest { state ->
            val role = (state as? SessionRepository.State.SignedIn)?.role
            if (role == null || !role.canRead(Lens.PLAN)) flowOf(emptyList()) else container.drawings.observeForProject(projectId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The revision of each drawing to build from, in drawing-number order. */
    val current: StateFlow<List<DrawingEntity>> = rows
        .map { all ->
            val byId = all.associateBy { it.id }
            Drawings.current(all.map { DrawingRepository.asSheet(it) }).mapNotNull { byId[it.id] }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** How many pictures each revision has, for the row. */
    val photoCounts: StateFlow<Map<String, Int>> = container.photos.observeCountsFor(PhotoRepository.Owner.DRAWING)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _openId = MutableStateFlow<String?>(null)

    /** The drawing open in the sheet, if any. */
    val open: StateFlow<DrawingEntity?> = combine(rows, _openId) { all, id -> all.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The open drawing's earlier revisions, newest first. */
    val openHistory: StateFlow<List<DrawingEntity>> = combine(rows, open) { all, drawing ->
        if (drawing == null) {
            emptyList()
        } else {
            val byId = all.associateBy { it.id }
            Drawings.history(all.map { DrawingRepository.asSheet(it) }, drawing.number).mapNotNull { byId[it.id] }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val openPhotos: StateFlow<List<PhotoEntity>> = _openId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.photos.observeFor(PhotoRepository.Owner.DRAWING, id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openDrawing(id: String?) {
        _openId.value = id
    }

    /** The current revision of [number], for the form to say what a new one replaces. */
    fun currentOf(number: String): DrawingEntity? {
        val sheet = Drawings.currentOf(rows.value.map { DrawingRepository.asSheet(it) }, number) ?: return null
        return rows.value.firstOrNull { it.id == sheet.id }
    }

    private val _refusal = MutableStateFlow<DrawingRepository.Refusal?>(null)
    val refusal: StateFlow<DrawingRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    fun record(number: String, title: String, revision: String, notes: String) = viewModelScope.launch {
        val me = session.value as? SessionRepository.State.SignedIn
        if (me == null) {
            _refusal.value = DrawingRepository.Refusal.NOT_ALLOWED
            return@launch
        }
        container.drawings.record(
            role = me.role,
            projectId = projectId,
            number = number,
            title = title,
            revision = revision,
            notes = notes,
            byName = me.account.displayName,
        ).onSuccess { drawing ->
            _openId.value = drawing.id
        }.onFailure { failure ->
            _refusal.value = (failure as? DrawingRepository.Refused)?.refusal ?: DrawingRepository.Refusal.UNKNOWN
        }
    }

    private suspend fun actor(): String = container.settings.settings.first().actorName

    fun newSheetTarget(): Pair<String, Uri> = container.photos.newCameraTarget()

    fun sheetCaptured(photoId: String) = viewModelScope.launch {
        val drawingId = _openId.value ?: return@launch
        container.photos.recordCameraPhoto(
            id = photoId,
            ownerType = PhotoRepository.Owner.DRAWING,
            ownerId = drawingId,
            actorName = actor(),
        )
    }

    fun sheetPicked(uri: Uri) = viewModelScope.launch {
        val drawingId = _openId.value ?: return@launch
        container.photos.importPhoto(
            source = uri,
            ownerType = PhotoRepository.Owner.DRAWING,
            ownerId = drawingId,
            actorName = actor(),
        )
    }

    fun deletePhoto(photo: PhotoEntity) = viewModelScope.launch {
        container.photos.delete(photo, actor())
    }
}
