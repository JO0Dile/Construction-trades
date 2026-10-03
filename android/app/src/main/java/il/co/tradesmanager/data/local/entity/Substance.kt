package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One hazardous substance kept on a job, from the day it arrived to the day
 * it went. Taken off the site is a date, not a delete. Photographs of its
 * safety data sheet are in the photos table under `Owner.SUBSTANCE_SHEET`.
 * See core.safety.Substances for the rules.
 */
@Entity(
    tableName = "substances",
    indices = [Index("projectId")],
)
data class SubstanceEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    /** HS-001, numbered per job. */
    val reference: String,
    /** What it is called on the tin: "Sika form oil", "propane, 12 kg bottles". */
    val name: String,
    val supplier: String? = null,
    /** Substances.Hazard names joined by commas. */
    val hazards: String,
    /** Where the rest of it is: "the locked cage by the gate", "the paint store, level -1". */
    val keptWhere: String,
    /** As it would be said: "4 bottles", "200 l drum". */
    val quantity: String? = null,
    /** What the sheet says to wear and to do when using it. */
    val precautions: String? = null,
    /** What the sheet says to do when somebody is splashed or breathes it. */
    val firstAid: String? = null,
    /** The date printed on the data sheet on file, as an epoch day. Null: no sheet on file. */
    val sheetOnDay: Long? = null,
    val addedAt: Long,
    val addedByName: String,
    val removedAt: Long? = null,
    val removedByName: String? = null,
)
