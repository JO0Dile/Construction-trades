package il.co.tradesmanager.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "team_members")
data class TeamMemberEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    /** ADMIN, SUPERVISOR, WORKER or VIEWER. */
    val role: String,
    val phone: String? = null,
    val isActive: Boolean = true,
)

/**
 * Append-only audit trail.
 *
 * There is no update or delete DAO method for this table on purpose: a
 * government tender asks for a log that the app itself cannot rewrite. Export
 * is read-only, and the only deletion is the retention purge, which records
 * its own purge event rather than editing rows.
 *
 * The purge does not run on a schedule and nothing is deleted by default. How
 * long records must be held is a legal question the operator answers; see
 * `core.security.Retention` for why the app declines to answer it.
 */
@Entity(
    tableName = "audit_log",
    indices = [Index("occurredAt"), Index("entityType", "entityId")],
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    /** CREATE, UPDATE, DELETE, STOCK_CHANGE, SIGN_OFF, SEED, EXPORT, PURGE. */
    val action: String,
    val actorId: String?,
    val actorName: String,
    val summary: String,
    val payloadJson: String? = null,
    val occurredAt: Long,
    /**
     * Position in the trail, from one, never reused.
     *
     * Timestamps do not order a log: two entries can share a millisecond, and
     * a clock can go backwards. A gap here says an entry was removed, which is
     * the thing a timestamp ordering would quietly hide.
     *
     * Zero on rows written before the chain existed, which is what marks them
     * as unverifiable rather than tampered with.
     */
    @ColumnInfo(defaultValue = "0")
    val sequence: Long = 0,
    /** The [hash] of the entry before this one. Empty on unchained rows. */
    @ColumnInfo(defaultValue = "''")
    val previousHash: String = "",
    /**
     * SHA-256 over [previousHash] and every field above, so that altering any
     * of them shows. See `core.security.AuditChain`. Empty on rows written
     * before this app version, which cannot be checked and say so.
     */
    @ColumnInfo(defaultValue = "''")
    val hash: String = "",
)
