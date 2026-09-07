package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Admission
import il.co.tradesmanager.core.access.Chain
import il.co.tradesmanager.core.access.Membership
import il.co.tradesmanager.core.access.Memberships
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.data.local.dao.MembershipDao
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.local.entity.CompanyEntity
import il.co.tradesmanager.data.local.entity.MembershipEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Which companies somebody belongs to, and what they may do in each.
 *
 * The account says who a person is. This says where they work, and there can
 * be more than one answer at once: a tradesperson is on this site today and
 * another tomorrow, and the firm they join next month did not exist when they
 * signed up. Their ID number, their photograph and the induction they signed
 * follow them across all of it; their role does not.
 */
class MembershipRepository(
    private val dao: MembershipDao,
    private val audit: AuditTrail,
) {

    fun observeFor(accountId: String): Flow<List<Membership>> =
        dao.observeFor(accountId).map { rows -> rows.map(::toDomain) }

    fun observeCompanies(): Flow<List<CompanyEntity>> = dao.observeCompanies()

    /** Everyone currently on one company's books. */
    fun observeForCompany(companyId: String?): Flow<List<MembershipEntity>> =
        dao.observeForCompany(companyId)

    suspend fun forAccount(accountId: String): List<Membership> =
        dao.forAccount(accountId).map(::toDomain)

    /**
     * Whether somebody is currently on one company's books.
     *
     * Asked by the gate before anybody is put through the trouble of signing.
     * [admit] asks the database again for itself rather than being told the
     * answer, so this being stale is a worse message and never a second
     * membership.
     */
    suspend fun isCurrentMember(accountId: String, companyId: String?): Boolean =
        dao.currentFor(accountId, companyId) != null

    /**
     * Puts somebody on a company's books.
     *
     * Rejoining a firm somebody has left creates a second row rather than
     * reviving the first. Two spells with the same company are two spells, and
     * flattening them would lose which one a job in March belonged to.
     */
    suspend fun join(
        accountId: String,
        companyId: String?,
        role: Role,
        actorName: String,
    ): Membership {
        val membership = MembershipEntity(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
            companyId = companyId,
            role = role.name,
            joinedAt = System.currentTimeMillis(),
            leftAt = null,
        )
        dao.upsert(membership)
        audit.record(
            ENTITY, membership.id, AuditTrail.Action.CREATE, actorName,
            "Joined as ${role.name.lowercase()}",
        )
        return toDomain(membership)
    }

    /**
     * Admits somebody at the gate: on the books, as a worker, having signed.
     *
     * Separate from [join] rather than a flag on it, because the two are
     * different acts by different people. [join] is an office putting somebody
     * on the books in whatever role was agreed. This is the person on the gate
     * establishing that the man in front of them is who he says he is and has
     * accepted the site's rules, and it grants exactly one role no matter who
     * calls it: see [Admission.grantedRole].
     *
     * [account] is a row already read out of the database, not an id somebody
     * typed. Whether they are already in is worked out here rather than passed
     * in, because a caller that has to be trusted about it is a caller that can
     * be wrong about it, and the cost of being wrong is two memberships and a
     * People list with the same man on it twice.
     */
    suspend fun admit(
        gateKeeperRole: Role,
        gateKeeperName: String,
        gateKeeperAccountId: String,
        companyId: String?,
        account: AccountEntity,
        signature: String,
    ): Result<Membership> {
        val gate = Admission.AtTheGate(
            gateKeeperRole = gateKeeperRole,
            foundAccountId = account.id,
            signed = signature.isNotBlank(),
            alreadyIn = dao.currentFor(account.id, companyId) != null,
        )
        Admission.blocksAdmission(gate)?.let { return Result.failure(NotAdmitted(it)) }

        val role = Admission.grantedRole()
        // Asserted rather than assumed. If a later edit ever makes
        // grantedRole() return something else, this fails here, in the one
        // place that writes the row, instead of quietly promoting people.
        check(Admission.gateMayGrant(role)) { "the gate may not grant $role" }

        val membership = MembershipEntity(
            id = UUID.randomUUID().toString(),
            accountId = account.id,
            companyId = companyId,
            role = role.name,
            joinedAt = System.currentTimeMillis(),
            leftAt = null,
            admittedByAccountId = gateKeeperAccountId,
            admittedByName = gateKeeperName,
            admissionSignature = signature,
        )
        dao.upsert(membership)
        audit.record(
            ENTITY, membership.id, AuditTrail.Action.CREATE, gateKeeperName,
            "Admitted ${account.displayName} at the gate as ${role.name.lowercase()}",
        )
        return Result.success(toDomain(membership))
    }

    /** Why an admission was refused, carrying the rule that refused it. */
    class NotAdmitted(val blocker: Admission.Blocker) :
        IllegalStateException("admission refused: $blocker")

    /**
     * Changes what somebody may do in one company.
     *
     * The last-owner guard counts owners *of that company*. Being the last
     * owner of one firm says nothing about your standing in another, and the
     * account-wide check this replaces could not tell the difference — it would
     * refuse a change that strands nobody.
     */
    suspend fun setRole(
        actorRole: Role,
        membershipId: String,
        role: Role,
        actorName: String,
    ): Result<Unit> {
        if (!actorRole.canManageMembers) {
            return Result.failure(AccountRepository.RefusedException(AccountRepository.Refusal.NotPermitted))
        }
        val target = dao.membership(membershipId)?.let(::toDomain)
            ?: return Result.failure(
                AccountRepository.RefusedException(AccountRepository.Refusal.NotPermitted),
            )
        if (stranded(target, role)) {
            return Result.failure(
                AccountRepository.RefusedException(AccountRepository.Refusal.LastAdministrator),
            )
        }
        dao.setRole(membershipId, role.name)
        audit.record(
            ENTITY, membershipId, AuditTrail.Action.UPDATE, actorName,
            "Role set to ${role.name.lowercase()}",
        )
        return Result.success(Unit)
    }

    /* ------------------------------------------------- the chain of command */

    /**
     * One company's chain, as [Chain] wants it.
     *
     * Current memberships only. Somebody who left in March is not on anybody's
     * crew now, and leaving them in would keep a branch of the chart alive
     * under a person who is gone.
     */
    suspend fun chainOf(companyId: String?): Chain.Company {
        if (companyId == null) return Chain.Company(links = emptyList())
        val current = dao.forCompany(companyId).filter { it.leftAt == null }
        return Chain.Company(
            links = current.map { Chain.Link(it.id, it.reportsToMembershipId) },
            owners = current.filter { Role.parse(it.role) == Role.OWNER }.map { it.id }.toSet(),
        )
    }

    /** Everybody whose pay [membershipId] may be shown, themselves included. */
    suspend fun payVisibleTo(companyId: String?, membershipId: String, role: Role): Set<String> =
        Chain.payVisibleTo(chainOf(companyId), membershipId, role)

    /**
     * Puts somebody on somebody else's crew.
     *
     * The rule is [Chain.blocksReporting] and it is asked here rather than
     * only at the screen, because a screen that is never shown is not a rule.
     * The chain is read fresh from the database for the same reason: an actor
     * who was above this person when the screen opened may not be by the time
     * they tap.
     */
    suspend fun setReportsTo(
        actorMembershipId: String,
        membershipId: String,
        bossMembershipId: String?,
        actorName: String,
    ): Result<Unit> {
        val target = dao.membership(membershipId)
            ?: return Result.failure(NotReassigned(Chain.Blocker.UNKNOWN))
        val company = chainOf(target.companyId)
        Chain.blocksReporting(
            company = company,
            actorMembershipId = actorMembershipId,
            subjectMembershipId = membershipId,
            newBossMembershipId = bossMembershipId,
        )?.let { return Result.failure(NotReassigned(it)) }

        dao.setReportsTo(membershipId, bossMembershipId)
        audit.record(
            ENTITY, membershipId, AuditTrail.Action.UPDATE, actorName,
            if (bossMembershipId == null) "Reports to nobody" else "Now reports to $bossMembershipId",
        )
        return Result.success(Unit)
    }

    /** Why a move along the chain was refused, carrying the rule that refused it. */
    class NotReassigned(val blocker: Chain.Blocker) :
        IllegalStateException("chain change refused: $blocker")

    /** Takes somebody off a company's books, keeping the record that they were on them. */
    suspend fun leave(actorRole: Role, membershipId: String, actorName: String): Result<Unit> {
        if (!actorRole.canManageMembers) {
            return Result.failure(
                AccountRepository.RefusedException(AccountRepository.Refusal.NotPermitted),
            )
        }
        val target = dao.membership(membershipId)?.let(::toDomain)
            ?: return Result.failure(
                AccountRepository.RefusedException(AccountRepository.Refusal.NotPermitted),
            )
        if (stranded(target, newRole = null)) {
            return Result.failure(
                AccountRepository.RefusedException(AccountRepository.Refusal.LastAdministrator),
            )
        }
        // Their crew moves up to whoever they answered to, before the row is
        // closed. A link left pointing at somebody who has gone is a branch of
        // the chart with nobody above it, and a branch with nobody above it is
        // a payroll nobody can read.
        val liftedTo = dao.membership(membershipId)?.reportsToMembershipId
        dao.reportingTo(membershipId).forEach { dao.setReportsTo(it.id, liftedTo) }

        dao.markLeft(membershipId, System.currentTimeMillis())
        audit.record(ENTITY, membershipId, AuditTrail.Action.DELETE, actorName, "Left the company")
        return Result.success(Unit)
    }

    private suspend fun stranded(target: Membership, newRole: Role?): Boolean {
        val companyId = target.companyId ?: return false
        val siblings = dao.forCompany(companyId).map(::toDomain)
        return Memberships.wouldStrandCompany(siblings, target, newRole)
    }

    private fun toDomain(row: MembershipEntity) = Membership(
        id = row.id,
        companyId = row.companyId,
        role = Role.parse(row.role),
        joinedAt = row.joinedAt,
        leftAt = row.leftAt,
    )

    private companion object {
        const val ENTITY = "membership"
    }
}
