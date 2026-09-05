package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A scaffold standing on a job, and the state of its inspection.
 *
 * The three timestamps are denormalised from the inspection rows on purpose.
 * The register's whole job is to answer "may this be climbed" for every
 * scaffold on the site at once, and a list that has to open each scaffold's
 * inspection history to answer it is a list that gets scrolled past. The
 * repository is the only thing that writes them, from the inspection it just
 * recorded.
 *
 * [alteredAt] is separate from the inspections rather than being one of them:
 * an alteration is not an inspection, it is the thing that voids one. A
 * scaffold that has been modified is a different scaffold, and the inspection
 * that passed the old one says nothing about the new one.
 */
@Entity(
    tableName = "scaffolds",
    indices = [Index("projectId")],
)
data class ScaffoldEntity(
    @PrimaryKey val id: String,
    /** Site form: SCF-001. */
    val reference: String,
    val projectId: String,
    /** Where it is, in the words somebody would use on the radio. */
    val location: String,
    /** Independent tied, birdcage, mobile tower, and so on. */
    val type: String?,
    val erectedByName: String?,
    val erectedAt: Long?,
    /** Denormalised from the last inspection. Written only by the repository. */
    val lastInspectedAt: Long?,
    val lastInspectionPassed: Boolean,
    val lastInspectorName: String?,
    /** When it was last changed or struck by weather. Voids the inspection. */
    val alteredAt: Long?,
    /** Struck down. Kept as a row: the register is a history, not a stock list. */
    val dismantledAt: Long?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One inspection, kept forever.
 *
 * Append-only in spirit and in the sync policy: the register exists so that
 * somebody can say what was known and when. An inspection that can be edited
 * after an accident is evidence of nothing, which is the same reason the daily
 * log locks when it is signed.
 *
 * A failed inspection is a row like any other. It is the more important kind:
 * the reason the scaffold was standing unusable for two days is in it.
 */
@Entity(
    tableName = "scaffold_inspections",
    foreignKeys = [
        ForeignKey(
            entity = ScaffoldEntity::class,
            parentColumns = ["id"],
            childColumns = ["scaffoldId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("scaffoldId"), Index("inspectedAt")],
)
data class ScaffoldInspectionEntity(
    @PrimaryKey val id: String,
    val scaffoldId: String,
    val inspectedAt: Long,
    val inspectorName: String,
    val passed: Boolean,
    /** What was wrong. The point of the row when [passed] is false. */
    val defects: String?,
    /** What was done about it, or what was stopped until it was. */
    val actionTaken: String?,
    /**
     * Why this inspection happened: WEEKLY, FIRST_USE, AFTER_ALTERATION or
     * AFTER_WEATHER. Regulation asks for all four, and "which one was it"
     * is the question an inspector is asked afterwards.
     */
    val reason: String,
    /** Signed on the phone by the person who inspected it. */
    val signature: String?,
    val createdAt: Long,
)
