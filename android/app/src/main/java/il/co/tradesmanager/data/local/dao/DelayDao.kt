package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.DelayEventEntity
import kotlinx.coroutines.flow.Flow

/** Write and read. There is no delete: a day the work stopped is a day the work stopped. */
@Dao
interface DelayDao {

    @Upsert
    suspend fun upsert(event: DelayEventEntity)

    @Query("SELECT * FROM delay_events WHERE id = :id")
    suspend fun event(id: String): DelayEventEntity?

    @Query("SELECT * FROM delay_events WHERE projectId = :projectId ORDER BY startedOnDay DESC")
    fun observeForProject(projectId: String): Flow<List<DelayEventEntity>>

    @Query("SELECT COUNT(*) FROM delay_events WHERE projectId = :projectId")
    suspend fun countForProject(projectId: String): Int

    /** Every row on every job, for the search box. Capped: search wants the recent ones. */
    @Query("SELECT * FROM delay_events ORDER BY recordedAt DESC LIMIT 500")
    suspend fun all(): List<DelayEventEntity>
}
