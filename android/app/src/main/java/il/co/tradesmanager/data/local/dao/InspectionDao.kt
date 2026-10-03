package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.InspectionEntity
import kotlinx.coroutines.flow.Flow

/** Write and read. There is no delete: an inspection that was asked for was asked for. */
@Dao
interface InspectionDao {

    @Upsert
    suspend fun upsert(inspection: InspectionEntity)

    @Query("SELECT * FROM inspections WHERE id = :id")
    suspend fun inspection(id: String): InspectionEntity?

    @Query("SELECT * FROM inspections WHERE projectId = :projectId ORDER BY requestedAt")
    fun observeForProject(projectId: String): Flow<List<InspectionEntity>>

    @Query("SELECT COUNT(*) FROM inspections WHERE projectId = :projectId")
    suspend fun countForProject(projectId: String): Int

    /** Whether [failedId] has been asked for again already. */
    @Query("SELECT COUNT(*) FROM inspections WHERE reinspectionOf = :failedId")
    suspend fun reinspectionCount(failedId: String): Int

    /** Every row on every job, for the search box. Capped: search wants the recent ones. */
    @Query("SELECT * FROM inspections ORDER BY requestedAt DESC LIMIT 500")
    suspend fun all(): List<InspectionEntity>
}
