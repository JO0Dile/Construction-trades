package il.co.tradesmanager.ui.visitors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.data.local.entity.SiteVisitEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.data.repository.VisitRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One job's visitor log: who is here now, signing more in, and who has been. */
class VisitorsViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    private val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    private val signedIn: SessionRepository.State.SignedIn?
        get() = session.value as? SessionRepository.State.SignedIn

    val mayWrite: StateFlow<Boolean> = session
        .map { state ->
            (state as? SessionRepository.State.SignedIn)?.role?.let { VisitRepository.mayWrite(it) } == true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * The job's visits, and nothing at all for somebody who may not read the
     * site's records -- not a list the screen then hides. The job page only
     * links here for those who may, but the rule belongs with the data.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val visits: StateFlow<List<SiteVisitEntity>> = session
        .flatMapLatest { state ->
            val role = (state as? SessionRepository.State.SignedIn)?.role
            if (role == null || !role.canRead(Lens.EVIDENCE)) {
                flowOf(emptyList())
            } else {
                container.visits.observeForProject(projectId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Signed in and not out, longest here first: the one most likely to have been forgotten. */
    val here: StateFlow<List<SiteVisitEntity>> = visits
        .map { all -> all.filter { it.leftAt == null }.sortedBy { it.arrivedAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val gone: StateFlow<List<SiteVisitEntity>> = visits
        .map { all -> all.filter { it.leftAt != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The job's name, for the title of an export. */
    val jobName: StateFlow<String> = container.projects.observeProjects()
        .map { jobs -> jobs.firstOrNull { it.id == projectId }?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _refusal = MutableStateFlow<VisitRepository.Refusal?>(null)
    val refusal: StateFlow<VisitRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    /** One event per visitor signed in, so the screen can clear its form and say so. */
    private val _signedInEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signedInEvents: SharedFlow<Unit> = _signedInEvents.asSharedFlow()

    fun signIn(
        name: String,
        organisation: String,
        phone: String,
        hostName: String,
        briefed: Boolean,
        signature: String,
    ) = viewModelScope.launch {
        val me = signedIn
        if (me == null) {
            _refusal.value = VisitRepository.Refusal.NOT_ALLOWED
            return@launch
        }
        container.visits.signIn(
            role = me.role,
            projectId = projectId,
            companyId = me.active?.companyId,
            name = name,
            organisation = organisation,
            phone = phone,
            hostName = hostName,
            briefed = briefed,
            signature = signature,
            byAccountId = me.account.id,
            byName = me.account.displayName,
        ).onSuccess {
            _signedInEvents.tryEmit(Unit)
        }.onFailure { failure ->
            _refusal.value = (failure as? VisitRepository.Refused)?.refusal
                ?: VisitRepository.Refusal.UNKNOWN
        }
    }

    fun signOut(visitId: String) = viewModelScope.launch {
        val me = signedIn
        if (me == null) {
            _refusal.value = VisitRepository.Refusal.NOT_ALLOWED
            return@launch
        }
        container.visits.signOut(me.role, visitId, me.account.displayName)
            .onFailure { failure ->
                _refusal.value = (failure as? VisitRepository.Refused)?.refusal
                    ?: VisitRepository.Refusal.UNKNOWN
            }
    }
}
