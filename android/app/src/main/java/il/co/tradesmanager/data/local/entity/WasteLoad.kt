package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One load of waste leaving a job.
 *
 * The ticket is the point of the row: a weighbridge or gate ticket from the
 * facility that took it. Its number is stored here and its photograph in the
 * photos table under `Owner.WASTE_TICKET`, keyed on this row's id, and either
 * one is enough to count the load as shown. A load with neither is still
 * recorded, because it still left the site.
 *
 * See core.evidence.Waste for the rules.
 */
@Entity(
    tableName = "waste_loads",
    // Every read is one job's loads.
    indices = [Index("projectId")],
)
data class WasteLoadEntity(
    @PrimaryKey val id: String,
    /** Waste belongs to a job. There is no such thing as a load from nowhere. */
    val projectId: String,
    val removedAt: Long,
    /** See core.evidence.Waste.Stream. */
    val stream: String,
    val quantity: Double,
    /** TONNES or CUBIC_METRES — see core.evidence.Waste.Measurement. */
    val unit: String,
    /** See core.evidence.Waste.Destination. */
    val destination: String,
    /** The facility's name: which crusher, which transfer station, which landfill. */
    val facility: String,
    /** Who took it away: the haulier's firm, or the lorry's registration. */
    val hauler: String? = null,
    val ticketNumber: String? = null,
    val notes: String? = null,
    val recordedByAccountId: String? = null,
    /** As it read at the time. */
    val recordedByName: String,
)
