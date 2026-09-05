package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Membership
import il.co.tradesmanager.core.access.Memberships
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.data.local.dao.MembershipDao
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
