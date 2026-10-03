package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.evidence.Waste
import il.co.tradesmanager.data.local.dao.WasteDao
import il.co.tradesmanager.data.local.entity.WasteLoadEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * The construction waste register, with the rules in `core.evidence.Waste`
 * applied on the write rather than trusted from the screen.
 */
class WasteRepository(
    private val dao: WasteDao,
    private val audit: AuditTrail,
) {

    /** Why a load was not recorded. */
    enum class Refusal {
        NOT_ALLOWED,
        NO_QUANTITY,
        NO_DESTINATION,
        HAZARDOUS_WITHOUT_TICKET,
        UNKNOWN,
    }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    fun observeForProject(projectId: String): Flow<List<WasteLoadEntity>> =
        dao.observeForProject(projectId)

    suspend fun record(
        role: Role,
        projectId: String,
        stream: Waste.Stream,
        quantity: Double,
        unit: Waste.Measurement,
        destination: Waste.Destination,
        facility: String,
        hauler: String?,
        ticketNumber: String?,
        notes: String?,
        byAccountId: String?,
        byName: String,
    ): Result<WasteLoadEntity> {
        if (!role.canWrite(Lens.EVIDENCE)) return Result.failure(Refused(Refusal.NOT_ALLOWED))
        Waste.refusal(stream, quantity, facility, ticketNumber)?.let {
            return Result.failure(Refused(it.asRefusal()))
        }
        val load = WasteLoadEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            removedAt = System.currentTimeMillis(),
            stream = stream.name,
            quantity = quantity,
            unit = unit.name,
            destination = destination.name,
            facility = facility.trim(),
            hauler = hauler?.trim()?.ifBlank { null },
            ticketNumber = ticketNumber?.trim()?.ifBlank { null },
            notes = notes?.trim()?.ifBlank { null },
            recordedByAccountId = byAccountId,
            recordedByName = byName,
        )
        dao.upsert(load)
        audit.record(
            ENTITY, load.id, AuditTrail.Action.CREATE, byName,
            Summary.of(
                Summaries.WASTE_RECORDED,
                Summary.number(quantity),
                Summary.nest(unitKey(unit)),
                Summary.nest(streamKey(stream)),
                load.facility,
            ),
        )
        return Result.success(load)
    }

    /**
     * Adds the ticket number to a load recorded without one -- the ticket
     * turned up in the lorry's glovebox a week later, which is how it goes.
     *
     * Only ever adds. A number already on the record is what somebody wrote
     * down from the ticket, and replacing it afterwards is not a correction
     * this register makes quietly.
     */
    suspend fun addTicketNumber(
        loadId: String,
        ticketNumber: String,
        actorName: String,
    ): Result<WasteLoadEntity> {
        val load = dao.load(loadId) ?: return Result.failure(Refused(Refusal.UNKNOWN))
        val number = ticketNumber.trim()
        if (number.isEmpty() || !load.ticketNumber.isNullOrBlank()) {
            return Result.failure(Refused(Refusal.UNKNOWN))
        }
        val updated = load.copy(ticketNumber = number)
        dao.upsert(updated)
        audit.record(
            ENTITY, load.id, AuditTrail.Action.UPDATE, actorName,
            Summary.of(Summaries.WASTE_TICKET_ADDED, load.facility),
        )
        return Result.success(updated)
    }

    private fun Waste.Refusal.asRefusal(): Refusal = when (this) {
        Waste.Refusal.NO_QUANTITY -> Refusal.NO_QUANTITY
        Waste.Refusal.NO_DESTINATION -> Refusal.NO_DESTINATION
        Waste.Refusal.HAZARDOUS_WITHOUT_TICKET -> Refusal.HAZARDOUS_WITHOUT_TICKET
    }

    companion object {
        /**
         * A stored row as the rules see it, or null if its vocabulary is one
         * this version does not know. Unknown rows are left out of the totals
         * rather than guessed into a stream they may not be.
         */
        fun asLoad(row: WasteLoadEntity, ticketPhotos: Int): Waste.Load? {
            val stream = runCatching { Waste.Stream.valueOf(row.stream) }.getOrNull() ?: return null
            val unit = runCatching { Waste.Measurement.valueOf(row.unit) }.getOrNull() ?: return null
            val destination = runCatching { Waste.Destination.valueOf(row.destination) }.getOrNull()
                ?: return null
            return Waste.Load(
                stream = stream,
                quantity = row.quantity,
                unit = unit,
                destination = destination,
                facility = row.facility,
                ticketNumber = row.ticketNumber,
                ticketPhotos = ticketPhotos,
            )
        }

        fun streamKey(stream: Waste.Stream): String = "waste_stream_" + stream.name.lowercase()

        fun unitKey(unit: Waste.Measurement): String = "waste_unit_" + unit.name.lowercase()

        private const val ENTITY = "waste_load"
    }
}
