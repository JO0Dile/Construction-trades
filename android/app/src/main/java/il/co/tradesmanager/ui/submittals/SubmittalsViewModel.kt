package il.co.tradesmanager.ui.submittals

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.work.Submittals
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.local.entity.SubmittalEntity
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.data.repository.SubmittalRepository
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

/** One job's material submittals: what is holding an order up, first. */
class SubmittalsViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    /** One revision, how it stands, and the revision that followed it if it was rejected. */
    data class Row(
        val submittal: SubmittalEntity,
        val state: Submittals.State,
        val followedBy: Int?,
    )

    private val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionRepository.State.Loading)

    val mayWrite: StateFlow<Boolean> = session
        .map { (it as? SessionRepository.State.SignedIn)?.role?.let { role -> SubmittalRepository.mayWrite(role) } == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Overdue first -- and nothing for somebody who may not read the plan. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<Row>> = session
        .flatMapLatest { state ->
            val role = (state as? SessionRepository.State.SignedIn)?.role
            if (role == null || !SubmittalRepository.mayRead(role)) flowOf(emptyList()) else container.submittals.observeForProject(projectId)
        }
        .map { all ->
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val next = all.filter { it.resubmissionOf != null }.associateBy { it.resubmissionOf }
            val rows = all.map { submittal ->
                val followed = next[submittal.id]
                Row(
                    submittal = submittal,
                    state = Submittals.state(
                        Submittals.decisionOf(submittal.decision), submittal.neededBy, followed != null, now, zone,
                    ),
                    followedBy = followed?.revision,
                )
            }
            Submittals.order(rows.map { it.state to it.submittal.submittedAt }).map { rows[it] }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _openId = MutableStateFlow<String?>(null)

    val open: StateFlow<Row?> = combine(rows, _openId) { list, id -> list.firstOrNull { it.submittal.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val openPhotos: StateFlow<List<PhotoEntity>> = open
        .map { it?.submittal?.id }
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.photos.observeFor(PhotoRepository.Owner.SUBMITTAL, id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openSubmittal(id: String?) {
        _openId.value = id
    }

    private val _refusal = MutableStateFlow<SubmittalRepository.Refusal?>(null)
    val refusal: StateFlow<SubmittalRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    private fun signedIn(): SessionRepository.State.SignedIn? =
        (session.value as? SessionRepository.State.SignedIn).also {
            if (it == null) _refusal.value = SubmittalRepository.Refusal.NOT_ALLOWED
        }

    private fun report(failure: Throwable) {
        _refusal.value = (failure as? SubmittalRepository.Refused)?.refusal ?: SubmittalRepository.Refusal.UNKNOWN
    }

    fun submit(item: String, supplier: String, location: String, submittedTo: String, neededBy: Long?) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.submittals.submit(
            role = me.role,
            projectId = projectId,
            item = item,
            supplier = supplier,
            location = location,
            submittedTo = submittedTo,
            neededBy = neededBy,
            byName = me.account.displayName,
        ).onFailure { report(it) }
    }

    fun decide(id: String, decision: Submittals.Decision, reviewer: String, notes: String) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.submittals.decide(me.role, id, decision, reviewer, notes, me.account.displayName).onFailure { report(it) }
    }

    /** The next revision opens, since it is the one to follow now. */
    fun resubmit(rejectedId: String, item: String, neededBy: Long?) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.submittals.resubmit(me.role, rejectedId, item, neededBy, me.account.displayName)
            .onSuccess { _openId.value = it.id }
            .onFailure { report(it) }
    }

    private suspend fun actor(): String = container.settings.settings.first().actorName

    fun newSheetTarget(): Pair<String, Uri> = container.photos.newCameraTarget()

    fun sheetCaptured(photoId: String) = viewModelScope.launch {
        val id = _openId.value ?: return@launch
        container.photos.recordCameraPhoto(
            id = photoId,
            ownerType = PhotoRepository.Owner.SUBMITTAL,
            ownerId = id,
            actorName = actor(),
        )
    }

    fun sheetPicked(uri: Uri) = viewModelScope.launch {
        val id = _openId.value ?: return@launch
        container.photos.importPhoto(
            source = uri,
            ownerType = PhotoRepository.Owner.SUBMITTAL,
            ownerId = id,
            actorName = actor(),
        )
    }

    fun deletePhoto(photo: PhotoEntity) = viewModelScope.launch {
        container.photos.delete(photo, actor())
    }
}
