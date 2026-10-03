package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One pre-use walk-round of one machine.
 *
 * Kept whatever it found. A check that found a defect is the record that the
 * machine was stopped, and a check that found nothing on the morning of an
 * accident is the record somebody will ask for. Nothing edits or deletes these.
 *
 * See core.safety.PreUse for the rules.
 */
@Entity(
    tableName = "plant_checks",
    // Every read is one machine's checks, or the latest check of each.
    indices = [Index("equipmentId")],
)
data class PlantCheckEntity(
    @PrimaryKey val id: String,
    val equipmentId: String,
    val checkedAt: Long,
    /** FIT or UNFIT — see core.safety.PreUse.Outcome. */
    val outcome: String,
    /** Item name to answer name, for every item: see core.safety.PreUse. */
    val answers: Map<String, String>,
    /** What was wrong. Required when anything was, and null when nothing was. */
    val defectNote: String? = null,
    val checkedByAccountId: String? = null,
    /** As it read at the time. */
    val checkedByName: String,
)
