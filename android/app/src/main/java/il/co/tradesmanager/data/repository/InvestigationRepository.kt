package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.safety.Investigations
import il.co.tradesmanager.data.local.dao.InvestigationDao
import il.co.tradesmanager.data.local.entity.IncidentActionEntity
import il.co.tradesmanager.data.local.entity.IncidentEntity
import il.co.tradesmanager.data.local.entity.IncidentInvestigationEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The investigation of each incident and its corrective actions, with the
 * rules in `core.safety.Investigations` applied on every write, under one
 * lock for the numbering and for something being closed twice.
 */
class InvestigationRepository(
    private val dao: InvestigationDao,
    /** The incident an investigation is of, from the incident register. */
    private val incidentOf: suspend (String) -> IncidentEntity?,
    private val audit: AuditTrail,
) {

    enum class Refusal {
        NOT_ALLOWED,
        NOT_STARTED,
        BLANK_IMMEDIATE_CAUSE,
        NO_CAUSE,
        ACTIONS_OPEN,
        ALREADY_CLOSED,
        BLANK_ACTION,
        DUE_BEFORE_INCIDENT,
        BLANK_CLOSING_NOTE,
        ACTION_ALREADY_CLOSED,
        UNKNOWN,
    }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    private val lock = Mutex()

    fun observe(incidentId: String): Flow<IncidentInvestigationEntity?> = dao.observe(incidentId)

    fun observeActions(incidentId: String): Flow<List<IncidentActionEntity>> = dao.observeActions(incidentId)

    fun observeAll(): Flow<List<IncidentInvestigationEntity>> = dao.observeAll()

    fun observeOutstandingForProject(projectId: String): Flow<Int> = dao.observeOutstandingForProject(projectId)

    fun observeOverdueActionsForProject(projectId: String, today: LocalDate): Flow<Int> =
        dao.observeOverdueActionsForProject(projectId, today.toEpochDay())

    /**
     * Starts the investigation, or writes what has been found so far. Every
     * field may be blank while it is under way; closing is what asks for
     * them.
     */
    suspend fun save(
        role: Role,
        incidentId: String,
        immediateCause: String,
        causes: Set<Investigations.Cause>,
        findings: String,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<IncidentInvestigationEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        incidentOf(incidentId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        val existing = dao.investigation(incidentId)
        if (existing?.closedAt != null) return@withLock Result.failure(Refused(Refusal.ALREADY_CLOSED))
        runCatching {
            val saved = IncidentInvestigationEntity(
                incidentId = incidentId,
                immediateCause = immediateCause.trim().ifBlank { null },
                causes = Investigations.encode(causes),
                findings = findings.trim().ifBlank { null },
                startedAt = existing?.startedAt ?: now,
                startedByName = existing?.startedByName ?: byName,
                updatedAt = now,
            )
            dao.upsert(saved)
            if (existing == null) {
                audit.record(ENTITY, incidentId, AuditTrail.Action.CREATE, byName, Summaries.INV_STARTED)
            } else {
                audit.record(ENTITY, incidentId, AuditTrail.Action.UPDATE, byName, Summaries.INV_UPDATED)
            }
            saved
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** Something to be done because of it, numbered under the incident. */
    suspend fun raise(
        role: Role,
        incidentId: String,
        text: String,
        ownerName: String,
        dueOn: LocalDate?,
        byName: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Result<IncidentActionEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val incident = incidentOf(incidentId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        val investigation = dao.investigation(incidentId) ?: return@withLock Result.failure(Refused(Refusal.NOT_STARTED))
        val incidentDay = Instant.ofEpochMilli(incident.occurredAt).atZone(zone).toLocalDate()
        Investigations.actionRefusal(text, dueOn, incidentDay, investigation.closedAt != null)?.let {
            return@withLock Result.failure(Refused(it.asRefusal()))
        }
        runCatching {
            val action = IncidentActionEntity(
                id = UUID.randomUUID().toString(),
                incidentId = incidentId,
                number = dao.countActions(incidentId) + 1,
                text = text.trim(),
                ownerName = ownerName.trim().ifBlank { null },
                dueOnDay = dueOn?.toEpochDay(),
                raisedAt = now,
                raisedByName = byName,
            )
            dao.upsertAction(action)
            audit.record(
                ACTION_ENTITY, action.id, AuditTrail.Action.CREATE, byName,
                Summary.of(Summaries.INV_ACTION_RAISED, action.number.toString(), action.ownerName ?: "—"),
            )
            action
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** Done, with what was done. Once. */
    suspend fun closeAction(
        role: Role,
        actionId: String,
        note: String,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<IncidentActionEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val action = dao.action(actionId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        Investigations.actionCloseRefusal(note, action.closedAt != null)?.let { return@withLock Result.failure(Refused(it.asRefusal())) }
        val closed = action.copy(closedAt = now, closedByName = byName, closingNote = note.trim())
        runCatching {
            dao.upsertAction(closed)
            audit.record(
                ACTION_ENTITY, action.id, AuditTrail.Action.UPDATE, byName,
                Summary.of(Summaries.INV_ACTION_CLOSED, action.number.toString()),
            )
            closed
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** Closed once it says what caused it and what lay behind it, and every action is done. */
    suspend fun close(
        role: Role,
        incidentId: String,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<IncidentInvestigationEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val investigation = dao.investigation(incidentId) ?: return@withLock Result.failure(Refused(Refusal.NOT_STARTED))
        val open = dao.actions(incidentId).count { it.closedAt == null }
        Investigations.closeRefusal(
            investigation.immediateCause,
            Investigations.decode(investigation.causes),
            open,
            investigation.closedAt != null,
        )?.let { return@withLock Result.failure(Refused(it.asRefusal())) }
        val closed = investigation.copy(closedAt = now, closedByName = byName, updatedAt = now)
        runCatching {
            dao.upsert(closed)
            audit.record(ENTITY, incidentId, AuditTrail.Action.UPDATE, byName, Summaries.INV_CLOSED)
            closed
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    private fun Investigations.Refusal.asRefusal(): Refusal = when (this) {
        Investigations.Refusal.BLANK_IMMEDIATE_CAUSE -> Refusal.BLANK_IMMEDIATE_CAUSE
        Investigations.Refusal.NO_CAUSE -> Refusal.NO_CAUSE
        Investigations.Refusal.ACTIONS_OPEN -> Refusal.ACTIONS_OPEN
        Investigations.Refusal.ALREADY_CLOSED -> Refusal.ALREADY_CLOSED
        Investigations.Refusal.BLANK_ACTION -> Refusal.BLANK_ACTION
        Investigations.Refusal.DUE_BEFORE_INCIDENT -> Refusal.DUE_BEFORE_INCIDENT
        Investigations.Refusal.BLANK_CLOSING_NOTE -> Refusal.BLANK_CLOSING_NOTE
        Investigations.Refusal.ACTION_ALREADY_CLOSED -> Refusal.ACTION_ALREADY_CLOSED
    }

    companion object {
        /** Looking into an accident is part of the site's record, kept by whoever keeps it. */
        fun mayWrite(role: Role): Boolean = role.canWrite(Lens.EVIDENCE)

        fun mayRead(role: Role): Boolean = role.canRead(Lens.EVIDENCE)

        private const val ENTITY = "incident_investigation"
        private const val ACTION_ENTITY = "incident_action"
    }
}
