package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Membership
import il.co.tradesmanager.core.access.Memberships
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.safety.Induction
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.local.entity.CompanyEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Who is signed in, and what that lets them do.
 *
 * One object rather than a role passed down through every screen: a permission
 * check is `session.canWrite(Lens.MONEY)` wherever it happens, which is the
 * point of expressing permissions as five lenses instead of as a list of
 * screens.
 */
class SessionRepository(
    private val settings: SettingsRepository,
    private val accounts: AccountRepository,
    private val memberships: MembershipRepository,
) {

    /**
     * What the app knows about the person using it.
     *
     * [SignedOut] and [NeedsSetup] are different states on purpose. Signed out
     * means pick an account; needs setup means there are no accounts at all,
     * and asking someone to choose from an empty list is not a screen.
     */
    sealed interface State {
        data object Loading : State
        data object NeedsSetup : State
        data object SignedOut : State
        /**
         * Somebody is signed in, in one of the companies they belong to.
         *
         * The account is who they are; the membership is where they are. The
         * role comes from the membership and never from the account, because
         * the same person is a site manager for one firm and on the tools for
         * another and there is no single answer to store on a person.
         */
        data class SignedIn(
            val account: AccountEntity,
            val memberships: List<Membership>,
            val companies: List<CompanyEntity>,
            /** What they last chose. Only counts while it is still current. */
            val activeCompanyId: String?,
        ) : State {
            val active: Membership? get() = Memberships.active(memberships, activeCompanyId)

            val role: Role get() = Memberships.roleFor(memberships, activeCompanyId)

            val company: CompanyEntity?
                get() = active?.companyId?.let { id -> companies.firstOrNull { it.id == id } }

            val isCompany: Boolean get() = active?.companyId != null

            /** The companies they can switch between, newest first. */
            val switchable: List<Membership> get() = Memberships.switchable(memberships)

            fun companyNamed(id: String?): CompanyEntity? =
                id?.let { wanted -> companies.firstOrNull { it.id == wanted } }

            /**
             * True until this person has read and signed the safety induction.
             *
             * Read off the account row rather than held as a flag somewhere,
             * so it survives the app being killed mid-induction: somebody who
             * closes the app on the PPE page comes back to the PPE page, not
             * to the inside of the app.
             */
            val needsInduction: Boolean get() = Induction.isRequired(account.inductedAt)

            /** Which induction they get — see [Induction.levelFor]. */
            val inductionLevel: Induction.Level get() = Induction.levelFor(role)

            fun canRead(lens: Lens): Boolean = role.canRead(lens)
            fun canWrite(lens: Lens): Boolean = role.canWrite(lens)
            val canManageMembers: Boolean get() = role.canManageMembers
            val canManageJobs: Boolean get() = role.canManageJobs
        }
    }

/**
     * Memberships are looked up per signed-in account rather than for
     * everybody, so the flow does not churn every time somebody else's row
     * changes. flatMapLatest because who is signed in decides which
     * memberships to watch, and that can change underneath us.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<State> = combine(
        settings.settings,
        accounts.observeAccounts(),
        memberships.observeCompanies(),
    ) { current, allAccounts, companies -> Triple(current, allAccounts, companies) }
        .flatMapLatest { (current, allAccounts, companies) ->
            val signedInId = current.signedInAccountId
            val account = signedInId?.let { id -> allAccounts.firstOrNull { it.id == id } }
            when {
                allAccounts.isEmpty() -> flowOf(State.NeedsSetup)
                // Signed out, or the account was removed while they were using it.
                account == null -> flowOf(State.SignedOut)
                else -> memberships.observeFor(account.id).map { rows ->
                    State.SignedIn(
                        account = account,
                        memberships = rows,
                        companies = companies,
                        activeCompanyId = current.activeCompanyId,
                    )
                }
            }
        }

    /**
     * Switches which company somebody is working in.
     *
     * Only a preference is written. Whether it takes effect is decided by
     * [Memberships.active] against the memberships that are actually current,
     * so choosing a firm you have been taken off does nothing.
     */
    suspend fun switchCompany(companyId: String?) = settings.setActiveCompany(companyId)

    suspend fun signIn(accountId: String, passcode: String): AccountRepository.SignIn {
        val result = accounts.signIn(accountId, passcode)
        if (result is AccountRepository.SignIn.Success) {
            settings.setSignedInAccount(result.account.id, result.account.displayName)
        }
        return result
    }

    /** Signing in with the name or ID number and password a manager handed over. */
    suspend fun signInByIdentifier(identifier: String, passcode: String): AccountRepository.SignIn {
        val result = accounts.signInByIdentifier(identifier, passcode)
        if (result is AccountRepository.SignIn.Success) {
            settings.setSignedInAccount(result.account.id, result.account.displayName)
        }
        return result
    }

    /** Used straight after creating an account, which needs no passcode check. */
    suspend fun adopt(account: AccountEntity) {
        settings.setSignedInAccount(account.id, account.displayName)
    }

    suspend fun signOut() = settings.signOut()

    /** The account for a one-off action, or null if nobody is signed in. */
    suspend fun current(): AccountEntity? =
        settings.settings.first().signedInAccountId?.let { accounts.account(it) }
}
