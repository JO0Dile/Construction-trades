package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * What was found out after one incident: one per incident, keyed by it.
 *
 * No job column: an incident filed on no job can be placed on one later, and
 * a copy of its job here would then say the wrong one. Everything per job is
 * reached through the incident. See core.safety.Investigations.
 */
@Entity(tableName = "incident_investigations")
data class IncidentInvestigationEntity(
    @PrimaryKey val incidentId: String,
    /** What directly caused it: "the board under the ladder slid". */
    val immediateCause: String? = null,
    /** Investigations.Cause names, comma separated. */
    val causes: String = "",
    /** What was found, and what anybody else on another job should know. */
    val findings: String? = null,
    val startedAt: Long,
    val startedByName: String,
    val updatedAt: Long,
    val closedAt: Long? = null,
    val closedByName: String? = null,
)

/**
 * Something to be done because of an incident, numbered under it, with
 * somebody to do it and a date, open until it is closed with what was done.
 * Never deleted: an action dropped is a lesson dropped.
 */
@Entity(
    tableName = "incident_actions",
    indices = [Index("incidentId")],
)
data class IncidentActionEntity(
    @PrimaryKey val id: String,
    val incidentId: String,
    /** 1, 2, 3 under the incident. */
    val number: Int,
    val text: String,
    val ownerName: String? = null,
    val dueOnDay: Long? = null,
    val raisedAt: Long,
    val raisedByName: String,
    val closedAt: Long? = null,
    val closedByName: String? = null,
    val closingNote: String? = null,
)
