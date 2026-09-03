package il.co.tradesmanager.data.repository

import il.co.tradesmanager.data.local.dao.ScheduleDao
import il.co.tradesmanager.data.local.entity.TaskBlockEntity
import il.co.tradesmanager.data.local.entity.TimeEntryEntity
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ScheduleRepository(
    private val dao: ScheduleDao,
    private val audit: AuditTrail,
) {

    fun observeDay(date: LocalDate): Flow<List<TaskBlockEntity>> = dao.observeDay(date.toEpochDay())

    fun observeWeek(startOfWeek: LocalDate): Flow<List<TaskBlockEntity>> =
        dao.observeRange(startOfWeek.toEpochDay(), startOfWeek.plusDays(6).toEpochDay())

    fun observeOpenTimeEntry(): Flow<TimeEntryEntity?> = dao.observeOpenTimeEntry()

    suspend fun save(block: TaskBlockEntity, actorName: String): TaskBlockEntity {
        val now = System.currentTimeMillis()
        val stored = block.copy(
            createdAt = block.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now,
        )
        dao.upsert(stored)
        audit.record(ENTITY, stored.id, AuditTrail.Action.UPDATE, actorName, stored.title)
        return stored
    }

    suspend fun setDone(id: String, done: Boolean, actorName: String) {
        dao.setDone(id, done, System.currentTimeMillis())
        audit.record(ENTITY, id, AuditTrail.Action.UPDATE, actorName, if (done) "done" else "reopened")
    }

    suspend fun delete(id: String, actorName: String) {
        dao.delete(id)
        audit.record(ENTITY, id, AuditTrail.Action.DELETE, actorName, "Time block removed")
    }

    /** Copies a day's blocks onto another date — the "same again tomorrow" case. */
    suspend fun copyDay(from: List<TaskBlockEntity>, to: LocalDate, actorName: String) {
        val now = System.currentTimeMillis()
        dao.upsertAll(
            from.map {
                it.copy(
                    id = UUID.randomUUID().toString(),
                    epochDay = to.toEpochDay(),
                    isDone = false,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
        audit.record(ENTITY, to.toString(), AuditTrail.Action.CREATE, actorName, "Copied ${from.size} blocks")
    }

    suspend fun checkIn(
        workerName: String,
        projectId: String?,
        latitude: Double?,
        longitude: Double?,
    ): TimeEntryEntity {
        val entry = TimeEntryEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            workerName = workerName,
            checkInAt = System.currentTimeMillis(),
            latitude = latitude,
            longitude = longitude,
        )
        dao.upsertTimeEntry(entry)
        audit.record("time_entry", entry.id, AuditTrail.Action.CREATE, workerName, "Checked in")
        return entry
    }

    suspend fun checkOut(entry: TimeEntryEntity) {
        val closed = entry.copy(checkOutAt = System.currentTimeMillis())
        dao.upsertTimeEntry(closed)
        audit.record(
            "time_entry", closed.id, AuditTrail.Action.UPDATE, closed.workerName,
            "Checked out after ${closed.minutesWorked ?: 0} min",
        )
    }

    private companion object {
        const val ENTITY = "task_block"
    }
}
