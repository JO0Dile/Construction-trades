package il.co.tradesmanager.ui.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.safety.Muster
import il.co.tradesmanager.data.local.entity.MusterEntity
import il.co.tradesmanager.data.repository.MusterRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The roll call screen's state.
 *
 * Everything the screen shows about a running roll call — the order the names
 * are read in, how many are missing, how long it has been going — comes out of
 * [MusterRepository.rollOf] rather than being worked out here, so the screen
 * and the record cannot disagree.
 */
class MusterViewModel(
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

    /** Whether this account may start one. The repository checks again. */
    val mayRun: StateFlow<Boolean> = session
        .map { state ->
            (state as? SessionRepository.State.SignedIn)?.role?.canWrite(Lens.EVIDENCE) == true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The roll call that is running, if one is. */
    val live: StateFlow<MusterEntity?> = container.musters.observeLive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The running roll call as the rules see it, names already in reading
     * order. Null when nothing is running.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val roll: StateFlow<Muster.Roll?> = live
        .flatMapLatest { muster ->
            if (muster == null) {
                flowOf(null)
            } else {
                container.musters.observePeople(muster.id)
                    .map { rows -> container.musters.rollOf(muster, rows) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The account ids of first aiders on the roll call.
     *
     * Read from everybody's tickets rather than asked for, so it cannot be
     * stale in the way a list somebody keeps in the hut is. See
     * Muster.firstAiders for what counts.
     */
    private val _firstAidTitles = MutableStateFlow<List<String>>(emptyList())

    init {
        viewModelScope.launch {
            _firstAidTitles.value = container.certifications.suggestedKinds()
                .filter { it.id == FIRST_AID_KIND }
                .flatMap { it.names.values }
        }
    }

    val firstAiders: StateFlow<Set<String>> = combine(
        container.certifications.observeByAccount(),
        _firstAidTitles,
    ) { byAccount, titles ->
        Muster.firstAiders(
            held = byAccount.mapValues { (_, tickets) ->
                tickets.map { Muster.Held(it.title, it.expiresOn) }
            },
            firstAidTitles = titles,
            now = System.currentTimeMillis(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** How many are checked in, for the card that offers to start one. */
    val onSite: StateFlow<Int> = container.musters.observeOnSiteCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** This firm's past roll calls, newest first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val history: StateFlow<List<MusterEntity>> = session
        .flatMapLatest { state ->
            val active = (state as? SessionRepository.State.SignedIn)?.active
            if (active == null) {
                flowOf(emptyList())
            } else {
                container.musters.observeForCompany(active.companyId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Job names by id, so a record can say which site was cleared.
     *
     * The site is on the record already; without this the screen would print
     * a UUID, which is the same as printing nothing.
     */
    val jobNames: StateFlow<Map<String, String>> = container.projects.observeProjects()
        .map { jobs -> jobs.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _refusal = MutableStateFlow<MusterRepository.Refusal?>(null)
    val refusal: StateFlow<MusterRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    /**
     * Puts a sentence on a failure, whatever it turned out to be.
     *
     * Anything that is not a [MusterRepository.Refused] still says something:
     * a roll call that silently does nothing is the worst screen in the app.
     */
    private fun refuse(failure: Throwable) {
        _refusal.value = (failure as? MusterRepository.Refused)?.refusal
            ?: MusterRepository.Refusal.UNKNOWN
    }

    fun start(reason: Muster.Reason, note: String) = viewModelScope.launch {
        val me = signedIn
        if (me == null) {
            _refusal.value = MusterRepository.Refusal.NOT_ALLOWED
            return@launch
        }
        container.musters.start(
            role = me.role,
            companyId = me.active?.companyId,
            reason = reason,
            note = note,
            byAccountId = me.account.id,
            byName = me.account.displayName,
        ).onFailure(::refuse)
    }

    /**
     * One roll call as the rules see it, for sharing: live or ended.
     *
     * Suspending rather than a flow, because it is read once at the moment
     * somebody presses share and must be the list as it stands then.
     */
    suspend fun rollFor(muster: MusterEntity): Muster.Roll =
        container.musters.rollOf(muster, container.musters.peopleOf(muster.id))

    fun present(rowId: String) = viewModelScope.launch {
        container.musters.settle(rowId, Muster.State.PRESENT).onFailure(::refuse)
    }

    fun elsewhere(rowId: String, account: String) = viewModelScope.launch {
        container.musters.settle(rowId, Muster.State.ACCOUNTED_ELSEWHERE, account)
            .onFailure(::refuse)
    }

    fun stillMissing(rowId: String) = viewModelScope.launch {
        container.musters.settle(rowId, Muster.State.UNACCOUNTED).onFailure(::refuse)
    }

    fun add(name: String) = viewModelScope.launch {
        val muster = live.value ?: return@launch
        val actor = signedIn?.account?.displayName.orEmpty()
        container.musters.add(muster.id, name, actor).onFailure(::refuse)
    }

    fun end() = viewModelScope.launch {
        val muster = live.value ?: return@launch
        val actor = signedIn?.account?.displayName.orEmpty()
        container.musters.end(muster.id, actor).onFailure(::refuse)
    }

    private companion object {
        /** The first-aid kind's id in the catalogue manifest. */
        const val FIRST_AID_KIND = "first-aid"
    }
}
