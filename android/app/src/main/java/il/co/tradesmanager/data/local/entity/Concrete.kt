package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One pour: an element being cast, and the trucks that fill it.
 *
 * Shaped like a purchase order and its lines, for the same reason — a pour is
 * one intention filled by several deliveries, and the interesting questions
 * ("did we get the volume we ordered", "did any of it go in late") are about
 * the set rather than any one truck.
 *
 * [temperatureCelsius] is recorded on the pour rather than each ticket because
 * it is a fact about the afternoon, and because it shortens the working life
 * of every load in it — see `core.evidence.ConcretePour`.
 */
@Entity(
    tableName = "concrete_pours",
    indices = [Index("projectId"), Index("startedAt")],
)
data class ConcretePourEntity(
    @PrimaryKey val id: String,
    /** Site form: POUR-001. */
    val reference: String,
    val projectId: String,
    /** What is being cast: foundation, column, level 3 slab. */
    val element: String,
    /** The mix the engineer specified, as written on the ticket: B-30, and so on. */
    val mixDesign: String?,
    /** Cubic metres ordered. What arrived is the sum of the tickets. */
    val orderedVolume: Double?,
    val supplierName: String?,
    val temperatureCelsius: Double?,
    val startedAt: Long,
    val completedAt: Long?,
    val notes: String?,
    val recordedByName: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One truck, one ticket (תעודת משלוח).
 *
 * [dispatchedAt] is the time on the supplier's ticket, not the time somebody
 * noticed the truck. It is the moment water met cement, and every judgement
 * about whether this load is still good is measured from it — so it is typed
 * from the paper rather than defaulted to now.
 *
 * A rejected load is kept, with the reason. A truck that was sent away is one
 * of the more important things that happened that day, and deleting the row
 * would leave a pour that silently does not add up.
 */
@Entity(
    tableName = "concrete_tickets",
    foreignKeys = [
        ForeignKey(
            entity = ConcretePourEntity::class,
            parentColumns = ["id"],
            childColumns = ["pourId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("pourId")],
)
data class ConcreteTicketEntity(
    @PrimaryKey val id: String,
    val pourId: String,
    /** The number printed on the supplier's delivery note. */
    val ticketNumber: String?,
    val truckNumber: String?,
    val volume: Double,
    val dispatchedAt: Long,
    val arrivedAt: Long?,
    val dischargedAt: Long?,
    /** Slump in centimetres, as measured on site. */
    val slumpCm: Double?,
    val rejected: Boolean,
    val rejectionReason: String?,
    val recordedByName: String,
    val createdAt: Long,
)
