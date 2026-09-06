package il.co.tradesmanager.ui.work

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.work.Assignment
import il.co.tradesmanager.data.local.entity.AssignmentEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.repository.EngagementRepository
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The work packages on one job, from where the signed-in firm is standing.
 *
 * "From where the firm is standing" is the whole screen. The same package is
 * something a contractor gave out and something a crew took on, and the two
 * see different buttons on it — so the identity of the viewing organisation
 * is not a detail passed to one function, it is the thing every row is read
 * through.
 */
class WorkPackagesViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    /**
     * Which organisation is looking.
     *
     * A personal account has no company, and its own account id stands in as
     * its organisation. That is not a placeholder: a sole trader taking work
     * from a contractor is a real party to a real agreement, and giving them
     * a blank id would silently drop them out of every confidentiality check
     * — which fails open, because a blank id matches a blank column.
     */
    val orgId: StateFlow<String> = session
        .map { state ->
            val signedIn = state as? SessionRepository.State.SignedIn ?: return@map ""
            signedIn.active?.companyId ?: signedIn.account.id
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _openId = MutableStateFlow<String?>(null)
    val openId: StateFlow<String?> = _openId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val packages: StateFlow<List<AssignmentEntity>> = orgId
        .flatMapLatest { org ->
            if (org.isBlank()) flowOf(emptyList())
            else container.engagements.observeAssignments(projectId, org)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val open: StateFlow<AssignmentEntity?> = _openId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else container.engagements.observeAssignment(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The proof photographs on the open package.
     *
     * Counted from the photo table rather than passed in by the screen. The
     * submit rule is "there is a photograph", and a screen that told the
     * repository how many there were could tell it one when there were none —
     * which is exactly the check being made.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val proofPhotos: StateFlow<List<PhotoEntity>> = _openId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else container.photos.observeFor(PhotoRepository.Owner.WORK_PACKAGE, id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The last refusal, for showing why a button did nothing. */
    private val _refusal = MutableStateFlow<EngagementRepository.Refusal?>(null)
    val refusal: StateFlow<EngagementRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() { _refusal.value = null }

    fun openPackage(id: String?) { _openId.value = id }

    // ---- proof photographs ------------------------------------------------

    fun newCameraTarget(): Pair<String, Uri> = container.photos.newCameraTarget()

    fun recordCameraPhoto(photoId: String) = viewModelScope.launch {
        val id = _openId.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.photos.recordCameraPhoto(photoId, PhotoRepository.Owner.WORK_PACKAGE, id, actor)
    }

    fun importPhoto(source: Uri) = viewModelScope.launch {
        val id = _openId.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.photos.importPhoto(source, PhotoRepository.Owner.WORK_PACKAGE, id, actor)
    }

    fun create(
        title: String,
        amount: Double,
        payeeOrgId: String,
        stageId: String?,
        scopeId: String?,
        location: String?,
    ) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        val raised = container.engagements.createAssignment(
            projectId = projectId,
            contractId = null,
            payerOrgId = orgId.value,
            payeeOrgId = payeeOrgId,
            title = title,
            stageId = stageId,
            scopeId = scopeId,
            location = location,
            amount = amount,
            actorName = actor,
        )
        _openId.value = raised.id
    }

    fun move(to: String, reason: String? = null) = viewModelScope.launch {
        val assignment = open.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.engagements
            .move(assignment, to, orgId.value, actor, reason, proofPhotos.value.size)
            .onFailure { failure ->
                _refusal.value = (failure as? EngagementRepository.Refused)?.refusal
            }
    }

    fun markInvoiced() = viewModelScope.launch {
        val assignment = open.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.engagements.markInvoiced(assignment, actor).onFailure { failure ->
            _refusal.value = (failure as? EngagementRepository.Refused)?.refusal
        }
    }

    companion object {

        /** Which side of the agreement this organisation is on, or null. */
        fun sideOf(assignment: AssignmentEntity, orgId: String): Assignment.Side? = when (orgId) {
            assignment.payerOrgId -> Assignment.Side.PAYER
            assignment.payeeOrgId -> Assignment.Side.PAYEE
            else -> null
        }

        /**
         * The moves this viewer may make right now.
         *
         * Derived from the same table the repository checks rather than from
         * a list of statuses written out again here. A second copy would drift
         * from the first, and the version that drifts is always the one that
         * shows a button which then refuses.
         */
        fun moves(assignment: AssignmentEntity, orgId: String): List<String> {
            val side = sideOf(assignment, orgId) ?: return emptyList()
            return Assignment.next(assignment.status)
                .filter { Assignment.actor(it) == side }
                .sorted()
        }
    }
}
