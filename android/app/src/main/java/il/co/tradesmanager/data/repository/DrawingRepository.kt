package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.evidence.Drawings
import il.co.tradesmanager.data.local.dao.DrawingDao
import il.co.tradesmanager.data.local.entity.DrawingEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A job's drawing register, with the rules in `core.evidence.Drawings`
 * applied on the write.
 *
 * Recording a revision and superseding the one before are one step, under a
 * lock, so two phones' worth of taps on the same drawing cannot leave two
 * revisions both marked current.
 */
class DrawingRepository(
    private val dao: DrawingDao,
    private val audit: AuditTrail,
) {

    enum class Refusal { NOT_ALLOWED, NO_NUMBER, NO_REVISION, ALREADY_CURRENT, UNKNOWN }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    private val recording = Mutex()

    fun observeForProject(projectId: String): Flow<List<DrawingEntity>> = dao.observeForProject(projectId)

    suspend fun record(
        role: Role,
        projectId: String,
        number: String,
        title: String,
        revision: String,
        notes: String?,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<DrawingEntity> = recording.withLock {
        if (!mayRecord(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val existing = dao.forProject(projectId)
        val current = Drawings.currentOf(existing.map(::asSheet), number)
            ?.let { sheet -> existing.first { it.id == sheet.id } }
        Drawings.refusal(number, revision, current?.let(::asSheet))?.let {
            return@withLock Result.failure(
                Refused(
                    when (it) {
                        Drawings.Refusal.NO_NUMBER -> Refusal.NO_NUMBER
                        Drawings.Refusal.NO_REVISION -> Refusal.NO_REVISION
                        Drawings.Refusal.ALREADY_CURRENT -> Refusal.ALREADY_CURRENT
                    },
                ),
            )
        }
        val drawing = DrawingEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            number = number.trim(),
            // A new revision of a drawing keeps its title unless one was typed.
            title = title.trim().ifBlank { current?.title.orEmpty() },
            revision = revision.trim(),
            receivedAt = now,
            notes = notes?.trim()?.ifBlank { null },
            recordedByName = byName,
        )
        runCatching {
            current?.let { dao.upsert(it.copy(supersededAt = now)) }
            dao.upsert(drawing)
            audit.record(
                ENTITY, drawing.id, AuditTrail.Action.CREATE, byName,
                if (current == null) {
                    Summary.of(Summaries.DRAWING_RECEIVED, drawing.number, drawing.revision)
                } else {
                    Summary.of(Summaries.DRAWING_SUPERSEDED, drawing.number, drawing.revision, current.revision)
                },
            )
            drawing
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    companion object {
        /** Whoever books things in: the drawings are part of what arrives on a job. */
        fun mayRecord(role: Role): Boolean = role.canWrite(Lens.PLAN)

        fun asSheet(row: DrawingEntity): Drawings.Sheet = Drawings.Sheet(
            id = row.id,
            number = row.number,
            revision = row.revision,
            receivedAt = row.receivedAt,
            supersededAt = row.supersededAt,
        )

        private const val ENTITY = "drawing"
    }
}
