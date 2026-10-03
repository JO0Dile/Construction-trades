package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One non-conformance report: what does not meet which requirement, what is
 * decided about it, and how the result was checked. Decided once and closed
 * once; never deleted. The photographs are in the photos table under
 * `Owner.NON_CONFORMANCE`. See core.evidence.NonConformances.
 */
@Entity(
    tableName = "non_conformances",
    indices = [Index("projectId")],
)
data class NonConformanceEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    /** NCR-001, numbered per job. */
    val reference: String,
    /** Where it is: "L3 slab, grid C-4", "flat 12, bathroom". */
    val element: String,
    /** What it fails: "drawing S-102 rev C", "spec 02.03, cover 40 mm", "SI 466". */
    val requirement: String,
    /** What was found. */
    val finding: String,
    /** A NonConformances.FoundBy name. */
    val foundBy: String,
    val raisedAt: Long,
    val raisedByName: String,
    /** A NonConformances.Disposition name, once decided. */
    val disposition: String? = null,
    /** For one kept as it is: who agreed to keep it. */
    val acceptedBy: String? = null,
    /** What will be done, for anything that changes the work. */
    val correction: String? = null,
    val dueOnDay: Long? = null,
    val decidedAt: Long? = null,
    val decidedByName: String? = null,
    /** How the result was checked. */
    val verification: String? = null,
    val closedAt: Long? = null,
    val closedByName: String? = null,
)
