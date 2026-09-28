package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.work.Delays
import il.co.tradesmanager.data.local.dao.DelayDao
import il.co.tradesmanager.data.local.entity.DelayEventEntity
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A job's delay events, with the rules in `core.work.Delays` applied on the
 * write. Every write is under one lock: the numbering, and the end and the
 * notice that are each written once.
 */
class DelayRepository(
    private val dao: DelayDao,
    private val audit: AuditTrail,
) {

    enum class Refusal {
        NOT_ALLOWED,
        BLANK_DESCRIPTION,
        STARTS_IN_FUTURE,
        ENDS_BEFORE_START,
        ALREADY_ENDED,
        NOTICE_ALREADY_GIVEN,
        NOBODY_NOTIFIED,
        NOTICE_BEFORE_START,
        UNKNOWN,
    }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    private val lock = Mutex()

    /** Every job's rows, recent first, for the search box. */
    suspend fun all(): List<DelayEventEntity> = dao.all()

    fun observeForProject(projectId: String): Flow<List<DelayEventEntity>> = dao.observeForProject(projectId)

    suspend fun record(
        role: Role,
        projectId: String,
        cause: Delays.Cause,
        description: String,
        affectedWork: String?,
        startedOn: LocalDate,
        relatedReference: String?,
        byName: String,
        today: LocalDate = LocalDate.now(),
        now: Long = System.currentTimeMillis(),
    ): Result<DelayEventEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        Delays.recordRefusal(description, startedOn, today)?.let { return@withLock Result.failure(Refused(it.asRefusal())) }
        runCatching {
            val event = DelayEventEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                reference = Delays.reference(dao.countForProject(projectId)),
                cause = cause.name,
                description = description.trim(),
                affectedWork = affectedWork?.trim()?.ifBlank { null },
                startedOnDay = startedOn.toEpochDay(),
                relatedReference = relatedReference?.trim()?.ifBlank { null },
                recordedByName = byName,
                recordedAt = now,
            )
            dao.upsert(event)
            audit.record(
                ENTITY, event.id, AuditTrail.Action.CREATE, byName,
                Summary.of(Summaries.DE_RECORDED, event.reference, startedOn.toString()),
            )
            event
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** The day the work could go ahead again. */
    suspend fun end(role: Role, eventId: String, endedOn: LocalDate, byName: String): Result<DelayEventEntity> =
        lock.withLock {
            if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
            val event = dao.event(eventId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
            Delays.endRefusal(LocalDate.ofEpochDay(event.startedOnDay), event.endedOnDay?.let(LocalDate::ofEpochDay), endedOn)
                ?.let { return@withLock Result.failure(Refused(it.asRefusal())) }
            val ended = event.copy(endedOnDay = endedOn.toEpochDay(), endRecordedByName = byName)
            runCatching {
                dao.upsert(ended)
                audit.record(
                    ENTITY, event.id, AuditTrail.Action.UPDATE, byName,
                    Summary.of(Summaries.DE_ENDED, event.reference, endedOn.toString()),
                )
                ended
            }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
        }

    /** That the other side was told: to whom, and on what day. */
    suspend fun notice(
        role: Role,
        eventId: String,
        notifiedTo: String,
        notifiedOn: LocalDate,
        byName: String,
    ): Result<DelayEventEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val event = dao.event(eventId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        Delays.noticeRefusal(
            LocalDate.ofEpochDay(event.startedOnDay),
            event.notifiedOnDay?.let(LocalDate::ofEpochDay),
            notifiedTo,
            notifiedOn,
        )?.let { return@withLock Result.failure(Refused(it.asRefusal())) }
        val notified = event.copy(
            notifiedTo = notifiedTo.trim(),
            notifiedOnDay = notifiedOn.toEpochDay(),
            noticeRecordedByName = byName,
        )
        runCatching {
            dao.upsert(notified)
            audit.record(
                ENTITY, event.id, AuditTrail.Action.UPDATE, byName,
                Summary.of(Summaries.DE_NOTIFIED, event.reference, notified.notifiedTo.orEmpty()),
            )
            notified
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    private fun Delays.Refusal.asRefusal(): Refusal = when (this) {
        Delays.Refusal.BLANK_DESCRIPTION -> Refusal.BLANK_DESCRIPTION
        Delays.Refusal.STARTS_IN_FUTURE -> Refusal.STARTS_IN_FUTURE
        Delays.Refusal.ENDS_BEFORE_START -> Refusal.ENDS_BEFORE_START
        Delays.Refusal.ALREADY_ENDED -> Refusal.ALREADY_ENDED
        Delays.Refusal.NOTICE_ALREADY_GIVEN -> Refusal.NOTICE_ALREADY_GIVEN
        Delays.Refusal.NOBODY_NOTIFIED -> Refusal.NOBODY_NOTIFIED
        Delays.Refusal.NOTICE_BEFORE_START -> Refusal.NOTICE_BEFORE_START
    }

    companion object {
        /** Whoever plans the work keeps the record of what held it up. */
        fun mayWrite(role: Role): Boolean = role.canWrite(Lens.PLAN)

        fun mayRead(role: Role): Boolean = role.canRead(Lens.PLAN)

        private const val ENTITY = "delay_event"
    }
}
