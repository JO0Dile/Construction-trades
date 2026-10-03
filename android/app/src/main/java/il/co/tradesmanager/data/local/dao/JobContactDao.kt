package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.JobContactEntity
import kotlinx.coroutines.flow.Flow

/** Write and read. There is no delete: who was on the job was on it. */
@Dao
interface JobContactDao {

    @Upsert
    suspend fun upsert(contact: JobContactEntity)

    @Query("SELECT * FROM job_contacts WHERE id = :id")
    suspend fun contact(id: String): JobContactEntity?

    @Query("SELECT * FROM job_contacts WHERE projectId = :projectId ORDER BY addedAt")
    fun observeForProject(projectId: String): Flow<List<JobContactEntity>>

    /** Every entry on every job, for the search box. Capped: search wants the recent ones. */
    @Query("SELECT * FROM job_contacts ORDER BY updatedAt DESC LIMIT 500")
    suspend fun all(): List<JobContactEntity>
}
