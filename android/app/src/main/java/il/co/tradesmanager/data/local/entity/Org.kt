package il.co.tradesmanager.data.local.entity

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
 * is read-only, and retention is enforced by a scheduled purge that records
 * its own purge event rather than by editing rows.
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
)
