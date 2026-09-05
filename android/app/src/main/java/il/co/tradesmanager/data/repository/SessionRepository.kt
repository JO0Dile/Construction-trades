package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.safety.Induction
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.local.entity.CompanyEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
        data class SignedIn(
            val account: AccountEntity,
            val company: CompanyEntity?,
        ) : State {
            val role: Role get() = Role.parse(account.role)
            val isCompany: Boolean get() = company != null

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

    val state: Flow<State> = combine(
        settings.settings.map { it.signedInAccountId },
        accounts.observeAccounts(),
        accounts.observeCompany(),
    ) { signedInId, allAccounts, company ->
        when {
            allAccounts.isEmpty() -> State.NeedsSetup
            signedInId == null -> State.SignedOut
            else -> allAccounts.firstOrNull { it.id == signedInId }
                // The signed-in account was removed while they were using it.
                ?.let { State.SignedIn(it, company) }
                ?: State.SignedOut
        }
    }

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
