package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.RiskAssessmentEntity
import kotlinx.coroutines.flow.Flow

/** Write and read. A finished risk is closed, not deleted: the assessment is a record of what was thought. */
@Dao
interface RiskDao {

    @Upsert
    suspend fun upsert(risk: RiskAssessmentEntity)

    @Query("SELECT * FROM risk_assessments WHERE id = :id")
    suspend fun risk(id: String): RiskAssessmentEntity?

    @Query("SELECT * FROM risk_assessments WHERE projectId = :projectId ORDER BY createdAt")
    fun observeForProject(projectId: String): Flow<List<RiskAssessmentEntity>>

    @Query("SELECT COUNT(*) FROM risk_assessments WHERE projectId = :projectId")
    suspend fun countForProject(projectId: String): Int

    /** Every row on every job, for the search box. Capped: search wants the recent ones. */
    @Query("SELECT * FROM risk_assessments ORDER BY createdAt DESC LIMIT 500")
    suspend fun all(): List<RiskAssessmentEntity>
}
