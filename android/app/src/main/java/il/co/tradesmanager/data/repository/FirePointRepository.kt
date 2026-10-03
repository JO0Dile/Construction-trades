package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.safety.FirePoints
import il.co.tradesmanager.data.local.dao.FirePointDao
import il.co.tradesmanager.data.local.entity.FirePointCheckEntity
import il.co.tradesmanager.data.local.entity.FirePointEntity
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A job's fire points and the looks at them, with the rules in
 * `core.safety.FirePoints` applied on every write, under one lock for the
 * numbering and for a look racing the point being taken away.
 */
class FirePointRepository(
    private val dao: FirePointDao,
    private val audit: AuditTrail,
) {

    enum class Refusal {
        NOT_ALLOWED,
        BLANK_LOCATION,
        SERVICE_DUE_IN_PAST,
        FAULT_NEEDS_NOTE,
        ALREADY_REMOVED,
        UNKNOWN,
    }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    private val lock = Mutex()

    /** Every job's rows, recent first, for the search box. */
    suspend fun all(): List<FirePointEntity> = dao.all()

    fun observeForProject(projectId: String): Flow<List<FirePointEntity>> = dao.observeForProject(projectId)

    fun observeChecksForProject(projectId: String): Flow<List<FirePointCheckEntity>> = dao.observeChecksForProject(projectId)

    suspend fun add(
        role: Role,
        projectId: String,
        kind: FirePoints.Kind,
        location: String,
        tagNumber: String,
        serviceDueOn: LocalDate?,
        byName: String,
        today: LocalDate = LocalDate.now(),
        now: Long = System.currentTimeMillis(),
    ): Result<FirePointEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        FirePoints.addRefusal(location, serviceDueOn, today)?.let { return@withLock Result.failure(Refused(it.asRefusal())) }
        runCatching {
            val point = FirePointEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                reference = FirePoints.reference(dao.countForProject(projectId)),
                kind = kind.name,
                location = location.trim(),
                tagNumber = tagNumber.trim().ifBlank { null },
                serviceDueOnDay = serviceDueOn?.toEpochDay(),
                addedAt = now,
                addedByName = byName,
            )
            dao.upsert(point)
            audit.record(
                ENTITY, point.id, AuditTrail.Action.CREATE, byName,
                Summary.of(Summaries.FP_ADDED, point.reference, point.location),
            )
            point
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** The monthly look. A fault says what was wrong. */
    suspend fun check(
        role: Role,
        pointId: String,
        ok: Boolean,
        note: String,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<FirePointCheckEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val point = dao.point(pointId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        FirePoints.checkRefusal(ok, note, point.removedAt != null)?.let { return@withLock Result.failure(Refused(it.asRefusal())) }
        runCatching {
            val check = FirePointCheckEntity(
                id = UUID.randomUUID().toString(),
                firePointId = point.id,
                projectId = point.projectId,
                checkedAt = now,
                ok = ok,
                note = note.trim().ifBlank { null },
                checkedByName = byName,
            )
            dao.insertCheck(check)
            audit.record(
                ENTITY, point.id, AuditTrail.Action.UPDATE, byName,
                if (ok) {
                    Summary.of(Summaries.FP_CHECKED, point.reference)
                } else {
                    Summary.of(Summaries.FP_FAULT, point.reference, check.note.orEmpty())
                },
            )
            check
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** The technician has been; the new date on the label replaces the old one. */
    suspend fun serviced(
        role: Role,
        pointId: String,
        nextDueOn: LocalDate,
        byName: String,
        today: LocalDate = LocalDate.now(),
    ): Result<FirePointEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val point = dao.point(pointId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        FirePoints.serviceRefusal(nextDueOn, point.removedAt != null, today)?.let {
            return@withLock Result.failure(Refused(it.asRefusal()))
        }
        val updated = point.copy(serviceDueOnDay = nextDueOn.toEpochDay())
        runCatching {
            dao.upsert(updated)
            audit.record(
                ENTITY, point.id, AuditTrail.Action.UPDATE, byName,
                Summary.of(Summaries.FP_SERVICED, point.reference, nextDueOn.toString()),
            )
            updated
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** Taken away. The row and its looks stay. */
    suspend fun remove(
        role: Role,
        pointId: String,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<FirePointEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val point = dao.point(pointId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        FirePoints.removeRefusal(point.removedAt != null)?.let { return@withLock Result.failure(Refused(it.asRefusal())) }
        val removed = point.copy(removedAt = now, removedByName = byName)
        runCatching {
            dao.upsert(removed)
            audit.record(ENTITY, point.id, AuditTrail.Action.UPDATE, byName, Summary.of(Summaries.FP_REMOVED, point.reference))
            removed
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    private fun FirePoints.Refusal.asRefusal(): Refusal = when (this) {
        FirePoints.Refusal.BLANK_LOCATION -> Refusal.BLANK_LOCATION
        FirePoints.Refusal.SERVICE_DUE_IN_PAST -> Refusal.SERVICE_DUE_IN_PAST
        FirePoints.Refusal.FAULT_NEEDS_NOTE -> Refusal.FAULT_NEEDS_NOTE
        FirePoints.Refusal.ALREADY_REMOVED -> Refusal.ALREADY_REMOVED
    }

    companion object {
        /** The site's safety record, kept by whoever keeps the rest of it. */
        fun mayWrite(role: Role): Boolean = role.canWrite(Lens.EVIDENCE)

        fun mayRead(role: Role): Boolean = role.canRead(Lens.EVIDENCE)

        /** Each point's newest look, which is the one its state rests on. */
        fun latestByPoint(checks: List<FirePointCheckEntity>): Map<String, FirePointCheckEntity> =
            checks.groupBy { it.firePointId }.mapValues { (_, looks) -> looks.maxBy { it.checkedAt } }

        private const val ENTITY = "fire_point"
    }
}
