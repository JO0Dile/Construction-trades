package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query

/**
 * Counts over a window for the weekly safety report. Read only; every
 * figure comes from a register that already exists.
 */
@Dao
interface SafetyReportDao {

    @Query(
        """
        SELECT COUNT(DISTINCT COALESCE(workerId, workerName)) FROM time_entries
        WHERE projectId = :projectId AND checkInAt <= :to
          AND (checkOutAt IS NULL OR checkOutAt >= :from)
        """,
    )
    suspend fun peopleOnSite(projectId: String, from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM site_visits WHERE projectId = :projectId AND arrivedAt >= :from AND arrivedAt <= :to")
    suspend fun visitors(projectId: String, from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM toolbox_talks WHERE projectId = :projectId AND heldAt >= :from AND heldAt <= :to")
    suspend fun talksHeld(projectId: String, from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM permits WHERE projectId = :projectId AND issuedAt >= :from AND issuedAt <= :to")
    suspend fun permitsIssued(projectId: String, from: Long, to: Long): Int

    /** The severity name is core.safety.Incidents.Severity's, as stored. */
    @Query(
        """
        SELECT COUNT(*) FROM incidents
        WHERE projectId = :projectId AND occurredAt >= :from AND occurredAt <= :to AND severity = 'NEAR_MISS'
        """,
    )
    suspend fun nearMisses(projectId: String, from: Long, to: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM incidents
        WHERE projectId = :projectId AND occurredAt >= :from AND occurredAt <= :to AND severity != 'NEAR_MISS'
        """,
    )
    suspend fun incidents(projectId: String, from: Long, to: Long): Int

    /** Recorded that week and not cancelled since: a violation withdrawn was not one. */
    @Query(
        """
        SELECT COUNT(*) FROM violations
        WHERE projectId = :projectId AND recordedAt >= :from AND recordedAt <= :to AND cancelledAt IS NULL
        """,
    )
    suspend fun violations(projectId: String, from: Long, to: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM inspections
        WHERE projectId = :projectId AND decidedAt >= :from AND decidedAt <= :to
          AND result IN ('PASSED', 'PASSED_WITH_COMMENTS')
        """,
    )
    suspend fun inspectionsPassed(projectId: String, from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM inspections WHERE projectId = :projectId AND decidedAt >= :from AND decidedAt <= :to AND result = 'FAILED'")
    suspend fun inspectionsFailed(projectId: String, from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM fire_point_checks WHERE projectId = :projectId AND ok = 0 AND checkedAt >= :from AND checkedAt <= :to")
    suspend fun fireFaults(projectId: String, from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM complaints WHERE projectId = :projectId AND receivedAt >= :from AND receivedAt <= :to")
    suspend fun complaintsReceived(projectId: String, from: Long, to: Long): Int
}
