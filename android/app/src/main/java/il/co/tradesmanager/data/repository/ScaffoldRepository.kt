package il.co.tradesmanager.data.repository

import il.co.tradesmanager.data.local.dao.ScaffoldDao
import il.co.tradesmanager.data.local.entity.ScaffoldEntity
import il.co.tradesmanager.data.local.entity.ScaffoldInspectionEntity
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * The scaffold register (פנקס פיגומים).
 *
 * Every inspection is inserted and never touched again; the summary columns on
 * the scaffold are recomputed from the one just recorded. That split is the
 * whole design: the list has to answer "may this be climbed" for thirty
 * scaffolds without opening thirty histories, and the history has to be
 * something nobody can tidy up after an accident.
 */
class ScaffoldRepository(
    private val dao: ScaffoldDao,
    private val audit: AuditTrail,
) {

    /** Why an inspection happened. Regulation asks for all four. */
    enum class Reason { WEEKLY, FIRST_USE, AFTER_ALTERATION, AFTER_WEATHER }

    fun observeForProject(projectId: String): Flow<List<ScaffoldEntity>> =
        dao.observeForProject(projectId)

    fun observe(id: String): Flow<ScaffoldEntity?> = dao.observe(id)

    fun observeInspections(scaffoldId: String): Flow<List<ScaffoldInspectionEntity>> =
        dao.observeInspections(scaffoldId)

    suspend fun erect(
        projectId: String,
        location: String,
        type: String?,
        erectedByName: String?,
        actorName: String,
    ): ScaffoldEntity {
        val now = System.currentTimeMillis()
        val scaffold = ScaffoldEntity(
            id = UUID.randomUUID().toString(),
            reference = String.format(Locale.ROOT, "SCF-%03d", dao.count() + 1),
            projectId = projectId,
            location = location.trim(),
            type = type?.trim()?.takeIf { it.isNotEmpty() },
            erectedByName = erectedByName?.trim()?.takeIf { it.isNotEmpty() },
            erectedAt = now,
            // Deliberately not "passed until proven otherwise". A scaffold
            // that has just gone up has not been inspected, and the register
            // says so until somebody signs that it has.
            lastInspectedAt = null,
            lastInspectionPassed = false,
            lastInspectorName = null,
            alteredAt = null,
            dismantledAt = null,
            notes = null,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(scaffold)
        audit.record(
            SCAFFOLD, scaffold.id, AuditTrail.Action.CREATE, actorName,
            "${scaffold.reference} ${scaffold.location}",
        )
        return scaffold
    }

    /**
     * Records an inspection and moves the scaffold with it.
     *
     * The summary columns are written from this inspection rather than from
     * whichever row happens to be newest, so a late entry for last Tuesday
     * cannot silently become the scaffold's current state.
     */
    suspend fun inspect(
        scaffold: ScaffoldEntity,
        passed: Boolean,
        reason: Reason,
        defects: String?,
        actionTaken: String?,
        signature: String?,
        inspectorName: String,
        inspectedAt: Long = System.currentTimeMillis(),
    ) {
        dao.insertInspection(
            ScaffoldInspectionEntity(
                id = UUID.randomUUID().toString(),
                scaffoldId = scaffold.id,
                inspectedAt = inspectedAt,
                inspectorName = inspectorName,
                passed = passed,
                defects = defects?.trim()?.takeIf { it.isNotEmpty() },
                actionTaken = actionTaken?.trim()?.takeIf { it.isNotEmpty() },
                reason = reason.name,
                signature = signature,
                createdAt = System.currentTimeMillis(),
            ),
        )
        // Only if this is the most recent thing known about the scaffold. An
        // inspection entered after a later one must go in the history without
        // reviving a state the site has already moved past.
        if (inspectedAt >= (scaffold.lastInspectedAt ?: Long.MIN_VALUE)) {
            dao.upsert(
                scaffold.copy(
                    lastInspectedAt = inspectedAt,
                    lastInspectionPassed = passed,
                    lastInspectorName = inspectorName,
                    // A passing inspection clears an alteration only if it
                    // came after it. An inspection entered late, from before
                    // the scaffold was changed, says nothing about the
                    // scaffold that is standing there now.
                    alteredAt = if (passed && inspectedAt >= (scaffold.alteredAt ?: Long.MIN_VALUE)) {
                        null
                    } else {
                        scaffold.alteredAt
                    },
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        audit.record(
            SCAFFOLD, scaffold.id, AuditTrail.Action.SIGN_OFF, inspectorName,
            "${scaffold.reference} ${if (passed) "passed" else "failed"}",
        )
    }

    /**
     * Marks it altered, which takes it out of use until it is inspected again.
     *
     * The same call covers a storm. Both are the same fact — the scaffold is
     * no longer the one that was signed off — and offering two buttons for it
     * would mean somebody choosing the wrong one and getting a different
     * answer to "may I climb it".
     */
    suspend fun markAltered(scaffold: ScaffoldEntity, note: String?, actorName: String) {
        val now = System.currentTimeMillis()
        dao.upsert(
            scaffold.copy(
                alteredAt = now,
                notes = note?.trim()?.takeIf { it.isNotEmpty() } ?: scaffold.notes,
                updatedAt = now,
            ),
        )
        audit.record(SCAFFOLD, scaffold.id, AuditTrail.Action.UPDATE, actorName, "Altered")
    }

    suspend fun dismantle(scaffold: ScaffoldEntity, actorName: String) {
        val now = System.currentTimeMillis()
        dao.upsert(scaffold.copy(dismantledAt = now, updatedAt = now))
        audit.record(SCAFFOLD, scaffold.id, AuditTrail.Action.UPDATE, actorName, "Dismantled")
    }

    suspend fun remove(scaffold: ScaffoldEntity, actorName: String) {
        dao.delete(scaffold)
        audit.record(SCAFFOLD, scaffold.id, AuditTrail.Action.DELETE, actorName, scaffold.reference)
    }

    private companion object {
        const val SCAFFOLD = "scaffold"
    }
}
