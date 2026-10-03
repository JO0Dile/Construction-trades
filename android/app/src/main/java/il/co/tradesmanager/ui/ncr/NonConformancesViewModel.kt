package il.co.tradesmanager.ui.ncr

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.evidence.NonConformances
import il.co.tradesmanager.data.local.entity.NonConformanceEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.repository.NonConformanceRepository
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import java.time.LocalDate
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

/** One job's non-conformance register: overdue first, then those waiting for a decision. */
class NonConformancesViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    data class Row(val report: NonConformanceEntity, val state: NonConformances.State)

    private val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionRepository.State.Loading)

    val mayWrite: StateFlow<Boolean> = session
        .map { (it as? SessionRepository.State.SignedIn)?.role?.let { role -> NonConformanceRepository.mayWrite(role) } == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Nothing for somebody who may not read the site's record. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<Row>> = session
        .flatMapLatest { state ->
            val role = (state as? SessionRepository.State.SignedIn)?.role
            if (role == null || !NonConformanceRepository.mayRead(role)) {
                flowOf(emptyList<NonConformanceEntity>())
            } else {
                container.nonConformances.observeForProject(projectId)
            }
        }
        .map { all -> rowsOf(all, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The job's name, for the title of an export. */
    val jobName: StateFlow<String> = container.projects.observeProjects()
        .map { jobs -> jobs.firstOrNull { it.id == projectId }?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _openId = MutableStateFlow<String?>(null)

    val open: StateFlow<Row?> = combine(rows, _openId) { list, id -> list.firstOrNull { it.report.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** What the open report found, as photographed. Only ever for a row [rows] already let through. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val openPhotos: StateFlow<List<PhotoEntity>> = open
        .map { it?.report?.id }
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.photos.observeFor(PhotoRepository.Owner.NON_CONFORMANCE, id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openReport(id: String?) {
        _openId.value = id
    }

    private val _refusal = MutableStateFlow<NonConformanceRepository.Refusal?>(null)
    val refusal: StateFlow<NonConformanceRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    private fun signedIn(): SessionRepository.State.SignedIn? =
        (session.value as? SessionRepository.State.SignedIn).also {
            if (it == null) _refusal.value = NonConformanceRepository.Refusal.NOT_ALLOWED
        }

    private fun report(failure: Throwable) {
        _refusal.value = (failure as? NonConformanceRepository.Refused)?.refusal ?: NonConformanceRepository.Refusal.UNKNOWN
    }

    /** Raised, then opened, so the photographs of it can be taken straight away. */
    fun raise(element: String, requirement: String, finding: String, foundBy: NonConformances.FoundBy) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.nonConformances.raise(me.role, projectId, element, requirement, finding, foundBy, me.account.displayName)
            .onSuccess { _openId.value = it.id }
            .onFailure { report(it) }
    }

    fun decide(reportId: String, disposition: NonConformances.Disposition, acceptedBy: String, correction: String, dueInDays: Long?) =
        viewModelScope.launch {
            val me = signedIn() ?: return@launch
            container.nonConformances.decide(
                role = me.role,
                reportId = reportId,
                disposition = disposition,
                acceptedBy = acceptedBy,
                correction = correction,
                dueOn = dueInDays?.let { LocalDate.now().plusDays(it) },
                byName = me.account.displayName,
            ).onFailure { report(it) }
        }

    fun close(reportId: String, verification: String) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.nonConformances.close(me.role, reportId, verification, me.account.displayName).onFailure { report(it) }
    }

    private suspend fun actor(): String = container.settings.settings.first().actorName

    fun newPhotoTarget(): Pair<String, Uri> = container.photos.newCameraTarget()

    /** Photographs are the record: added by whoever may write it, to a report already open. */
    fun photoCaptured(photoId: String) = viewModelScope.launch {
        val id = _openId.value ?: return@launch
        if (!mayWrite.value) return@launch
        container.photos.recordCameraPhoto(id = photoId, ownerType = PhotoRepository.Owner.NON_CONFORMANCE, ownerId = id, actorName = actor())
    }

    fun photoPicked(uri: Uri) = viewModelScope.launch {
        val id = _openId.value ?: return@launch
        if (!mayWrite.value) return@launch
        container.photos.importPhoto(source = uri, ownerType = PhotoRepository.Owner.NON_CONFORMANCE, ownerId = id, actorName = actor())
    }

    /** Only before it is closed, and only for somebody who may write the register. */
    fun deletePhoto(photo: PhotoEntity) = viewModelScope.launch {
        if (!mayWrite.value || open.value?.report?.closedAt != null) return@launch
        container.photos.delete(photo, actor())
    }

    companion object {
        fun rowsOf(all: List<NonConformanceEntity>, today: LocalDate): List<Row> {
            val rows = all.map {
                Row(it, NonConformances.state(it.decidedAt != null, it.dueOnDay?.let(LocalDate::ofEpochDay), it.closedAt != null, today))
            }
            return NonConformances.order(rows.map { it.state to (it.report.closedAt ?: it.report.raisedAt) }).map { rows[it] }
        }
    }
}
