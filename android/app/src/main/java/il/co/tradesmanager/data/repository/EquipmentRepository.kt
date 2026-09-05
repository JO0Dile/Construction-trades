package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.money.HireCost
import il.co.tradesmanager.core.people.Expiry
import il.co.tradesmanager.data.local.dao.EquipmentDao
import il.co.tradesmanager.data.local.entity.EquipmentEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Plant: what the firm owns or hires, where it is, and when it is next due a
 * service.
 *
 * Part of the Stuff lens. Kept apart from inventory because the two answer
 * different questions — stock asks "have we got enough", plant asks "where is
 * it and is it legal to use".
 */
class EquipmentRepository(
    private val dao: EquipmentDao,
    private val audit: AuditTrail,
) {

    object Ownership {
        const val OWNED = "OWNED"
        const val HIRED = "HIRED"
    }

    object Status {
        /** In the yard, ready to go out. */
        const val AVAILABLE = "AVAILABLE"
        const val ON_SITE = "ON_SITE"
        /** Being repaired or serviced; must not be sent to a job. */
        const val MAINTENANCE = "MAINTENANCE"
        /** Hired kit that has gone back. Stops accruing on its return date. */
        const val OFF_HIRE = "OFF_HIRE"

        val all = listOf(AVAILABLE, ON_SITE, MAINTENANCE, OFF_HIRE)
    }

    fun observeAll(): Flow<List<EquipmentEntity>> = dao.observeAll()

    fun observeForProject(projectId: String): Flow<List<EquipmentEntity>> =
        dao.observeForProject(projectId)

    /** Plant lapsing into a service inside the same window as a person's ticket. */
    fun observeServiceDue(now: () -> Long = System::currentTimeMillis): Flow<List<EquipmentEntity>> =
        dao.observeServiceDueBefore(now() + Expiry.WARNING_DAYS * DAY_MILLIS)

    /**
     * What hired plant on a job has run up so far.
     *
     * Summed in Kotlin from [HireCost] rather than in SQL, so the rule for how
     * a part day counts exists once. A firm has tens of machines; the cost of
     * summing them here is nothing next to the cost of two definitions.
     */
    fun observeCommittedHire(projectId: String): Flow<Double> =
        dao.observeForProject(projectId).map { onJob ->
            val now = System.currentTimeMillis()
            onJob.filter { it.ownership == Ownership.HIRED }
                .sumOf { HireCost.accrued(it.hireRatePerDay, it.hireStartedOn, it.hireEndsOn, now) }
        }

    suspend fun add(
        name: String,
        ownership: String,
        serialNumber: String?,
        hireRatePerDay: Double?,
        serviceDueOn: Long?,
        actorName: String,
    ): EquipmentEntity {
        val now = System.currentTimeMillis()
        val equipment = EquipmentEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            ownership = ownership,
            status = Status.AVAILABLE,
            serialNumber = serialNumber?.trim()?.takeIf { it.isNotEmpty() },
            supplierId = null,
            assignedProjectId = null,
            hireRatePerDay = hireRatePerDay?.takeIf { ownership == Ownership.HIRED },
            // Hire starts counting the moment it is on the books, not when
            // somebody remembers to set a date.
            hireStartedOn = now.takeIf { ownership == Ownership.HIRED },
            hireEndsOn = null,
            lastServicedOn = null,
            serviceDueOn = serviceDueOn,
            notes = null,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(equipment)
        audit.record(ENTITY, equipment.id, AuditTrail.Action.CREATE, actorName, equipment.name)
        return equipment
    }

    /**
     * Sends a machine to a job, or brings it back to the yard.
     *
     * Assigning also sets it on site, because a machine that is on a job and
     * "available" is a machine two foremen will both count on.
     */
    suspend fun assign(equipment: EquipmentEntity, projectId: String?, actorName: String) {
        dao.upsert(
            equipment.copy(
                assignedProjectId = projectId,
                status = if (projectId == null) Status.AVAILABLE else Status.ON_SITE,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        audit.record(
            ENTITY, equipment.id, AuditTrail.Action.UPDATE, actorName,
            if (projectId == null) "${equipment.name} back in the yard" else "${equipment.name} on site",
        )
    }

    suspend fun setStatus(equipment: EquipmentEntity, status: String, actorName: String) {
        val now = System.currentTimeMillis()
        dao.upsert(
            equipment.copy(
                status = status,
                // Going off hire stops the meter on the day it goes back.
                hireEndsOn = if (status == Status.OFF_HIRE) now else equipment.hireEndsOn,
                updatedAt = now,
            ),
        )
        audit.record(
            ENTITY, equipment.id, AuditTrail.Action.UPDATE, actorName,
            "${equipment.name} ${status.lowercase().replace('_', ' ')}",
        )
    }

    suspend fun recordService(equipment: EquipmentEntity, nextDueOn: Long?, actorName: String) {
        val now = System.currentTimeMillis()
        dao.upsert(
            equipment.copy(lastServicedOn = now, serviceDueOn = nextDueOn, updatedAt = now),
        )
        audit.record(ENTITY, equipment.id, AuditTrail.Action.UPDATE, actorName, "${equipment.name} serviced")
    }

    suspend fun remove(equipment: EquipmentEntity, actorName: String) {
        dao.delete(equipment)
        audit.record(ENTITY, equipment.id, AuditTrail.Action.DELETE, actorName, equipment.name)
    }

    private companion object {
        const val ENTITY = "equipment"
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
