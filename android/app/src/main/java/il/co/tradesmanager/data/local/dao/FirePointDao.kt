package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.FirePointCheckEntity
import il.co.tradesmanager.data.local.entity.FirePointEntity
import kotlinx.coroutines.flow.Flow

/** Write and read. There is no delete: neither a fire point nor a look at one is taken back. */
@Dao
interface FirePointDao {

    @Upsert
    suspend fun upsert(point: FirePointEntity)

    @Query("SELECT * FROM fire_points WHERE id = :id")
    suspend fun point(id: String): FirePointEntity?

    @Query("SELECT * FROM fire_points WHERE projectId = :projectId ORDER BY addedAt")
    fun observeForProject(projectId: String): Flow<List<FirePointEntity>>

    @Query("SELECT COUNT(*) FROM fire_points WHERE projectId = :projectId")
    suspend fun countForProject(projectId: String): Int

    /** Every row on every job, for the search box. Capped: search wants the recent ones. */
    @Query("SELECT * FROM fire_points ORDER BY addedAt DESC LIMIT 500")
    suspend fun all(): List<FirePointEntity>

    @Insert
    suspend fun insertCheck(check: FirePointCheckEntity)

    /** A job's looks, newest first: the first for each point is its current one. */
    @Query("SELECT * FROM fire_point_checks WHERE projectId = :projectId ORDER BY checkedAt DESC")
    fun observeChecksForProject(projectId: String): Flow<List<FirePointCheckEntity>>
}
