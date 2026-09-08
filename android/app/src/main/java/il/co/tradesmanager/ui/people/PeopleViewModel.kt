package il.co.tradesmanager.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Chain
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.data.catalog.ProjectKind
import il.co.tradesmanager.data.local.entity.MembershipEntity
import il.co.tradesmanager.data.local.entity.CertificationEntity
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.repository.AccountRepository
import il.co.tradesmanager.data.repository.MembershipRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PeopleViewModel(private val container: AppContainer) : ViewModel() {

    /**
     * The crew of the company somebody is working in — not every account on
     * the phone.
     *
     * The old list showed all of them, which was fine while a device belonged
     * to one firm and is a leak the moment somebody works for two. Pairing each
     * membership with its account also means a row shows the role held *here*,
     * which is the only role that means anything on this screen.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val members: StateFlow<List<Member>> = container.session.state
        .flatMapLatest { state ->
            val signedIn = state as? SessionRepository.State.SignedIn
            if (signedIn == null) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                combine(
                    container.memberships.observeForCompany(signedIn.active?.companyId),
                    container.accounts.observeAccounts(),
                ) { rows, accounts ->
                    rows.mapNotNull { row ->
                        accounts.firstOrNull { it.id == row.accountId }
                            ?.let { Member(it, row) }
                    }.sortedBy { it.account.displayName }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    /** Account id -> that person's tickets. One query for the whole list. */
    val certifications: StateFlow<Map<String, List<CertificationEntity>>> =
        container.certifications.observeByAccount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _kinds = MutableStateFlow<List<ProjectKind>>(emptyList())
    val kinds: StateFlow<List<ProjectKind>> = _kinds.asStateFlow()

    init {
        viewModelScope.launch { _kinds.value = container.certifications.suggestedKinds() }
    }

    fun addCertification(accountId: String, title: String, reference: String?, expiresOn: Long?) =
        viewModelScope.launch {
            val actor = container.settings.settings.first().actorName
            container.certifications.add(accountId, title, reference, expiresOn, actor)
        }

    fun removeCertification(certification: CertificationEntity) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.certifications.remove(certification, actor)
    }

    /** Set when the app refused a change, so the screen can say why. */
    private val _refusal = MutableStateFlow<AccountRepository.Refusal?>(null)
    val refusal: StateFlow<AccountRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    fun addMember(
        name: String,
        username: String?,
        idNumber: String?,
        phone: String?,
        email: String?,
        role: Role,
        passcode: String?,
    ) = viewModelScope.launch {
        val signedIn = session.value as? SessionRepository.State.SignedIn ?: return@launch
        container.accounts.addMember(
            actorRole = signedIn.role,
            actorName = signedIn.account.displayName,
            companyId = signedIn.active?.companyId,
            name = name,
            role = role,
            passcode = passcode,
            username = username,
            idNumber = idNumber,
            phone = phone,
            email = email,
        ).recordRefusal()
    }

    fun setRole(target: Member, role: Role) = viewModelScope.launch {
        val signedIn = session.value as? SessionRepository.State.SignedIn ?: return@launch
        container.memberships.setRole(
            actorRole = signedIn.role,
            membershipId = target.membership.id,
            role = role,
            actorName = signedIn.account.displayName,
        ).recordRefusal()
    }

    /* ------------------------------------------------- the chain of command */

    /** One person who could be somebody's boss, as the sheet needs them. */
    data class Candidate(val membershipId: String, val name: String)

    /** Set when a move along the chain was refused, so the screen can say why. */
    private val _chainRefusal = MutableStateFlow<Chain.Blocker?>(null)
    val chainRefusal: StateFlow<Chain.Blocker?> = _chainRefusal.asStateFlow()

    fun clearChainRefusal() {
        _chainRefusal.value = null
    }

    /** The name of whoever [target] answers to, or null for nobody yet. */
    fun reportsToName(target: Member): String? = target.membership.reportsToMembershipId
        ?.let { id -> members.value.firstOrNull { it.membership.id == id } }
        ?.account?.displayName

    /**
     * This company's chain, derived from the list already on screen.
     *
     * Read from [members] rather than fetched, so the sheet can ask what to
     * offer without a suspending call in the middle of composition. It decides
     * nothing: every attempt goes through [MembershipRepository.setReportsTo],
     * which reads the chain fresh and applies the same rule, because an actor
     * who was above somebody when the sheet opened may not be by the time they
     * tap.
     */
    private fun chain(): Chain.Company {
        val rows = members.value.map { it.membership }
        return Chain.Company(
            links = rows.map { Chain.Link(it.id, it.reportsToMembershipId) },
            owners = rows.filter { Role.parse(it.role) == Role.OWNER }.map { it.id }.toSet(),
        )
    }

    /**
     * Who [target] could be put under, from this viewer's position.
     *
     * Empty when this viewer has no business moving them at all, so the
     * section is not drawn rather than drawn dead. Otherwise everybody except
     * [target] themselves: which of them would make a circle is [Chain]'s
     * question, answered on the attempt with a sentence saying so, rather than
     * by a chip quietly missing from a row.
     */
    fun candidatesFor(target: Member): List<Candidate> {
        val signedIn = session.value as? SessionRepository.State.SignedIn ?: return emptyList()
        val me = signedIn.active?.id.orEmpty()
        if (!Chain.mayArrange(chain(), me, target.membership.id)) return emptyList()
        return members.value
            .filterNot { it.membership.id == target.membership.id }
            .map { Candidate(it.membership.id, it.account.displayName) }
    }

    fun setReportsTo(target: Member, bossMembershipId: String?) = viewModelScope.launch {
        val signedIn = session.value as? SessionRepository.State.SignedIn ?: return@launch
        container.memberships.setReportsTo(
            actorMembershipId = signedIn.active?.id.orEmpty(),
            membershipId = target.membership.id,
            bossMembershipId = bossMembershipId,
            actorName = signedIn.account.displayName,
        ).onFailure { failure ->
            _chainRefusal.value = (failure as? MembershipRepository.NotReassigned)?.blocker
        }
    }

    fun remove(target: Member) = viewModelScope.launch {
        val signedIn = session.value as? SessionRepository.State.SignedIn ?: return@launch
        container.memberships.leave(
            actorRole = signedIn.role,
            membershipId = target.membership.id,
            actorName = signedIn.account.displayName,
        ).recordRefusal()
    }

    private fun Result<*>.recordRefusal() {
        _refusal.value = (exceptionOrNull() as? AccountRepository.RefusedException)?.refusal
    }

    /**
     * One person as they appear on this company's books: who they are, and
     * what they are *here*. The same account can appear on another company's
     * list with a different role, which is the point.
     */
    data class Member(val account: AccountEntity, val membership: MembershipEntity) {
        val role: Role get() = Role.parse(membership.role)
    }
}
