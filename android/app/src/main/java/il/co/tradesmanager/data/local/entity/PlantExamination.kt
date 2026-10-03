package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One periodic examination of one machine: the certificate, as recorded.
 *
 * A history rather than columns on the machine, because each certificate is
 * a record somebody may ask to see, and the one before it matters when this
 * one failed. Written once. Days are the site's calendar days
 * (LocalDate.toEpochDay). A photograph of the certificate is in the photos
 * table under `Owner.PLANT_EXAMINATION`. See core.safety.Examinations.
 */
@Entity(
    tableName = "plant_examinations",
    indices = [Index("equipmentId")],
)
data class PlantExaminationEntity(
    @PrimaryKey val id: String,
    val equipmentId: String,
    val examinedOnDay: Long,
    /** The qualified examiner who signed it. */
    val examinerName: String,
    val certificateNumber: String? = null,
    /** An Examinations.Result name. */
    val result: String,
    val nextDueDay: Long? = null,
    /** The conditions, or why it failed. */
    val notes: String? = null,
    val recordedByName: String,
    val recordedAt: Long,
)
