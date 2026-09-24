package il.co.tradesmanager.ui.inspections

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.evidence.Inspections
import il.co.tradesmanager.data.local.entity.InspectionEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.repository.InspectionRepository
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import java.time.ZoneId
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

/** One job's inspection requests: what is holding the work up, first. */
class InspectionsViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    /**
     * One request, how it stands, and the references around it: the later
     * request that re-inspects it, the earlier one it re-inspects, and the
     * pour it cleared.
     */
    data class Row(
        val inspection: InspectionEntity,
        val state: Inspections.State,
        val reinspectedAs: String?,
        val reinspectionOf: String?,
        val clearedPour: String?,
    )

    private val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionRepository.State.Loading)

    val mayWrite: StateFlow<Boolean> = session
        .map { (it as? SessionRepository.State.SignedIn)?.role?.let { role -> InspectionRepository.mayWrite(role) } == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Nothing for somebody who may not read the site's record. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val all = session.flatMapLatest { state ->
        val role = (state as? SessionRepository.State.SignedIn)?.role
        if (role == null || !InspectionRepository.mayRead(role)) flowOf(emptyList()) else container.inspections.observeForProject(projectId)
    }

    val rows: StateFlow<List<Row>> = combine(all, container.concrete.observePours(projectId)) { inspections, pours ->
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val byId = inspections.associateBy { it.id }
        val againOf = inspections.filter { it.reinspectionOf != null }.associateBy { it.reinspectionOf }
        val pourReference = pours.associate { it.id to it.reference }
        val rows = inspections.map { inspection ->
            val again = againOf[inspection.id]
            Row(
                inspection = inspection,
                state = Inspections.state(
                    Inspections.resultOf(inspection.result), inspection.wantedOn, again != null, now, zone,
                ),
                reinspectedAs = again?.reference,
                reinspectionOf = inspection.reinspectionOf?.let { byId[it]?.reference },
                clearedPour = inspection.clearedPourId?.let { pourReference[it] },
            )
        }
        Inspections.order(rows.map { it.state to it.inspection.requestedAt }).map { rows[it] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _openId = MutableStateFlow<String?>(null)

    val open: StateFlow<Row?> = combine(rows, _openId) { list, id -> list.firstOrNull { it.inspection.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val openPhotos: StateFlow<List<PhotoEntity>> = open
        .map { it?.inspection?.id }
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.photos.observeFor(PhotoRepository.Owner.INSPECTION, id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openInspection(id: String?) {
        _openId.value = id
    }

    private val _refusal = MutableStateFlow<InspectionRepository.Refusal?>(null)
    val refusal: StateFlow<InspectionRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    private fun signedIn(): SessionRepository.State.SignedIn? =
        (session.value as? SessionRepository.State.SignedIn).also {
            if (it == null) _refusal.value = InspectionRepository.Refusal.NOT_ALLOWED
        }

    private fun report(failure: Throwable) {
        _refusal.value = (failure as? InspectionRepository.Refused)?.refusal ?: InspectionRepository.Refusal.UNKNOWN
    }

    fun request(kind: Inspections.Kind, element: String, requestedOf: String, wantedOn: Long?) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.inspections.request(
            role = me.role,
            projectId = projectId,
            kind = kind,
            element = element,
            requestedOf = requestedOf,
            wantedOn = wantedOn,
            byName = me.account.displayName,
        ).onFailure { report(it) }
    }

    fun decide(id: String, result: Inspections.Result, inspectorName: String, comments: String) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.inspections.decide(me.role, id, result, inspectorName, comments, me.account.displayName).onFailure { report(it) }
    }

    /** Asking again after a failure; the new request opens, since it is the one to follow now. */
    fun reinspect(failedId: String, wantedOn: Long?) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.inspections.reinspect(me.role, failedId, wantedOn, me.account.displayName)
            .onSuccess { _openId.value = it.id }
            .onFailure { report(it) }
    }

    private suspend fun actor(): String = container.settings.settings.first().actorName

    fun newFormTarget(): Pair<String, Uri> = container.photos.newCameraTarget()

    fun formCaptured(photoId: String) = viewModelScope.launch {
        val id = _openId.value ?: return@launch
        container.photos.recordCameraPhoto(
            id = photoId,
            ownerType = PhotoRepository.Owner.INSPECTION,
            ownerId = id,
            actorName = actor(),
        )
    }

    fun formPicked(uri: Uri) = viewModelScope.launch {
        val id = _openId.value ?: return@launch
        container.photos.importPhoto(
            source = uri,
            ownerType = PhotoRepository.Owner.INSPECTION,
            ownerId = id,
            actorName = actor(),
        )
    }

    fun deletePhoto(photo: PhotoEntity) = viewModelScope.launch {
        container.photos.delete(photo, actor())
    }
}
