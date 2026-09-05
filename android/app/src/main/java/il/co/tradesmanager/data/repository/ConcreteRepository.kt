package il.co.tradesmanager.data.repository

import il.co.tradesmanager.data.local.dao.ConcreteDao
import il.co.tradesmanager.data.local.entity.ConcretePourEntity
import il.co.tradesmanager.data.local.entity.ConcreteTicketEntity
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Concrete arriving, and what happened to each load.
 *
 * The Stuff lens: this is a delivery, recorded by whoever is on the slab
 * taking them in. What makes it worth its own screen rather than a line in
 * stock is the clock — see `core.evidence.ConcretePour`. Cement does not wait,
 * and the only moment anybody can act on a load that has run out of time is
 * before it is discharged.
 */
class ConcreteRepository(
    private val dao: ConcreteDao,
    private val audit: AuditTrail,
) {

    fun observePours(projectId: String): Flow<List<ConcretePourEntity>> =
        dao.observePours(projectId)

    fun observePour(id: String): Flow<ConcretePourEntity?> = dao.observePour(id)

    fun observeTickets(pourId: String): Flow<List<ConcreteTicketEntity>> =
        dao.observeTickets(pourId)

    fun observePlacedVolume(pourId: String): Flow<Double> = dao.observePlacedVolume(pourId)

    suspend fun startPour(
        projectId: String,
        element: String,
        mixDesign: String?,
        orderedVolume: Double?,
        supplierName: String?,
        temperatureCelsius: Double?,
        actorName: String,
    ): ConcretePourEntity {
        val now = System.currentTimeMillis()
        val pour = ConcretePourEntity(
            id = UUID.randomUUID().toString(),
            reference = String.format(Locale.ROOT, "POUR-%03d", dao.pourCount() + 1),
            projectId = projectId,
            element = element.trim(),
            mixDesign = mixDesign?.trim()?.takeIf { it.isNotEmpty() },
            orderedVolume = orderedVolume,
            supplierName = supplierName?.trim()?.takeIf { it.isNotEmpty() },
            temperatureCelsius = temperatureCelsius,
            startedAt = now,
            completedAt = null,
            notes = null,
            recordedByName = actorName,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsertPour(pour)
        audit.record(POUR, pour.id, AuditTrail.Action.CREATE, actorName, "${pour.reference} ${pour.element}")
        return pour
    }

    suspend fun completePour(pour: ConcretePourEntity, notes: String?, actorName: String) {
        val now = System.currentTimeMillis()
        dao.upsertPour(
            pour.copy(
                completedAt = now,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = now,
            ),
        )
        audit.record(POUR, pour.id, AuditTrail.Action.SIGN_OFF, actorName, "${pour.reference} finished")
    }

    suspend fun removePour(pour: ConcretePourEntity, actorName: String) {
        dao.deletePour(pour)
        audit.record(POUR, pour.id, AuditTrail.Action.DELETE, actorName, pour.reference)
    }

    /**
     * Books a truck in.
     *
     * [dispatchedAt] is read off the supplier's ticket rather than defaulted to
     * now, because it is the moment water met cement and every judgement about
     * this load is measured from it. Recording the time the truck was noticed
     * would quietly make every load look fresher than it is.
     */
    suspend fun addTicket(
        pourId: String,
        ticketNumber: String?,
        truckNumber: String?,
        volume: Double,
        dispatchedAt: Long,
        actorName: String,
    ): ConcreteTicketEntity {
        val ticket = ConcreteTicketEntity(
            id = UUID.randomUUID().toString(),
            pourId = pourId,
            ticketNumber = ticketNumber?.trim()?.takeIf { it.isNotEmpty() },
            truckNumber = truckNumber?.trim()?.takeIf { it.isNotEmpty() },
            volume = volume,
            dispatchedAt = dispatchedAt,
            arrivedAt = System.currentTimeMillis(),
            dischargedAt = null,
            slumpCm = null,
            rejected = false,
            rejectionReason = null,
            recordedByName = actorName,
            createdAt = System.currentTimeMillis(),
        )
        dao.upsertTicket(ticket)
        audit.record(
            TICKET, ticket.id, AuditTrail.Action.CREATE, actorName,
            "Truck ${ticket.truckNumber.orEmpty()} ${ticket.volume}m3",
        )
        return ticket
    }

    /** The slump test, recorded before the load goes in rather than after. */
    suspend fun recordSlump(ticket: ConcreteTicketEntity, slumpCm: Double?) {
        dao.upsertTicket(ticket.copy(slumpCm = slumpCm))
    }

    /** Discharged. The clock stops here and the load is in the structure. */
    suspend fun markPlaced(ticket: ConcreteTicketEntity, actorName: String) {
        if (ticket.rejected || ticket.dischargedAt != null) return
        dao.upsertTicket(ticket.copy(dischargedAt = System.currentTimeMillis()))
        audit.record(TICKET, ticket.id, AuditTrail.Action.UPDATE, actorName, "Placed")
    }

    /**
     * Sends a truck away. Kept, not deleted: a load that was rejected is one of
     * the more important things that happened that day, and removing the row
     * would leave a pour that silently does not add up.
     */
    suspend fun reject(ticket: ConcreteTicketEntity, reason: String?, actorName: String) {
        if (ticket.dischargedAt != null) return
        dao.upsertTicket(
            ticket.copy(
                rejected = true,
                rejectionReason = reason?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
        audit.record(
            TICKET, ticket.id, AuditTrail.Action.UPDATE, actorName,
            "Rejected: ${reason.orEmpty()}",
        )
    }

    suspend fun removeTicket(ticket: ConcreteTicketEntity, actorName: String) {
        dao.deleteTicket(ticket)
        audit.record(TICKET, ticket.id, AuditTrail.Action.DELETE, actorName, ticket.ticketNumber.orEmpty())
    }

    private companion object {
        const val POUR = "concrete_pour"
        const val TICKET = "concrete_ticket"
    }
}
