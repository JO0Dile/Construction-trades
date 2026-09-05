package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A trench or excavation on a job.
 *
 * [servicesLocatedAt] and what was found are on the excavation itself rather
 * than on an inspection, because locating services is not something that
 * happens again and again — it happens once, before the first spade, and the
 * answer does not change. It is also the only field here whose absence means
 * nobody may dig, rather than nobody may enter.
 *
 * The inspection summary is denormalised the way the scaffold register's is,
 * for the same reason: the list has to answer "may anybody go in" for every
 * trench on the site without opening each one's history.
 */
@Entity(
    tableName = "excavations",
    indices = [Index("projectId")],
)
data class ExcavationEntity(
    @PrimaryKey val id: String,
    /** Site form: EXC-001. */
    val reference: String,
    val projectId: String,
    /** Where it is, in the words used on the radio. */
    val location: String,
    val depthMetres: Double?,
    /** NONE, BATTERED, SHORED or PILED — see core.safety.Excavation. */
    val support: String,
    /** When somebody established what is buried here. */
    val servicesLocatedAt: Long?,
    val servicesLocatedByName: String?,
    /** What they found: drawings from the utility, a CAT scan, trial holes. */
    val servicesNotes: String?,
    /** Denormalised from the last inspection. Written only by the repository. */
    val lastInspectedAt: Long?,
    val lastInspectionPassed: Boolean,
    val lastInspectorName: String?,
    /** Rain, a collapse, vibration, a load at the edge. Voids the inspection. */
    val disturbedAt: Long?,
    val backfilledAt: Long?,
    val notes: String?,
    val createdByName: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One inspection of a trench, kept forever.
 *
 * Inserted and never updated, like the scaffold register's. The difference is
 * how long one is worth: a scaffold's lasts a week, a trench's lasts the day.
 * It rains overnight, the sides dry and crack, a lorry parks near the edge —
 * and the man climbing in at six in the morning is relying on somebody having
 * looked since all of that.
 */
@Entity(
    tableName = "excavation_inspections",
    foreignKeys = [
        ForeignKey(
            entity = ExcavationEntity::class,
            parentColumns = ["id"],
            childColumns = ["excavationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("excavationId"), Index("inspectedAt")],
)
data class ExcavationInspectionEntity(
    @PrimaryKey val id: String,
    val excavationId: String,
    val inspectedAt: Long,
    val inspectorName: String,
    val passed: Boolean,
    /** What was wrong. The point of the row when [passed] is false. */
    val defects: String?,
    val actionTaken: String?,
    /** Signed on the phone by whoever looked. */
    val signature: String?,
    val createdAt: Long,
)
