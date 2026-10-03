package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One extinguisher, fire blanket or hose reel on a job. Taken away is a
 * date, not a delete. Its monthly looks are [FirePointCheckEntity] rows.
 * See core.safety.FirePoints for the rules.
 */
@Entity(
    tableName = "fire_points",
    indices = [Index("projectId")],
)
data class FirePointEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    /** FP-001, numbered per job. */
    val reference: String,
    /** A FirePoints.Kind name. */
    val kind: String,
    /** Where it hangs: "by the stair, level 2", "outside the site office". */
    val location: String,
    /** The number on its tag or body, if it has one. */
    val tagNumber: String? = null,
    /** The next service date on its label, as an epoch day. */
    val serviceDueOnDay: Long? = null,
    val addedAt: Long,
    val addedByName: String,
    val removedAt: Long? = null,
    val removedByName: String? = null,
)

/**
 * One look at one fire point: who, when, and whether it was fine. Written
 * once and never changed; a fault is put right by a later look that finds it
 * fine, not by editing this one.
 */
@Entity(
    tableName = "fire_point_checks",
    indices = [Index("firePointId"), Index("projectId")],
)
data class FirePointCheckEntity(
    @PrimaryKey val id: String,
    val firePointId: String,
    /** Copied from the point, so a job's looks are read without a join. */
    val projectId: String,
    val checkedAt: Long,
    val ok: Boolean,
    /** What was wrong, or anything worth saying when it was fine. */
    val note: String? = null,
    val checkedByName: String,
)
