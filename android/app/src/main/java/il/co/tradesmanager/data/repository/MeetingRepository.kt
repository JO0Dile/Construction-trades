package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.work.Meetings
import il.co.tradesmanager.data.local.dao.MeetingDao
import il.co.tradesmanager.data.local.entity.MeetingActionEntity
import il.co.tradesmanager.data.local.entity.MeetingEntity
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A job's meetings and the points agreed at them, with the rules in
 * `core.work.Meetings` applied on every write, under one lock for the two
 * numberings and for a point being closed twice.
 */
class MeetingRepository(
    private val dao: MeetingDao,
    private val audit: AuditTrail,
) {

    enum class Refusal {
        NOT_ALLOWED,
        HELD_IN_FUTURE,
        BLANK_ACTION,
        DUE_BEFORE_MEETING,
        BLANK_CLOSING_NOTE,
        ALREADY_CLOSED,
        UNKNOWN,
    }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    private val lock = Mutex()

    fun observeForProject(projectId: String): Flow<List<MeetingEntity>> = dao.observeForProject(projectId)

    fun observeActionsForProject(projectId: String): Flow<List<MeetingActionEntity>> = dao.observeActionsForProject(projectId)

    /** Every job's points, recent first, for the search box. */
    suspend fun allActions(): List<MeetingActionEntity> = dao.allActions()

    suspend fun record(
        role: Role,
        projectId: String,
        kind: Meetings.Kind,
        heldOn: LocalDate,
        attendees: String,
        notes: String,
        byName: String,
        today: LocalDate = LocalDate.now(),
        now: Long = System.currentTimeMillis(),
    ): Result<MeetingEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        Meetings.meetingRefusal(heldOn, today)?.let { return@withLock Result.failure(Refused(it.asRefusal())) }
        runCatching {
            val meeting = MeetingEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                reference = Meetings.reference(dao.countForProject(projectId)),
                kind = kind.name,
                heldOnDay = heldOn.toEpochDay(),
                attendees = attendees.trim().ifBlank { null },
                notes = notes.trim().ifBlank { null },
                recordedByName = byName,
                recordedAt = now,
            )
            dao.upsert(meeting)
            audit.record(
                ENTITY, meeting.id, AuditTrail.Action.CREATE, byName,
                Summary.of(Summaries.MT_RECORDED, meeting.reference, heldOn.toString()),
            )
            meeting
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** A point agreed at [meetingId], numbered under it. */
    suspend fun raise(
        role: Role,
        meetingId: String,
        text: String,
        ownerName: String,
        dueOn: LocalDate?,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<MeetingActionEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val meeting = dao.meeting(meetingId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        Meetings.actionRefusal(text, dueOn, LocalDate.ofEpochDay(meeting.heldOnDay))?.let {
            return@withLock Result.failure(Refused(it.asRefusal()))
        }
        runCatching {
            val action = MeetingActionEntity(
                id = UUID.randomUUID().toString(),
                meetingId = meeting.id,
                projectId = meeting.projectId,
                reference = Meetings.actionReference(meeting.reference, dao.countActionsFor(meeting.id) + 1),
                text = text.trim(),
                ownerName = ownerName.trim().ifBlank { null },
                dueOnDay = dueOn?.toEpochDay(),
                raisedAt = now,
            )
            dao.upsertAction(action)
            audit.record(
                ACTION_ENTITY, action.id, AuditTrail.Action.CREATE, byName,
                Summary.of(Summaries.MT_ACTION_RAISED, action.reference, action.ownerName ?: "—"),
            )
            action
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** Done, with what was done. Once. */
    suspend fun close(
        role: Role,
        actionId: String,
        note: String,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<MeetingActionEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val action = dao.action(actionId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        Meetings.closeRefusal(note, action.closedAt != null)?.let { return@withLock Result.failure(Refused(it.asRefusal())) }
        val closed = action.copy(closedAt = now, closedByName = byName, closingNote = note.trim())
        runCatching {
            dao.upsertAction(closed)
            audit.record(
                ACTION_ENTITY, action.id, AuditTrail.Action.UPDATE, byName,
                Summary.of(Summaries.MT_ACTION_CLOSED, action.reference),
            )
            closed
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    private fun Meetings.Refusal.asRefusal(): Refusal = when (this) {
        Meetings.Refusal.HELD_IN_FUTURE -> Refusal.HELD_IN_FUTURE
        Meetings.Refusal.BLANK_ACTION -> Refusal.BLANK_ACTION
        Meetings.Refusal.DUE_BEFORE_MEETING -> Refusal.DUE_BEFORE_MEETING
        Meetings.Refusal.BLANK_CLOSING_NOTE -> Refusal.BLANK_CLOSING_NOTE
        Meetings.Refusal.ALREADY_CLOSED -> Refusal.ALREADY_CLOSED
    }

    companion object {
        /** What is agreed about the work is the plan's, kept by whoever keeps the plan. */
        fun mayWrite(role: Role): Boolean = role.canWrite(Lens.PLAN)

        fun mayRead(role: Role): Boolean = role.canRead(Lens.PLAN)

        private const val ENTITY = "meeting"
        private const val ACTION_ENTITY = "meeting_action"
    }
}
