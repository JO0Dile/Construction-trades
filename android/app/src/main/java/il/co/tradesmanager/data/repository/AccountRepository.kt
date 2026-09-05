package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.security.Passcode
import il.co.tradesmanager.core.security.Signature
import il.co.tradesmanager.data.local.dao.AccountDao
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.local.entity.CompanyEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Who works here, and what each of them may see.
 *
 * Local-first and unapologetically so: an account is a row on this device, not
 * a session with a server. A crew in a basement car park with no signal has to
 * be able to sign in and record what they did, and any design that phones home
 * to authenticate fails that on day one. Sync, when it comes, adds a server
 * copy of these rows — it does not change what a role means or where the check
 * happens.
 */
class AccountRepository(
    private val dao: AccountDao,
    private val memberships: MembershipRepository,
    private val audit: AuditTrail,
) {

    sealed interface SignIn {
        data class Success(val account: AccountEntity) : SignIn
        data object WrongPasscode : SignIn
        data object NoSuchAccount : SignIn

        /**
         * The name and the password together did not match anybody.
         *
         * Deliberately one answer rather than two. Telling somebody that the
         * name exists but the password is wrong tells them which of their
         * colleagues to keep guessing at, and the person signing in gained
         * nothing from the distinction — they retype both either way.
         */
        data object WrongCredentials : SignIn
    }

    /** Why a member could not be removed or demoted. */
    sealed interface Refusal {
        data object LastAdministrator : Refusal
        data object NotPermitted : Refusal
    }

    fun observeAccounts(): Flow<List<AccountEntity>> = dao.observeAccounts()
    fun observeAccount(id: String): Flow<AccountEntity?> = dao.observeAccount(id)

    suspend fun hasAnyAccount(): Boolean = dao.accountCount() > 0

    suspend fun account(id: String): AccountEntity? = dao.account(id)

    /**
     * The first account on the device: one person, their own tools.
     *
     * Stored as an owner so every permission check has one shape — a sole
     * trader never meets the permission model at all.
     */
    suspend fun createPersonalAccount(
        name: String,
        passcode: String?,
        username: String? = null,
        idNumber: String? = null,
    ): AccountEntity =
        create(
            name = name,
            companyId = null,
            role = Role.OWNER,
            passcode = passcode,
            username = username,
            idNumber = idNumber,
            // Their own tools: a membership with no company behind it, so
            // every permission check has exactly one shape to read.
            joinCompanyId = null,
        )

    /**
     * A company and the person setting it up, who is its owner. Both rows are
     * written together because a company with no members cannot be signed into
     * and would just be a dead end in the sign-in list.
     */
    suspend fun createCompany(
        companyName: String,
        registrationNumber: String?,
        ownerName: String,
        passcode: String?,
        ownerUsername: String? = null,
        ownerIdNumber: String? = null,
    ): AccountEntity {
        val now = System.currentTimeMillis()
        val company = CompanyEntity(
            id = UUID.randomUUID().toString(),
            name = companyName.trim(),
            registrationNumber = registrationNumber?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertCompany(company)
        audit.record("company", company.id, AuditTrail.Action.CREATE, ownerName, company.name)
        return create(
            name = ownerName,
            companyId = company.id,
            role = Role.OWNER,
            passcode = passcode,
            username = ownerUsername,
            idNumber = ownerIdNumber,
            joinCompanyId = company.id,
        )
    }

    /** Adds a member. Only an owner or HR gets here — see [Role.canManageMembers]. */
    /**
     * Adds somebody to the company the person adding them is working in.
     *
     * [actorRole] and [companyId] come from the caller's active membership,
     * not from the actor's account row: whoever is adding a member may be an
     * owner here and on the tools somewhere else, and only the membership in
     * force can say which.
     */
    suspend fun addMember(
        actorRole: Role,
        actorName: String,
        companyId: String?,
        name: String,
        role: Role,
        passcode: String?,
        username: String? = null,
        idNumber: String? = null,
    ): Result<AccountEntity> {
        if (!actorRole.canManageMembers) {
            return Result.failure(RefusedException(Refusal.NotPermitted))
        }
        return Result.success(
            create(
                name = name,
                companyId = companyId,
                role = role,
                passcode = passcode,
                username = username,
                idNumber = idNumber,
                joinCompanyId = companyId,
                actorName = actorName,
            ),
        )
    }

    /**
     * Changing a role and taking somebody off the books both moved to
     * [MembershipRepository]. They are things that happen to a person *in a
     * company*, and once somebody can be in two, an account-level answer is
     * the wrong shape — demoting a manager on one site must not touch what
     * they are on another.
     */

    suspend fun setPasscode(account: AccountEntity, passcode: String?) {
        val hashed = passcode?.takeIf { it.isNotBlank() }?.let { Passcode.hash(it) }
        dao.setPasscode(account.id, hashed?.hash, hashed?.salt)
        audit.record(
            ENTITY, account.id, AuditTrail.Action.UPDATE, account.displayName,
            if (hashed == null) "Passcode removed" else "Passcode changed",
        )
    }

    suspend fun signIn(accountId: String, passcode: String): SignIn {
        val account = dao.account(accountId)?.takeIf { it.deletedAt == null }
            ?: return SignIn.NoSuchAccount
        if (!Passcode.verify(passcode, account.passcodeHash, account.passcodeSalt)) {
            return SignIn.WrongPasscode
        }
        val now = System.currentTimeMillis()
        dao.recordSignIn(account.id, now)
        return SignIn.Success(account.copy(lastSignInAt = now))
    }

    /**
     * Signs in by the name or ID number the site office gave somebody, rather
     * than by picking their row off a list.
     *
     * The list was the wrong shape twice over. It showed everyone on the
     * device to anyone holding it — a crew roster to whoever picks the tablet
     * up — and it did not match how people are actually told who they are: a
     * manager hands over a name and a password.
     *
     * Names on a site repeat. Two Hammams on one job is normal, so every
     * account matching the name is tried and the password decides which one
     * was meant. The ID number is accepted in the same field because it is the
     * thing that does not repeat.
     */
    suspend fun signInByIdentifier(identifier: String, passcode: String): SignIn {
        val typed = identifier.trim()
        if (typed.isEmpty()) return SignIn.WrongCredentials

        val candidates = dao.accountsMatching(typed).filter { it.deletedAt == null }
        val account = candidates.firstOrNull {
            Passcode.opens(passcode, it.passcodeHash, it.passcodeSalt)
        } ?: return SignIn.WrongCredentials

        val now = System.currentTimeMillis()
        dao.recordSignIn(account.id, now)
        return SignIn.Success(account.copy(lastSignInAt = now))
    }

    /**
     * Records that somebody read the safety induction and signed it.
     *
     * Refuses a signature that is not one. The induction is the record that
     * says a person was told what to wear and who to tell, and a stray tap on
     * the glass has told them nothing — so the check lives here, on the only
     * path that can set the date, rather than only on the screen.
     */
    suspend fun recordInduction(accountId: String, signature: String): Boolean {
        if (!Signature.isSigned(signature)) return false
        val account = dao.account(accountId)?.takeIf { it.deletedAt == null } ?: return false
        val now = System.currentTimeMillis()
        dao.upsert(account.copy(inductionSignature = signature, inductedAt = now))
        audit.record(
            ENTITY, account.id, AuditTrail.Action.SIGN_OFF, account.displayName,
            "Safety induction signed",
        )
        return true
    }

    /** Whether an ID number is already spoken for, so sign-up can say so. */
    suspend fun isIdNumberTaken(idNumber: String): Boolean =
        idNumber.isNotBlank() && dao.countWithIdNumber(idNumber) > 0

    /**
     * Sets the ID number, once.
     *
     * A one-way door on purpose. This is what a site office identifies a person
     * by — it goes on the gate list and on the induction record — so quietly
     * editing it is how one person ends up standing behind another person's
     * paperwork. Adding a missing one is welcome; changing a set one is a
     * conversation with the office, not a text field.
     *
     * Returns false when there is already one, so a caller cannot overwrite it
     * by mistake even if a screen forgets to disable the field.
     */
    suspend fun setIdNumber(accountId: String, idNumber: String): Boolean {
        val trimmed = idNumber.trim()
        if (trimmed.isEmpty()) return false
        val account = dao.account(accountId)?.takeIf { it.deletedAt == null } ?: return false
        if (!account.idNumber.isNullOrBlank()) return false
        if (isIdNumberTaken(trimmed)) return false
        dao.upsert(account.copy(idNumber = trimmed))
        audit.record(ENTITY, account.id, AuditTrail.Action.UPDATE, account.displayName, "ID number set")
        return true
    }

    private suspend fun create(
        name: String,
        companyId: String?,
        role: Role,
        passcode: String?,
        username: String? = null,
        idNumber: String? = null,
        joinCompanyId: String? = null,
        actorName: String = name.trim(),
    ): AccountEntity {
        val hashed = passcode?.takeIf { it.isNotBlank() }?.let { Passcode.hash(it) }
        val account = AccountEntity(
            id = UUID.randomUUID().toString(),
            displayName = name.trim(),
            companyId = companyId,
            role = role.name,
            passcodeHash = hashed?.hash,
            passcodeSalt = hashed?.salt,
            createdAt = System.currentTimeMillis(),
            lastSignInAt = null,
            deletedAt = null,
            username = username?.trim()?.takeIf { it.isNotEmpty() },
            idNumber = idNumber?.trim()?.takeIf { it.isNotEmpty() },
            // Nobody is inducted by being created. The person themselves reads
            // it and signs it, on their own first sign-in.
            inductionSignature = null,
            inductedAt = null,
        )
        dao.upsert(account)
        // The membership, not the account row, is what says what they may
        // do — and it is written on the same code path, so an account without
        // one would have to be a deliberate edit rather than an oversight.
        memberships.join(
            accountId = account.id,
            companyId = joinCompanyId,
            role = role,
            actorName = actorName,
        )
        audit.record(
            ENTITY, account.id, AuditTrail.Action.CREATE, actorName,
            "${account.displayName} (${role.name.lowercase()})",
        )
        return account
    }

    class RefusedException(val refusal: Refusal) : Exception(refusal.toString())

    private companion object {
        const val ENTITY = "account"
    }
}
