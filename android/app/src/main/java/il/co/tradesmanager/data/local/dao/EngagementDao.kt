package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.AssignmentEntity
import il.co.tradesmanager.data.local.entity.ContractAmendmentEntity
import il.co.tradesmanager.data.local.entity.ContractEntity
import il.co.tradesmanager.data.local.entity.EngagementEntity
import kotlinx.coroutines.flow.Flow

/**
 * Reading and writing the contracting chain.
 *
 * **Nothing here deletes.** That is deliberate rather than unfinished:
 *
 * - A work package carries proof photographs keyed on its id, so removing the
 *   row would leave the pictures behind with nothing pointing at them — and
 *   those pictures are the evidence the work was done.
 * - An amendment is the record of what a price went from and to. A contract
 *   that could be deleted would take its own price history with it, which is
 *   the exact failure the amendment workflow exists to prevent.
 * - Who was on a job in March is asked in September, usually with a solicitor
 *   in the room. An engagement ends with `endedAt`, not by vanishing.
 *
 * Anything that genuinely has to go — a package raised against the wrong firm
 * — is cancelled through `core.work.Assignment`, which leaves a row saying so.
 * If a delete is ever needed, it needs a retention rule and an audit entry
 * first, not an `@Delete` annotation.
 */
@Dao
interface EngagementDao {

    @Upsert
    suspend fun upsert(engagement: EngagementEntity)

    /**
     * Everyone on the job, still engaged first.
     *
     * Ordered by [EngagementEntity.party] as stored rather than by tier depth,
     * because depth is a property of the enum in `core` and SQL has no
     * business knowing it. The list is short; the caller sorts.
     */
    @Query(
        """
        SELECT * FROM engagements
        WHERE projectId = :projectId
        ORDER BY endedAt IS NOT NULL, orgName
        """,
    )
    fun observeForProject(projectId: String): Flow<List<EngagementEntity>>

    @Query("SELECT * FROM engagements WHERE projectId = :projectId AND orgId = :orgId LIMIT 1")
    suspend fun forOrg(projectId: String, orgId: String): EngagementEntity?

    // ---- contracts ------------------------------------------------------

    @Upsert
    suspend fun upsert(contract: ContractEntity)

    /**
     * The contracts this organisation is a party to on this job.
     *
     * The filter is the confidentiality rule expressed in SQL, and it is here
     * rather than in the caller for one reason: a query that returned every
     * contract and left the filtering to Kotlin would put the general
     * contractor's figures in memory on a crew leader's phone. See
     * `core.access.Commercial`.
     *
     * The disclosure list is stored comma-delimited **including the outer
     * commas** — `,org.a,org.b,` — so the match is on `,id,` and not on a
     * bare substring. Without the delimiters an org whose id is a prefix of
     * another's would be handed a contract it was never disclosed to, which
     * is a confidentiality bug that only shows up on the one customer whose
     * ids happen to collide.
     */
    @Query(
        """
        SELECT * FROM contracts
        WHERE projectId = :projectId
          AND (payerOrgId = :orgId OR payeeOrgId = :orgId
               OR disclosedToOrgIds LIKE '%,' || :orgId || ',%')
        ORDER BY createdAt DESC
        """,
    )
    fun observeContractsFor(projectId: String, orgId: String): Flow<List<ContractEntity>>

    @Query("SELECT * FROM contracts WHERE id = :id")
    suspend fun contract(id: String): ContractEntity?

    @Query("SELECT COUNT(*) FROM contracts")
    suspend fun contractCount(): Int

    // ---- amendments -----------------------------------------------------

    @Upsert
    suspend fun upsert(amendment: ContractAmendmentEntity)

    @Query("SELECT * FROM contract_amendments WHERE contractId = :contractId ORDER BY version")
    fun observeAmendments(contractId: String): Flow<List<ContractAmendmentEntity>>

    @Query("SELECT * FROM contract_amendments WHERE contractId = :contractId ORDER BY version")
    suspend fun amendments(contractId: String): List<ContractAmendmentEntity>

    @Query(
        """
        SELECT COUNT(*) FROM contract_amendments
        WHERE contractId = :contractId AND status = 'PROPOSED'
        """,
    )
    suspend fun openAmendments(contractId: String): Int

    @Query(
        """
        SELECT COALESCE(MAX(version), 0) FROM contract_amendments
        WHERE contractId = :contractId
        """,
    )
    suspend fun lastAmendmentVersion(contractId: String): Int

    // ---- assignments ----------------------------------------------------

    @Upsert
    suspend fun upsert(assignment: AssignmentEntity)

    @Query("SELECT * FROM assignments WHERE id = :id")
    fun observeAssignment(id: String): Flow<AssignmentEntity?>

    /**
     * The packages this organisation is on either side of.
     *
     * Both sides in one query because both appear on the same screen: what a
     * firm handed out and what it took on are the same list to a crew leader
     * who does both.
     */
    @Query(
        """
        SELECT * FROM assignments
        WHERE projectId = :projectId AND (payerOrgId = :orgId OR payeeOrgId = :orgId)
        ORDER BY
            CASE status
                WHEN 'OFFERED' THEN 0
                WHEN 'SUBMITTED' THEN 1
                WHEN 'REJECTED' THEN 2
                WHEN 'IN_PROGRESS' THEN 3
                WHEN 'ACCEPTED' THEN 4
                WHEN 'DRAFT' THEN 5
                ELSE 6
            END,
            updatedAt DESC
        """,
    )
    fun observeAssignments(projectId: String, orgId: String): Flow<List<AssignmentEntity>>

    @Query("SELECT COUNT(*) FROM assignments")
    suspend fun assignmentCount(): Int
}
