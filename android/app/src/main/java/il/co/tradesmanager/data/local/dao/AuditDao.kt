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

    @Query("DELETE FROM audit_log WHERE occurredAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long): Int

    @androidx.room.Upsert
    suspend fun upsertMember(member: TeamMemberEntity)

    @Query("SELECT * FROM team_members WHERE isActive = 1 ORDER BY displayName")
    fun observeMembers(): Flow<List<TeamMemberEntity>>
}
