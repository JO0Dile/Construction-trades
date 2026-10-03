package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.ComplaintEntity
import kotlinx.coroutines.flow.Flow

/** Write and read. There is no delete: a complaint that was made was made. */
@Dao
interface ComplaintDao {

    @Upsert
    suspend fun upsert(complaint: ComplaintEntity)

    @Query("SELECT * FROM complaints WHERE id = :id")
    suspend fun complaint(id: String): ComplaintEntity?

    @Query("SELECT * FROM complaints WHERE projectId = :projectId ORDER BY receivedAt")
    fun observeForProject(projectId: String): Flow<List<ComplaintEntity>>

    @Query("SELECT COUNT(*) FROM complaints WHERE projectId = :projectId")
    suspend fun countForProject(projectId: String): Int

    /** Every row on every job, for the search box. Capped: search wants the recent ones. */
    @Query("SELECT * FROM complaints ORDER BY receivedAt DESC LIMIT 500")
    suspend fun all(): List<ComplaintEntity>
}
