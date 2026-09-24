package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One material sent for approval, and the answer.
 *
 * The decision is written once. A rejected submittal is followed by a row
 * with the same [reference], the next [revision], and [resubmissionOf]
 * pointing back at it, so every go at the same thing stays on the record. A
 * datasheet or a photograph of the sample is in the photos table under
 * `Owner.SUBMITTAL`. See core.work.Submittals for the rules.
 */
@Entity(
    tableName = "submittals",
    indices = [Index("projectId")],
)
data class SubmittalEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    /** MS-001, numbered per job; kept by every revision. */
    val reference: String,
    /** 0 for the first submission, then 1, 2... */
    val revision: Int,
    /** What is proposed: "Porcelain floor tile 60x60, grey, matt". */
    val item: String,
    /** Who makes or sells it, if it was said. */
    val supplier: String? = null,
    /** Where it goes, or the clause of the specification it answers. */
    val location: String? = null,
    /** Who it went to: "the architect", "the supervisor", a name. */
    val submittedTo: String,
    val submittedAt: Long,
    /** The day the answer is needed by, to order in time. */
    val neededBy: Long? = null,
    val submittedByName: String,
    /** The rejected revision this one follows. */
    val resubmissionOf: String? = null,
    /** A Submittals.Decision name, once there is one. */
    val decision: String? = null,
    /** Who decided, which is rarely who typed it in. */
    val reviewerName: String? = null,
    val notes: String? = null,
    val decidedAt: Long? = null,
    val decisionRecordedByName: String? = null,
)
