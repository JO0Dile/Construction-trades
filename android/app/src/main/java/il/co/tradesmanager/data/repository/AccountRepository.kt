package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.security.Passcode
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
    private val audit: AuditTrail,
) {

    sealed interface SignIn {
        data class Success(val account: AccountEntity) : SignIn
        data object WrongPasscode : SignIn
        data object NoSuchAccount : SignIn
    }

    /** Why a member could not be removed or demoted. */
    sealed interface Refusal {
        data object LastAdministrator : Refusal
        data object NotPermitted : Refusal
    }

    fun observeAccounts(): Flow<List<AccountEntity>> = dao.observeAccounts()
    fun observeAccount(id: String): Flow<AccountEntity?> = dao.observeAccount(id)
    fun observeCompany(): Flow<CompanyEntity?> = dao.observeCompany()

    suspend fun hasAnyAccount(): Boolean = dao.accountCount() > 0

    suspend fun account(id: String): AccountEntity? = dao.account(id)

    /**
     * The first account on the device: one person, their own tools.
     *
     * Stored as an owner so every permission check has one shape — a sole
     * trader never meets the permission model at all.
     */
    suspend fun createPersonalAccount(name: String, passcode: String?): AccountEntity =
        create(name = name, companyId = null, role = Role.OWNER, passcode = passcode)

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
        )
    }

    /** Adds a member. Only an owner or HR gets here — see [Role.canManageMembers]. */
    suspend fun addMember(
        actor: AccountEntity,
        name: String,
        role: Role,
        passcode: String?,
    ): Result<AccountEntity> {
        if (!Role.parse(actor.role).canManageMembers) {
            return Result.failure(RefusedException(Refusal.NotPermitted))
        }
        return Result.success(
            create(
                name = name,
                companyId = actor.companyId,
                role = role,
                passcode = passcode,
                actorName = actor.displayName,
            ),
        )
    }

    /**
     * Changes what someone may see.
     *
     * Refuses to remove the last administrator. Locking every person out of
     * their own company's data is not a permission decision, it is a data-loss
     * event with a permissions-shaped cause.
     */
    suspend fun setRole(actor: AccountEntity, target: AccountEntity, role: Role): Result<Unit> {
        if (!Role.parse(actor.role).canManageMembers) {
            return Result.failure(RefusedException(Refusal.NotPermitted))
        }
        if (wouldStrandTheCompany(target, role)) {
            return Result.failure(RefusedException(Refusal.LastAdministrator))
        }
        dao.setRole(target.id, role.name)
        audit.record(
            ENTITY, target.id, AuditTrail.Action.UPDATE, actor.displayName,
            "${target.displayName} is now ${role.name.lowercase()}",
        )
        return Result.success(Unit)
    }

    suspend fun remove(actor: AccountEntity, target: AccountEntity): Result<Unit> {
        if (!Role.parse(actor.role).canManageMembers) {
            return Result.failure(RefusedException(Refusal.NotPermitted))
        }
        if (wouldStrandTheCompany(target, newRole = null)) {
            return Result.failure(RefusedException(Refusal.LastAdministrator))
        }
        // Soft delete: the audit trail names people, so their rows have to stay.
        dao.softDelete(target.id, System.currentTimeMillis())
        audit.record(
            ENTITY, target.id, AuditTrail.Action.DELETE, actor.displayName, target.displayName,
        )
        return Result.success(Unit)
    }

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
     * True when this change would leave nobody able to administer the company.
     * A personal account cannot strand anyone, so it is always free to change.
     */
    private suspend fun wouldStrandTheCompany(target: AccountEntity, newRole: Role?): Boolean {
        if (target.companyId == null) return false
        if (Role.parse(target.role) != Role.OWNER) return false
        if (newRole == Role.OWNER) return false
        return dao.countWithRole(Role.OWNER.name) <= 1
    }

    private suspend fun create(
        name: String,
        companyId: String?,
        role: Role,
        passcode: String?,
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
        )
        dao.upsert(account)
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
