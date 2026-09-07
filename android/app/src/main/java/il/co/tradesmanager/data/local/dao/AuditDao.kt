package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import il.co.tradesmanager.data.local.entity.AuditLogEntity
import il.co.tradesmanager.data.local.entity.TeamMemberEntity
import kotlinx.coroutines.flow.Flow

/**
 * Insert and read only. There is deliberately no update, and the single delete
 * is the retention purge, which the caller must itself record as a PURGE event.
 */
@Dao
interface AuditDao {

    @Insert
    suspend fun insert(entry: AuditLogEntity)

    @Query("SELECT * FROM audit_log ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log WHERE entityType = :type AND entityId = :id ORDER BY occurredAt DESC")
    fun observeFor(type: String, id: String): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log WHERE occurredAt >= :since ORDER BY occurredAt")
    suspend fun exportSince(since: Long): List<AuditLogEntity>

    /**
     * The end of the chain: what a new entry has to follow.
     *
     * Ordered by sequence rather than time. A clock that goes backwards — a
     * phone crossing a timezone, a user correcting the date — would otherwise
     * hand the next entry the wrong predecessor and break a trail nobody had
     * touched.
     */
    @Query("SELECT * FROM audit_log ORDER BY sequence DESC LIMIT 1")
    suspend fun lastEntry(): AuditLogEntity?

    /**
     * The oldest surviving entry, which after a purge is the edge of the trail.
     *
     * Its `previousHash` points at a row that no longer exists. That is the
     * one break verification cannot tell from tampering, so the purge records
     * this row's identity in its own entry: the gap stops being unexplained.
     */
    @Query("SELECT * FROM audit_log ORDER BY sequence ASC LIMIT 1")
    suspend fun oldestEntry(): AuditLogEntity?

    /**
     * The whole trail, oldest first, for verification.
     *
     * Bounded: a phone two years into a job holds a lot of these, and reading
     * every one to redraw a screen is how a settings page becomes a freeze.
     * Verification therefore checks the most recent [limit] entries, which is
     * a window and is described as one on screen.
     */
    @Query("SELECT * FROM audit_log ORDER BY sequence DESC, occurredAt DESC LIMIT :limit")
    suspend fun newestFirst(limit: Int): List<AuditLogEntity>

    @Query("DELETE FROM audit_log WHERE occurredAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long): Int

    @androidx.room.Upsert
    suspend fun upsertMember(member: TeamMemberEntity)

    @Query("SELECT * FROM team_members WHERE isActive = 1 ORDER BY displayName")
    fun observeMembers(): Flow<List<TeamMemberEntity>>
}
