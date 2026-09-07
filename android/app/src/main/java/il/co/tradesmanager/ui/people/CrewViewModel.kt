package il.co.tradesmanager.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Chain
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.local.entity.CertificationEntity
import il.co.tradesmanager.data.local.entity.MembershipEntity
import il.co.tradesmanager.data.local.entity.TradeEntity
import il.co.tradesmanager.data.local.entity.ViolationEntity
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
 * The crew, by trade, for somebody who has to find one of them on a site.
 *
 * The safety officer's problem, in their words: a man walks away from you, you
 * saw his face, and you know he was doing electrical work. So you open
 * electrical, look at the faces, and find him. A search box is no use — you do
 * not know his name, that is the entire difficulty.
 */
class CrewViewModel(
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

    /** The trades this firm works in, for the chip row. */
    val trades: StateFlow<List<TradeEntity>> = container.trades.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _tradeId = MutableStateFlow<String?>(null)
    val tradeId: StateFlow<String?> = _tradeId.asStateFlow()

    /** Tapping the trade you are already looking at clears it. */
    fun setTrade(id: String?) {
        _tradeId.value = if (_tradeId.value == id) null else id
    }

    /** One person as a face on a list. */
    data class Person(
        val account: AccountEntity,
        val membership: MembershipEntity,
        val faceUri: String?,
    ) {
        val role: Role get() = Role.parse(membership.role)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val everyone: StateFlow<List<Person>> = session
        .flatMapLatest { state ->
            val active = (state as? SessionRepository.State.SignedIn)?.active
            if (active == null) {
                flowOf(emptyList())
            } else {
                combine(
                    container.memberships.observeForCompany(active.companyId),
                    container.accounts.observeAccounts(),
                ) { rows, accounts ->
                    rows.mapNotNull { row ->
                        accounts.firstOrNull { it.id == row.accountId }
                            ?.let { Person(it, row, faceUri = null) }
                    }.sortedBy { it.account.displayName }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Everybody's face, keyed by account.
     *
     * Loaded once for the whole list rather than per row. A list of faces
     * where each row fetches its own is a list that flickers its way down the
     * screen, which is the opposite of useful when somebody is scanning it for
     * a man who just walked off.
     */
    private val faces: StateFlow<Map<String, String>> = container.photos.observeFaces()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val people: StateFlow<List<Person>> = combine(everyone, faces, _tradeId) { all, byId, trade ->
        all
            // Null trade means nobody has said what they do. They stay off the
            // trade lists rather than being guessed onto one -- a labourer on
            // the electricians' list is exactly the mistake this must not make.
            .filter { trade == null || it.membership.tradeId == trade }
            .map { it.copy(faceUri = byId[it.account.id]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** How many people have no trade recorded, so the screen can say so. */
    val withoutTrade: StateFlow<Int> = everyone
        .map { all -> all.count { it.membership.tradeId == null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /* --------------------------------------------------------- one profile */

    private val _openId = MutableStateFlow<String?>(null)

    /**
     * Read out of [people] rather than [everyone], so the face on the profile
     * is the one that was on the row that was tapped. Reading the unfaced list
     * would open a sheet with a blank circle on the screen whose whole purpose
     * is a face.
     */
    val open: StateFlow<Person?> = combine(people, _openId) { all, id ->
        all.firstOrNull { it.membership.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun openProfile(membershipId: String?) {
        _openId.value = membershipId
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val openTickets: StateFlow<List<CertificationEntity>> = open
        .flatMapLatest { person ->
            if (person == null) {
                flowOf(emptyList())
            } else {
                container.certifications.observeForAccount(person.account.id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * What has been confirmed against them, and only what has been confirmed.
     *
     * A draft is one officer's unfinished sentence. Showing it on a profile
     * would put an accusation in front of whoever opens it before the person
     * who wrote it has stood behind it, and the whole point of the confirm
     * step is that it is the moment somebody does.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val openViolations: StateFlow<List<ViolationEntity>> = open
        .flatMapLatest { person ->
            if (person == null) {
                flowOf(emptyList())
            } else {
                container.violations.observeConfirmedAgainst(person.account.id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Who they answer to, by name. Null when nobody has drawn the line yet. */
    val openReportsTo: StateFlow<String?> = combine(open, everyone) { person, all ->
        person?.membership?.reportsToMembershipId
            ?.let { id -> all.firstOrNull { it.membership.id == id } }
            ?.account?.displayName
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Whether this viewer may be shown what the open person is paid.
     *
     * Asked rather than assumed. A safety officer has no Money lens and the
     * answer is always no for them, but a site manager opening the same screen
     * about their own crew has a different answer, and a profile that decided
     * by remembering which screen it was would get one of the two wrong.
     */
    val mayPrice: StateFlow<Boolean> = combine(open, everyone) { person, all ->
        val me = signedIn ?: return@combine false
        if (person == null) return@combine false
        Chain.maySeePay(chainOf(all), me.active?.id.orEmpty(), me.role, person.membership.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** This company's chain, from the list already loaded. One definition. */
    private fun chainOf(all: List<Person>) = Chain.Company(
        links = all.map { Chain.Link(it.membership.id, it.membership.reportsToMembershipId) },
        owners = all.filter { it.role == Role.OWNER }.map { it.membership.id }.toSet(),
    )

    /* ---------------------------------------------------- saying the trade */

    fun setTradeOf(person: Person, tradeId: String?) = viewModelScope.launch {
        val me = signedIn ?: return@launch
        container.memberships.setTrade(
            actorMembershipId = me.active?.id.orEmpty(),
            membershipId = person.membership.id,
            tradeId = tradeId,
            actorName = me.account.displayName,
        )
    }

    /** Whether this viewer may say what somebody's trade is. */
    fun mayName(person: Person): Boolean {
        val me = signedIn ?: return false
        return Chain.mayArrange(
            chainOf(everyone.value),
            me.active?.id.orEmpty(),
            person.membership.id,
        )
    }
}
