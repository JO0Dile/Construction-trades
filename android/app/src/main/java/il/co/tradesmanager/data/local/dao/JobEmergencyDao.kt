package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.JobEmergencyEntity
import kotlinx.coroutines.flow.Flow

/** One sheet per job, replaced in place. */
@Dao
interface JobEmergencyDao {

    @Upsert
    suspend fun upsert(sheet: JobEmergencyEntity)

    @Query("SELECT * FROM job_emergency WHERE projectId = :projectId")
    suspend fun sheet(projectId: String): JobEmergencyEntity?

    @Query("SELECT * FROM job_emergency WHERE projectId = :projectId")
    fun observe(projectId: String): Flow<JobEmergencyEntity?>
}
