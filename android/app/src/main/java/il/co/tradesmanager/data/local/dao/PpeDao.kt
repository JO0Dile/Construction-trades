package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.PpeIssueEntity
import kotlinx.coroutines.flow.Flow

/** Write and read. There is no delete: handing something back is a date, not a removal. */
@Dao
interface PpeDao {

    @Upsert
    suspend fun upsert(issue: PpeIssueEntity)

    @Query("SELECT * FROM ppe_issues WHERE id = :id")
    suspend fun issue(id: String): PpeIssueEntity?

    /**
     * One firm's issues, newest first.
     *
     * The null arm is the one-man band, not everybody -- the same shape as
     * HeatDao.observeForCompany, for the same reason.
     */
    @Query(
        """
        SELECT * FROM ppe_issues
        WHERE (:companyId IS NULL AND companyId IS NULL) OR companyId = :companyId
        ORDER BY issuedAt DESC
        """,
    )
    fun observeForCompany(companyId: String?): Flow<List<PpeIssueEntity>>

    /** What one person is holding now, newest first. For their profile. */
    @Query("SELECT * FROM ppe_issues WHERE accountId = :accountId AND handedBackAt IS NULL ORDER BY issuedAt DESC")
    fun observeHeldBy(accountId: String): Flow<List<PpeIssueEntity>>
}
