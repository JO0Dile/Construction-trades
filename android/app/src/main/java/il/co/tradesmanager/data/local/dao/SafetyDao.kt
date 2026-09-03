package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.ChecklistRunEntity
import il.co.tradesmanager.data.local.entity.ChecklistRunItemEntity
import il.co.tradesmanager.data.local.entity.IncidentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SafetyDao {

    @Upsert
    suspend fun upsertRun(run: ChecklistRunEntity)

    @Query("SELECT * FROM checklist_runs WHERE id = :runId")
    fun observeRun(runId: String): Flow<ChecklistRunEntity?>

    @Query("SELECT * FROM checklist_runs WHERE id = :runId")
    suspend fun run(runId: String): ChecklistRunEntity?

    @Query(
        """
        SELECT * FROM checklist_runs
        WHERE (:projectId IS NULL OR projectId = :projectId)
        ORDER BY startedAt DESC LIMIT 100
        """,
    )
    fun observeRuns(projectId: String?): Flow<List<ChecklistRunEntity>>

    @Query("SELECT * FROM checklist_runs WHERE templateId = :templateId ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestRunFor(templateId: String): ChecklistRunEntity?

    @Upsert
    suspend fun upsertRunItem(item: ChecklistRunItemEntity)

    @Query("SELECT * FROM checklist_run_items WHERE runId = :runId")
    fun observeRunItems(runId: String): Flow<List<ChecklistRunItemEntity>>

    @Query("SELECT * FROM checklist_run_items WHERE runId = :runId")
    suspend fun runItems(runId: String): List<ChecklistRunItemEntity>

    @Upsert
    suspend fun upsertIncident(incident: IncidentEntity)

    @Query("SELECT * FROM incidents ORDER BY occurredAt DESC LIMIT 200")
    fun observeIncidents(): Flow<List<IncidentEntity>>
}
