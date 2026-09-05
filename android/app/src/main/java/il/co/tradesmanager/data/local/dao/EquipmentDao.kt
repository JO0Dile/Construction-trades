package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.EquipmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EquipmentDao {

    @Upsert
    suspend fun upsert(equipment: EquipmentEntity)

    @Delete
    suspend fun delete(equipment: EquipmentEntity)

    @Query("SELECT * FROM equipment ORDER BY serviceDueOn IS NULL, serviceDueOn, name")
    fun observeAll(): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment WHERE assignedProjectId = :projectId ORDER BY name")
    fun observeForProject(projectId: String): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment WHERE id = :id")
    suspend fun equipment(id: String): EquipmentEntity?

    /** Plant due a service inside the window, for the dashboard. */
    @Query(
        """
        SELECT * FROM equipment
        WHERE serviceDueOn IS NOT NULL AND serviceDueOn <= :before
        ORDER BY serviceDueOn
        """,
    )
    fun observeServiceDueBefore(before: Long): Flow<List<EquipmentEntity>>
}
