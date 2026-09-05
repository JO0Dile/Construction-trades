package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.safety.Excavation
import il.co.tradesmanager.data.local.dao.ExcavationDao
import il.co.tradesmanager.data.local.entity.ExcavationEntity
import il.co.tradesmanager.data.local.entity.ExcavationInspectionEntity
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * The excavation register.
 *
 * Shaped like the scaffold register on purpose — inspections inserted and never
 * touched, a summary on the parent so the list can answer its one question
 * without opening every history — because they are the same kind of record.
 * What differs is how long an inspection is worth and what has to be true
 * before anybody digs at all.
 */
class ExcavationRepository(
    private val dao: ExcavationDao,
    private val audit: AuditTrail,
) {

    fun observeForProject(projectId: String): Flow<List<ExcavationEntity>> =
        dao.observeForProject(projectId)

    fun observe(id: String): Flow<ExcavationEntity?> = dao.observe(id)

    fun observeInspections(excavationId: String): Flow<List<ExcavationInspectionEntity>> =
        dao.observeInspections(excavationId)

    suspend fun create(
        projectId: String,
        location: String,
        depthMetres: Double?,
        support: Excavation.Support,
        actorName: String,
    ): ExcavationEntity {
        val now = System.currentTimeMillis()
        val excavation = ExcavationEntity(
            id = UUID.randomUUID().toString(),
            reference = String.format(Locale.ROOT, "EXC-%03d", dao.count() + 1),
            projectId = projectId,
            location = location.trim(),
            depthMetres = depthMetres,
            support = support.name,
            // Not "assumed clear". Somebody has to have looked, and until they
            // have, the register says nobody knows what is down there.
            servicesLocatedAt = null,
            servicesLocatedByName = null,
            servicesNotes = null,
            lastInspectedAt = null,
            lastInspectionPassed = false,
            lastInspectorName = null,
            disturbedAt = null,
            backfilledAt = null,
            notes = null,
            createdByName = actorName,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(excavation)
        audit.record(
            EXCAVATION, excavation.id, AuditTrail.Action.CREATE, actorName,
            "${excavation.reference} ${excavation.location}",
        )
        return excavation
    }

    suspend fun setDimensions(
        excavation: ExcavationEntity,
        depthMetres: Double?,
        support: Excavation.Support,
        actorName: String,
    ) {
        dao.upsert(
            excavation.copy(
                depthMetres = depthMetres,
                support = support.name,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        audit.record(EXCAVATION, excavation.id, AuditTrail.Action.UPDATE, actorName, excavation.reference)
    }

    /**
     * Records that somebody found out what is buried here.
     *
     * The one thing on this screen that cannot be put right afterwards. A
     * trench that is too steep can be battered back; a cable that has been cut
     * has been cut, and if somebody was holding the tool at the time the rest
     * of the register is beside the point.
     */
    suspend fun recordServicesLocated(
        excavation: ExcavationEntity,
        notes: String?,
        actorName: String,
    ) {
        val now = System.currentTimeMillis()
        dao.upsert(
            excavation.copy(
                servicesLocatedAt = now,
                servicesLocatedByName = actorName,
                servicesNotes = notes?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = now,
            ),
        )
        audit.record(
            EXCAVATION, excavation.id, AuditTrail.Action.SIGN_OFF, actorName,
            "${excavation.reference} services located",
        )
    }

    suspend fun inspect(
        excavation: ExcavationEntity,
        passed: Boolean,
        defects: String?,
        actionTaken: String?,
        signature: String?,
        inspectorName: String,
        inspectedAt: Long = System.currentTimeMillis(),
    ) {
        dao.insertInspection(
            ExcavationInspectionEntity(
                id = UUID.randomUUID().toString(),
                excavationId = excavation.id,
                inspectedAt = inspectedAt,
                inspectorName = inspectorName,
                passed = passed,
                defects = defects?.trim()?.takeIf { it.isNotEmpty() },
                actionTaken = actionTaken?.trim()?.takeIf { it.isNotEmpty() },
                signature = signature,
                createdAt = System.currentTimeMillis(),
            ),
        )
        // Only when this is the latest thing known, so an inspection entered
        // late cannot revive a state the site has already moved past.
        if (inspectedAt >= (excavation.lastInspectedAt ?: Long.MIN_VALUE)) {
            dao.upsert(
                excavation.copy(
                    lastInspectedAt = inspectedAt,
                    lastInspectionPassed = passed,
                    lastInspectorName = inspectorName,
                    // A passing inspection clears a disturbance only if it came
                    // after it — the same rule as a scaffold's alteration.
                    disturbedAt = if (
                        passed && inspectedAt >= (excavation.disturbedAt ?: Long.MIN_VALUE)
                    ) {
                        null
                    } else {
                        excavation.disturbedAt
                    },
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        audit.record(
            EXCAVATION, excavation.id, AuditTrail.Action.SIGN_OFF, inspectorName,
            "${excavation.reference} ${if (passed) "passed" else "failed"}",
        )
    }

    /** Rain, a collapse, vibration, a load at the edge. Takes it out of use. */
    suspend fun markDisturbed(excavation: ExcavationEntity, note: String?, actorName: String) {
        val now = System.currentTimeMillis()
        dao.upsert(
            excavation.copy(
                disturbedAt = now,
                notes = note?.trim()?.takeIf { it.isNotEmpty() } ?: excavation.notes,
                updatedAt = now,
            ),
        )
        audit.record(EXCAVATION, excavation.id, AuditTrail.Action.UPDATE, actorName, "Disturbed")
    }

    suspend fun backfill(excavation: ExcavationEntity, actorName: String) {
        val now = System.currentTimeMillis()
        dao.upsert(excavation.copy(backfilledAt = now, updatedAt = now))
        audit.record(EXCAVATION, excavation.id, AuditTrail.Action.UPDATE, actorName, "Backfilled")
    }

    suspend fun remove(excavation: ExcavationEntity, actorName: String) {
        dao.delete(excavation)
        audit.record(
            EXCAVATION, excavation.id, AuditTrail.Action.DELETE, actorName, excavation.reference,
        )
    }

    private companion object {
        const val EXCAVATION = "excavation"
    }
}
