package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.PlantExaminationEntity
import kotlinx.coroutines.flow.Flow

/** Write and read. There is no delete: a certificate that was issued was issued. */
@Dao
interface PlantExaminationDao {

    @Upsert
    suspend fun upsert(examination: PlantExaminationEntity)

    /**
     * Every machine's latest examination. Latest by the day examined, then by
     * when it was typed in, then by id, so one machine never appears twice.
     */
    @Query(
        """
        SELECT * FROM plant_examinations e
        WHERE e.id = (
            SELECT id FROM plant_examinations
            WHERE equipmentId = e.equipmentId
            ORDER BY examinedOnDay DESC, recordedAt DESC, id DESC
            LIMIT 1
        )
        """,
    )
    fun observeLatest(): Flow<List<PlantExaminationEntity>>

    @Query("SELECT * FROM plant_examinations WHERE equipmentId = :equipmentId ORDER BY examinedOnDay DESC, recordedAt DESC")
    fun observeFor(equipmentId: String): Flow<List<PlantExaminationEntity>>
}
