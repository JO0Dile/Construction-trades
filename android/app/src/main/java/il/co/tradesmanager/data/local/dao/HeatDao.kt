package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.HeatCheckEntity
import kotlinx.coroutines.flow.Flow

/** Write and read. There is no delete: a hot day with nothing done is the record. */
@Dao
interface HeatDao {

    @Upsert
    suspend fun upsert(check: HeatCheckEntity)

    /**
     * One firm's checks, newest first.
     *
     * The null arm is the one-man band, not everybody -- the same shape as
     * ViolationDao.observeForCompany, for the same reason.
     */
    @Query(
        """
        SELECT * FROM heat_checks
        WHERE (:companyId IS NULL AND companyId IS NULL) OR companyId = :companyId
        ORDER BY checkedAt DESC
        LIMIT :limit
        """,
    )
    fun observeForCompany(companyId: String?, limit: Int = 200): Flow<List<HeatCheckEntity>>
}
