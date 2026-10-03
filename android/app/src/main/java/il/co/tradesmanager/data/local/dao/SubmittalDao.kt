package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.SubmittalEntity
import kotlinx.coroutines.flow.Flow

/** Write and read. There is no delete: what was submitted was submitted. */
@Dao
interface SubmittalDao {

    @Upsert
    suspend fun upsert(submittal: SubmittalEntity)

    @Query("SELECT * FROM submittals WHERE id = :id")
    suspend fun submittal(id: String): SubmittalEntity?

    @Query("SELECT * FROM submittals WHERE projectId = :projectId ORDER BY submittedAt")
    fun observeForProject(projectId: String): Flow<List<SubmittalEntity>>

    /** First submissions only: a revision keeps its number, so it does not take a new one. */
    @Query("SELECT COUNT(*) FROM submittals WHERE projectId = :projectId AND resubmissionOf IS NULL")
    suspend fun countFirstSubmissions(projectId: String): Int

    @Query("SELECT COUNT(*) FROM submittals WHERE resubmissionOf = :rejectedId")
    suspend fun resubmissionCount(rejectedId: String): Int

    /** Every row on every job, for the search box. Capped: search wants the recent ones. */
    @Query("SELECT * FROM submittals ORDER BY submittedAt DESC LIMIT 500")
    suspend fun all(): List<SubmittalEntity>
}
