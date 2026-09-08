package il.co.tradesmanager.ui.safety

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.safety.Violations
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
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
import kotlinx.coroutines.flow.mapLatest
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

    /**
     * The register, scoped to whoever is looking at it.
     *
     * Keyed on the membership, not on its company. Those are two different
     * questions and this asked the wrong one: no current membership means
     * nobody's books and nothing to show, while a membership with no company
     * is a person working alone, whose own register is theirs to read. Asking
     * only about the company answered "nothing" to both.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val violations: StateFlow<List<ViolationEntity>> = session
        .flatMapLatest { state ->
            val active = (state as? SessionRepository.State.SignedIn)?.active
            if (active == null) {
                flowOf(emptyList())
            } else {
                container.violations.observeForCompany(active.companyId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /* --------------------------------------------------------- finding them */

    private val _found = MutableStateFlow<AccountEntity?>(null)
    val found: StateFlow<AccountEntity?> = _found.asStateFlow()

    /**
     * The found person's photograph.
     *
     * The reason the officer types an ID at all. Five men on a site in the
     * same hi-vis are not told apart by a name, and this card showed no face
     * at all until it was wired to one — a person card that never loads a
     * person is the failure this app keeps having.
     */
    private val _foundFace = MutableStateFlow<String?>(null)
    val foundFace: StateFlow<String?> = _foundFace.asStateFlow()

    /** True once a search has run and come back with nobody. */
    private val _searchedInVain = MutableStateFlow(false)
    val searchedInVain: StateFlow<Boolean> = _searchedInVain.asStateFlow()

    fun search(idNumber: String) = viewModelScope.launch {
        val match = container.accounts.findByIdNumber(idNumber)
        _found.value = match
        _foundFace.value = match?.let { container.photos.faceOf(it.id) }
        _searchedInVain.value = match == null && idNumber.isNotBlank()
    }

    fun clearSearch() {
        _found.value = null
        _foundFace.value = null
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

    /** The face of whoever the open draft is written against. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val openFace: StateFlow<String?> = open
        .mapLatest { draft -> draft?.let { container.photos.faceOf(it.againstAccountId) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _refusal = MutableStateFlow<ViolationRepository.Refusal?>(null)
    val refusal: StateFlow<ViolationRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    /**
     * Puts a sentence on a failure, whatever it turned out to be.
     *
     * The three callers each used to write `(failure as? Refused)?.refusal`,
     * which reads as careful and is the bug: anything that is not a Refused —
     * a disk that is full, a row that vanished — cast to null, cleared the
     * refusal, and left the screen looking like nothing had happened.
     */
    private fun refuse(failure: Throwable) {
        _refusal.value = (failure as? ViolationRepository.Refused)?.refusal
            ?: ViolationRepository.Refusal.UNKNOWN
    }

    /**
     * Opens a draft against whoever the search found.
     *
     * Every way this can fail now says so. It used to have four silent exits
     * — three `?: return` guards and a swallowed failure — so pressing the
     * button on an account that could not write one did exactly nothing, and
     * nothing is indistinguishable from a broken app.
     */
    fun startAgainstFound() = viewModelScope.launch {
        // The only guard left without a sentence: the button lives inside the
        // card that the found person draws, so there is no screen on which
        // this can be null and no reader to explain it to.
        val person = _found.value ?: return@launch
        val me = signedIn
        val active = me?.active
        if (me == null || active == null) {
            _refusal.value = ViolationRepository.Refusal.NOT_ON_ANY_BOOKS
            return@launch
        }
        container.violations.startDraft(
            // Read off the session already in hand, not fetched again. Two
            // reads are two chances to disagree with each other.
            role = me.role,
            companyId = active.companyId,
            projectId = null,
            againstAccountId = person.id,
            againstName = person.displayName,
            againstIdNumber = person.idNumber.orEmpty(),
            recordedByAccountId = me.account.id,
            recordedByName = me.account.displayName,
        ).onSuccess {
            _openId.value = it.id
            clearSearch()
        }.onFailure(::refuse)
    }

    /**
     * The jobs a violation can be put against.
     *
     * Every job, not only the top-level ones. A violation happens on the
     * twelfth floor, and the twelfth floor is a part of a tower — offering
     * only the tower would be offering the wrong answer politely.
     */
    val jobs: StateFlow<List<ProjectEntity>> = container.projects.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Puts the open draft against a job, or takes it off one. */
    fun setProject(projectId: String?) = viewModelScope.launch {
        val draft = open.value ?: return@launch
        container.violations.setProject(draft, projectId).onFailure(::refuse)
    }

    fun edit(description: String, cost: Double?) = viewModelScope.launch {
        val draft = open.value ?: return@launch
        container.violations.updateDraft(draft, description, cost).onFailure(::refuse)
    }

    /** Where the camera should write, when the officer photographs it there. */
    fun newEvidenceTarget(): Pair<String, Uri> = container.photos.newCameraTarget()

    /** The camera came back with a picture. */
    fun evidenceCaptured(photoId: String) = viewModelScope.launch {
        val draft = open.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.photos.recordCameraPhoto(
            id = photoId,
            ownerType = PhotoRepository.Owner.VIOLATION,
            ownerId = draft.id,
            actorName = actor,
        )
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
            .onFailure(::refuse)
    }

    fun cancel() = viewModelScope.launch {
        val draft = open.value ?: return@launch
        container.violations.cancel(draft)
            .onSuccess { _openId.value = null }
            .onFailure(::refuse)
    }

    fun openDraft(id: String?) {
        _openId.value = id
    }
}
