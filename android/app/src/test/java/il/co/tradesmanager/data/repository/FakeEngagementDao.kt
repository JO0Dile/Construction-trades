package il.co.tradesmanager.data.repository

import il.co.tradesmanager.data.local.dao.AuditDao
import il.co.tradesmanager.data.local.dao.EngagementDao
import il.co.tradesmanager.data.local.entity.AssignmentEntity
import il.co.tradesmanager.data.local.entity.AuditLogEntity
import il.co.tradesmanager.data.local.entity.ContractAmendmentEntity
import il.co.tradesmanager.data.local.entity.ContractEntity
import il.co.tradesmanager.data.local.entity.EngagementEntity
import il.co.tradesmanager.data.local.entity.TeamMemberEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The engagement tables, in memory.
 *
 * `EngagementRepository` is where the rules in `core` are actually enforced on
 * a write, and until now it had no tests at all: exercising it needs Room, and
 * Room needs a build. The DAO is an interface, so it does not — a fake that
 * keeps rows in a list runs on the JVM in milliseconds and still puts the
 * repository's own logic under test, which is the part that decides whether a
 * crew can approve its own work.
 *
 * The queries are reimplemented here rather than mocked away, because two of
 * them are the confidentiality rule expressed in SQL. A fake that returned
 * everything would let a test pass while the real query leaked.
 */
class FakeEngagementDao : EngagementDao {

    private val engagements = MutableStateFlow<List<EngagementEntity>>(emptyList())
    private val contracts = MutableStateFlow<List<ContractEntity>>(emptyList())
    private val amendments = MutableStateFlow<List<ContractAmendmentEntity>>(emptyList())
    private val assignments = MutableStateFlow<List<AssignmentEntity>>(emptyList())

    override suspend fun upsert(engagement: EngagementEntity) {
        engagements.value = engagements.value.filterNot { it.id == engagement.id } + engagement
    }

    override fun observeForProject(projectId: String): Flow<List<EngagementEntity>> =
        engagements.map { all ->
            all.filter { it.projectId == projectId }
                .sortedWith(compareBy({ it.endedAt != null }, { it.orgName }))
        }

    override suspend fun forOrg(projectId: String, orgId: String): EngagementEntity? =
        engagements.value.firstOrNull { it.projectId == projectId && it.orgId == orgId }

    override suspend fun upsert(contract: ContractEntity) {
        contracts.value = contracts.value.filterNot { it.id == contract.id } + contract
    }

    /**
     * The confidentiality filter, matching the SQL exactly — including the
     * bounding commas on the disclosure list, which is what stops an org id
     * that is a prefix of another's from matching.
     */
    override fun observeContractsFor(projectId: String, orgId: String): Flow<List<ContractEntity>> =
        contracts.map { all ->
            all.filter {
                it.projectId == projectId && (
                    it.payerOrgId == orgId ||
                        it.payeeOrgId == orgId ||
                        it.disclosedToOrgIds.contains(",$orgId,")
                    )
            }.sortedByDescending { it.createdAt }
        }

    override suspend fun contract(id: String): ContractEntity? =
        contracts.value.firstOrNull { it.id == id }

    override suspend fun contractCount(): Int = contracts.value.size

    override suspend fun upsert(amendment: ContractAmendmentEntity) {
        amendments.value = amendments.value.filterNot { it.id == amendment.id } + amendment
    }

    override fun observeAmendments(contractId: String): Flow<List<ContractAmendmentEntity>> =
        amendments.map { all -> all.filter { it.contractId == contractId }.sortedBy { it.version } }

    override suspend fun amendments(contractId: String): List<ContractAmendmentEntity> =
        amendments.value.filter { it.contractId == contractId }.sortedBy { it.version }

    override suspend fun openAmendments(contractId: String): Int =
        amendments.value.count { it.contractId == contractId && it.status == "PROPOSED" }

    override suspend fun lastAmendmentVersion(contractId: String): Int =
        amendments.value.filter { it.contractId == contractId }.maxOfOrNull { it.version } ?: 0

    override suspend fun upsert(assignment: AssignmentEntity) {
        assignments.value = assignments.value.filterNot { it.id == assignment.id } + assignment
    }

    override fun observeAssignment(id: String): Flow<AssignmentEntity?> =
        assignments.map { all -> all.firstOrNull { it.id == id } }

    override fun observeAssignments(
        projectId: String,
        orgId: String,
    ): Flow<List<AssignmentEntity>> =
        assignments.map { all ->
            all.filter {
                it.projectId == projectId && (it.payerOrgId == orgId || it.payeeOrgId == orgId)
            }
        }

    override suspend fun assignmentCount(): Int = assignments.value.size

    /** What the repository actually wrote, for asserting on. */
    fun assignment(id: String): AssignmentEntity? = assignments.value.firstOrNull { it.id == id }

    fun contractRows(): List<ContractEntity> = contracts.value

    fun amendmentRows(): List<ContractAmendmentEntity> = amendments.value

    fun engagementRows(): List<EngagementEntity> = engagements.value
}

/** Collects audit rows so a test can assert that a change was recorded. */
class FakeAuditDao : AuditDao {

    val entries = mutableListOf<AuditLogEntity>()

    override suspend fun insert(entry: AuditLogEntity) { entries += entry }

    override fun observeRecent(limit: Int): Flow<List<AuditLogEntity>> =
        MutableStateFlow(entries.toList())

    override fun observeFor(type: String, id: String): Flow<List<AuditLogEntity>> =
        MutableStateFlow(entries.filter { it.entityType == type && it.entityId == id })

    override suspend fun exportSince(since: Long): List<AuditLogEntity> =
        entries.filter { it.occurredAt >= since }

    override suspend fun purgeOlderThan(cutoff: Long): Int {
        val before = entries.size
        entries.removeAll { it.occurredAt < cutoff }
        return before - entries.size
    }

    override suspend fun upsertMember(member: TeamMemberEntity) = Unit

    override fun observeMembers(): Flow<List<TeamMemberEntity>> = MutableStateFlow(emptyList())
}
