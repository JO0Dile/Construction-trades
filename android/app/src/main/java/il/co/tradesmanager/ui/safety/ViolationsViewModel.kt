package il.co.tradesmanager.ui.safety

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.safety.Violations
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.local.entity.ViolationEntity
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.data.repository.ViolationRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The violation register for one firm, as its safety officer sees it. */
class ViolationsViewModel(
    private val container: AppContainer,
) : ViewModel() {

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    private val signedIn: SessionRepository.State.SignedIn?
        get() = session.value as? SessionRepository.State.SignedIn

    private val companyId: String? get() = signedIn?.active?.companyId

    private val role: Role get() = signedIn?.role ?: Role.WORKER

    @OptIn(ExperimentalCoroutinesApi::class)
    val violations: StateFlow<List<ViolationEntity>> = session
        .flatMapLatest { state ->
            val id = (state as? SessionRepository.State.SignedIn)?.active?.companyId
            if (id == null) flowOf(emptyList()) else container.violations.observeForCompany(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /* --------------------------------------------------------- finding them */

    private val _found = MutableStateFlow<AccountEntity?>(null)
    val found: StateFlow<AccountEntity?> = _found.asStateFlow()

    /** True once a search has run and come back with nobody. */
    private val _searchedInVain = MutableStateFlow(false)
    val searchedInVain: StateFlow<Boolean> = _searchedInVain.asStateFlow()

    fun search(idNumber: String) = viewModelScope.launch {
        val match = container.accounts.findByIdNumber(idNumber)
        _found.value = match
        _searchedInVain.value = match == null && idNumber.isNotBlank()
    }

    fun clearSearch() {
        _found.value = null
        _searchedInVain.value = false
    }

    /* ------------------------------------------------------------ the draft */

    private val _openId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val open: StateFlow<ViolationEntity?> = _openId
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.violations.observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val evidence: StateFlow<List<PhotoEntity>> = _openId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList())
            } else {
                container.photos.observeFor(PhotoRepository.Owner.VIOLATION, id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refusal = MutableStateFlow<ViolationRepository.Refusal?>(null)
    val refusal: StateFlow<ViolationRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    /** Opens a draft against whoever the search found. */
    fun startAgainstFound() = viewModelScope.launch {
        val person = _found.value ?: return@launch
        val company = companyId ?: return@launch
        val me = signedIn?.account ?: return@launch
        container.violations.startDraft(
            role = role,
            companyId = company,
            projectId = null,
            againstAccountId = person.id,
            againstName = person.displayName,
            againstIdNumber = person.idNumber.orEmpty(),
            recordedByAccountId = me.id,
            recordedByName = me.displayName,
        ).onSuccess {
            _openId.value = it.id
            clearSearch()
        }.onFailure { failure ->
            _refusal.value = (failure as? ViolationRepository.Refused)?.refusal
        }
    }

    fun edit(description: String, cost: Double?) = viewModelScope.launch {
        val draft = open.value ?: return@launch
        container.violations.updateDraft(draft, description, cost).onFailure { failure ->
            _refusal.value = (failure as? ViolationRepository.Refused)?.refusal
        }
    }

    /**
     * Attaches a still or a video.
     *
     * Which it is comes from the content resolver inside the repository, not
     * from here. A screen guessing would be a screen that can guess wrong,
     * and a video written to a .jpg and put through the watermarker is
     * destroyed evidence.
     */
    fun addEvidence(uri: Uri) = viewModelScope.launch {
        val draft = open.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.photos.importPhoto(
            source = uri,
            ownerType = PhotoRepository.Owner.VIOLATION,
            ownerId = draft.id,
            actorName = actor,
        )
    }

    /**
     * What is stopping this being confirmed, or null.
     *
     * The same rule the repository will apply, asked here so the screen can
     * say which thing is missing. The repository checks again and is the one
     * that decides — this is for the message, not for the decision.
     */
    fun blocker(): Violations.Blocker? {
        val draft = open.value ?: return null
        return Violations.blocksConfirming(
            Violations.Draft(
                status = Violations.Status.valueOf(draft.status),
                againstAccountId = draft.againstAccountId,
                evidenceCount = evidence.value.size,
                description = draft.description,
            ),
        )
    }

    fun confirm() = viewModelScope.launch {
        val draft = open.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.violations.confirm(draft, actor)
            .onSuccess { _openId.value = null }
            .onFailure { failure ->
                _refusal.value = (failure as? ViolationRepository.Refused)?.refusal
            }
    }

    fun cancel() = viewModelScope.launch {
        val draft = open.value ?: return@launch
        container.violations.cancel(draft).onSuccess { _openId.value = null }
    }

    fun openDraft(id: String?) {
        _openId.value = id
    }
}
