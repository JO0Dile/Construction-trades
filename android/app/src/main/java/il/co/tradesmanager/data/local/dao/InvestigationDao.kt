package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.IncidentActionEntity
import il.co.tradesmanager.data.local.entity.IncidentInvestigationEntity
import kotlinx.coroutines.flow.Flow

/** Write and read. There is no delete: what an investigation found stays found, and an action is closed, not removed. */
@Dao
interface InvestigationDao {

    @Upsert
    suspend fun upsert(investigation: IncidentInvestigationEntity)

    @Query("SELECT * FROM incident_investigations WHERE incidentId = :incidentId")
    suspend fun investigation(incidentId: String): IncidentInvestigationEntity?

    @Query("SELECT * FROM incident_investigations WHERE incidentId = :incidentId")
    fun observe(incidentId: String): Flow<IncidentInvestigationEntity?>

    /** Every investigation, for the state shown beside each report in the register. */
    @Query("SELECT * FROM incident_investigations ORDER BY startedAt DESC LIMIT 1000")
    fun observeAll(): Flow<List<IncidentInvestigationEntity>>

    @Upsert
    suspend fun upsertAction(action: IncidentActionEntity)

    @Query("SELECT * FROM incident_actions WHERE id = :id")
    suspend fun action(id: String): IncidentActionEntity?

    @Query("SELECT * FROM incident_actions WHERE incidentId = :incidentId ORDER BY number")
    fun observeActions(incidentId: String): Flow<List<IncidentActionEntity>>

    @Query("SELECT * FROM incident_actions WHERE incidentId = :incidentId ORDER BY number")
    suspend fun actions(incidentId: String): List<IncidentActionEntity>

    @Query("SELECT COUNT(*) FROM incident_actions WHERE incidentId = :incidentId")
    suspend fun countActions(incidentId: String): Int

    /**
     * Serious incidents and deaths on a job whose investigation is not
     * closed, started or not. The severity names are
     * core.safety.Incidents.Severity's, as stored.
     */
    @Query(
        """
        SELECT COUNT(*) FROM incidents i
        WHERE i.projectId = :projectId AND i.severity IN ('SERIOUS', 'FATAL')
          AND NOT EXISTS (
            SELECT 1 FROM incident_investigations v WHERE v.incidentId = i.id AND v.closedAt IS NOT NULL
          )
        """,
    )
    fun observeOutstandingForProject(projectId: String): Flow<Int>

    /** Actions on a job's incidents still open with their date gone by: due before [today], an epoch day. */
    @Query(
        """
        SELECT COUNT(*) FROM incident_actions a JOIN incidents i ON i.id = a.incidentId
        WHERE i.projectId = :projectId AND a.closedAt IS NULL AND a.dueOnDay IS NOT NULL AND a.dueOnDay < :today
        """,
    )
    fun observeOverdueActionsForProject(projectId: String, today: Long): Flow<Int>
}
