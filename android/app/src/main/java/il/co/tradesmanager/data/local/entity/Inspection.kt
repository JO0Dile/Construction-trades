package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One request for somebody to inspect work before it is covered up, and what
 * they found.
 *
 * The result is written once. A failure is followed by a new request whose
 * [reinspectionOf] points back at it, so both stay on the record. A photograph
 * of the signed form is in the photos table under `Owner.INSPECTION`. See
 * core.evidence.Inspections for the rules.
 */
@Entity(
    tableName = "inspections",
    indices = [Index("projectId")],
)
data class InspectionEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    /** IR-001, numbered per job in the order asked. */
    val reference: String,
    /** An Inspections.Kind name. */
    val kind: String,
    /** What is to be seen, and where: "Level 3 slab, grids A-D, bottom steel". */
    val element: String,
    /** Who was asked: "the structural engineer", "the supervisor", a name. */
    val requestedOf: String,
    val requestedAt: Long,
    /** The day the site wants them, if it said. */
    val wantedOn: Long? = null,
    val requestedByName: String,
    /** The failed request this one inspects again, if it is a re-inspection. */
    val reinspectionOf: String? = null,
    /** An Inspections.Result name, once there is one. */
    val result: String? = null,
    /** Who actually inspected, which is rarely who typed it in. */
    val inspectorName: String? = null,
    val comments: String? = null,
    val decidedAt: Long? = null,
    val resultRecordedByName: String? = null,
    /** The pour this passed inspection cleared, when it cleared one. */
    val clearedPourId: String? = null,
)
