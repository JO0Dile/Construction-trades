package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.EquipmentEntity
import il.co.tradesmanager.data.local.entity.PlantCheckEntity
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

    @Upsert
    suspend fun upsertCheck(check: PlantCheckEntity)

    /**
     * The newest pre-use check of every machine, for the register to say which
     * ones have been checked today.
     *
     * Ties on the same millisecond are broken on id, so one machine never
     * appears twice however two checks happened to land.
     */
    @Query(
        """
        SELECT * FROM plant_checks c
        WHERE c.id = (
            SELECT id FROM plant_checks
            WHERE equipmentId = c.equipmentId
            ORDER BY checkedAt DESC, id DESC
            LIMIT 1
        )
        """,
    )
    fun observeLatestChecks(): Flow<List<PlantCheckEntity>>

    @Query("SELECT * FROM plant_checks WHERE equipmentId = :equipmentId ORDER BY checkedAt DESC LIMIT :limit")
    fun observeChecks(equipmentId: String, limit: Int = 60): Flow<List<PlantCheckEntity>>
}
