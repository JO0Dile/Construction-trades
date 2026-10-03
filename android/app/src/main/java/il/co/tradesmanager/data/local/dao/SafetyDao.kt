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

    /**
     * The register for one company, or for a sole trader when [companyId] is
     * null, with the reports filed before incidents carried a company
     * (marked '') in every register, as they always were.
     */
    @Query(
        """
        SELECT * FROM incidents
        WHERE (:companyId IS NULL AND companyId IS NULL) OR companyId = :companyId OR companyId = ''
        ORDER BY occurredAt DESC LIMIT 200
        """,
    )
    fun observeIncidents(companyId: String?): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE id = :id")
    suspend fun incident(id: String): IncidentEntity?

    @Query("SELECT * FROM incidents WHERE id = :id")
    fun observeIncident(id: String): Flow<IncidentEntity?>
}
