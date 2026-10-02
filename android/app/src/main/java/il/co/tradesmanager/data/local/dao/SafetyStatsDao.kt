package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query

/** A finished shift's two ends, for counting the hours worked. */
data class ShiftSpan(val projectId: String?, val checkInAt: Long, val checkOutAt: Long?)

/** How many of something one job recorded; [kind] is the incident severity as stored, where there is one. */
data class JobTally(val projectId: String?, val kind: String?, val total: Int)

/** How many of something one job recorded. */
data class JobCount(val projectId: String?, val total: Int)

/**
 * The company's safety statistics, counted over a window across the jobs
 * asked about. Read only; every figure comes from a register that already
 * exists. [to] is exclusive throughout.
 */
@Dao
interface SafetyStatsDao {

    /**
     * Finished shifts that end after the window opens and start before it
     * closes. Those that only touch it are trimmed by the caller.
     */
    @Query(
        """
        SELECT projectId, checkInAt, checkOutAt FROM time_entries
        WHERE projectId IN (:projectIds) AND checkOutAt IS NOT NULL
          AND checkInAt < :to AND checkOutAt > :from
        """,
    )
    suspend fun shifts(projectIds: List<String>, from: Long, to: Long): List<ShiftSpan>

    @Query(
        """
        SELECT projectId, severity AS kind, COUNT(*) AS total FROM incidents
        WHERE projectId IN (:projectIds) AND occurredAt >= :from AND occurredAt < :to
        GROUP BY projectId, severity
        """,
    )
    suspend fun incidents(projectIds: List<String>, from: Long, to: Long): List<JobTally>

    /** Not cancelled since: a violation withdrawn was not one. */
    @Query(
        """
        SELECT projectId, COUNT(*) AS total FROM violations
        WHERE projectId IN (:projectIds) AND recordedAt >= :from AND recordedAt < :to AND cancelledAt IS NULL
        GROUP BY projectId
        """,
    )
    suspend fun violations(projectIds: List<String>, from: Long, to: Long): List<JobCount>

    @Query(
        """
        SELECT projectId, COUNT(*) AS total FROM toolbox_talks
        WHERE projectId IN (:projectIds) AND heldAt >= :from AND heldAt < :to
        GROUP BY projectId
        """,
    )
    suspend fun talks(projectIds: List<String>, from: Long, to: Long): List<JobCount>

    /** The severity names are core.safety.Incidents.Severity's, as stored. */
    @Query("SELECT MAX(occurredAt) FROM incidents WHERE projectId IN (:projectIds) AND severity IN ('SERIOUS', 'FATAL')")
    suspend fun lastSerious(projectIds: List<String>): Long?
}
