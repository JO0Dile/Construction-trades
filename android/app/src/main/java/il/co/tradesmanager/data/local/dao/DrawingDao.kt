package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.DrawingEntity
import kotlinx.coroutines.flow.Flow

/** Write and read. There is no delete: a superseded drawing is history, not rubbish. */
@Dao
interface DrawingDao {

    @Upsert
    suspend fun upsert(drawing: DrawingEntity)

    /** One job's register, every revision. The rules decide which are current. */
    @Query("SELECT * FROM drawings WHERE projectId = :projectId ORDER BY receivedAt DESC")
    fun observeForProject(projectId: String): Flow<List<DrawingEntity>>

    @Query("SELECT * FROM drawings WHERE projectId = :projectId")
    suspend fun forProject(projectId: String): List<DrawingEntity>

    /**
     * A new revision and the one it supersedes, in one transaction: no moment
     * at which the old one is marked replaced and the new one is not there.
     */
    @Transaction
    suspend fun supersede(previous: DrawingEntity?, current: DrawingEntity) {
        previous?.let { upsert(it) }
        upsert(current)
    }
}
