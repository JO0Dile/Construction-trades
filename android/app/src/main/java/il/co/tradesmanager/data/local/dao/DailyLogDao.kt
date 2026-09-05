package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.DailyLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * The daily log, and the counting that fills it in.
 *
 * Every count below asks the same question of a different table: what happened
 * on this job between these two moments. None of it is stored on the log — it
 * is read back each time from the rows that recorded it, so a log cannot drift
 * away from what actually happened, and correcting a mistyped delivery
 * corrects the log too.
 */
@Dao
interface DailyLogDao {

    @Upsert
    suspend fun upsert(log: DailyLogEntity)

    @Query("SELECT * FROM daily_logs WHERE id = :id")
    fun observe(id: String): Flow<DailyLogEntity?>

    @Query("SELECT * FROM daily_logs WHERE id = :id")
    suspend fun log(id: String): DailyLogEntity?

    @Query("SELECT * FROM daily_logs WHERE projectId = :projectId AND logDate = :logDate")
    suspend fun forDay(projectId: String, logDate: Long): DailyLogEntity?

    @Query(
        """
        SELECT * FROM daily_logs
        WHERE projectId = :projectId
        ORDER BY logDate DESC
        LIMIT 400
        """,
    )
    fun observeForProject(projectId: String): Flow<List<DailyLogEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM project_tasks
        WHERE projectId = :projectId AND isDone = 1
          AND doneAt >= :from AND doneAt <= :to
        """,
    )
    suspend fun tasksCompleted(projectId: String, from: Long, to: Long): Int

    /**
     * Stock that moved onto this job. A delivery booked against a purchase
     * order writes one of these, so counting movements counts deliveries
     * without the log needing to know what a purchase order is.
     */
    @Query(
        """
        SELECT COUNT(*) FROM stock_movements
        WHERE projectId = :projectId AND occurredAt >= :from AND occurredAt <= :to
        """,
    )
    suspend fun deliveries(projectId: String, from: Long, to: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM permits
        WHERE projectId = :projectId AND issuedAt IS NOT NULL
          AND issuedAt >= :from AND issuedAt <= :to
        """,
    )
    suspend fun permitsIssued(projectId: String, from: Long, to: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM toolbox_talks
        WHERE projectId = :projectId AND heldAt >= :from AND heldAt <= :to
        """,
    )
    suspend fun talksHeld(projectId: String, from: Long, to: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM snags
        WHERE projectId = :projectId AND raisedAt >= :from AND raisedAt <= :to
        """,
    )
    suspend fun snagsRaised(projectId: String, from: Long, to: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM incidents
        WHERE projectId = :projectId AND occurredAt >= :from AND occurredAt <= :to
        """,
    )
    suspend fun incidents(projectId: String, from: Long, to: Long): Int
}
