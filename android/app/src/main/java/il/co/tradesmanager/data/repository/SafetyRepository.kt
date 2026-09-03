package il.co.tradesmanager.data.repository

import il.co.tradesmanager.data.local.dao.CatalogDao
import il.co.tradesmanager.data.local.dao.SafetyDao
import il.co.tradesmanager.data.local.entity.ChecklistRunEntity
import il.co.tradesmanager.data.local.entity.ChecklistRunItemEntity
import il.co.tradesmanager.data.local.entity.ChecklistTemplateEntity
import il.co.tradesmanager.data.local.entity.ChecklistTemplateItemEntity
import il.co.tradesmanager.data.local.entity.IncidentEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class SafetyRepository(
    private val safetyDao: SafetyDao,
    private val catalogDao: CatalogDao,
    private val audit: AuditTrail,
) {

    object State {
        const val PASS = "PASS"
        const val FAIL = "FAIL"
        const val NOT_APPLICABLE = "NOT_APPLICABLE"
    }

    fun observeTemplates(tradeIds: List<String>): Flow<List<ChecklistTemplateEntity>> =
        catalogDao.observeChecklistTemplates(tradeIds)

    suspend fun templateItems(templateId: String): List<ChecklistTemplateItemEntity> =
        catalogDao.checklistTemplateItems(templateId)

    fun observeRun(runId: String): Flow<ChecklistRunEntity?> = safetyDao.observeRun(runId)

    fun observeRunItems(runId: String): Flow<List<ChecklistRunItemEntity>> = safetyDao.observeRunItems(runId)

    fun observeRuns(projectId: String? = null): Flow<List<ChecklistRunEntity>> = safetyDao.observeRuns(projectId)

    suspend fun startRun(templateId: String, projectId: String?, actorName: String): ChecklistRunEntity {
        val run = ChecklistRunEntity(
            id = UUID.randomUUID().toString(),
            templateId = templateId,
            projectId = projectId,
            startedAt = System.currentTimeMillis(),
            blocked = true,
        )
        safetyDao.upsertRun(run)
        audit.record(ENTITY, run.id, AuditTrail.Action.CREATE, actorName, "Started checklist $templateId")
        return run
    }

    suspend fun answer(runId: String, templateItemId: String, state: String, note: String?) {
        safetyDao.upsertRunItem(
            ChecklistRunItemEntity(
                // Deterministic id: answering the same check twice replaces the
                // answer instead of leaving two contradictory rows behind.
                id = "$runId:$templateItemId",
                runId = runId,
                templateItemId = templateItemId,
                state = state,
                note = note?.takeIf { it.isNotBlank() },
                answeredAt = System.currentTimeMillis(),
            ),
        )
        refreshBlockedState(runId)
    }

    /**
     * Recomputes whether the run may be signed.
     *
     * A run is blocked while any critical check is unanswered or failed. This
     * is derived from the answers each time rather than tracked as a flag the
     * UI sets, so no screen can accidentally clear it.
     */
    suspend fun refreshBlockedState(runId: String): Boolean {
        val run = safetyDao.run(runId) ?: return true
        val criticalIds = catalogDao.checklistTemplateItems(run.templateId)
            .filter { it.critical }
            .map { it.id }
            .toSet()
        val answers = safetyDao.runItems(runId).associateBy { it.templateItemId }
        val blocked = criticalIds.any { id ->
            val state = answers[id]?.state
            state == null || state == State.FAIL
        }
        if (blocked != run.blocked) safetyDao.upsertRun(run.copy(blocked = blocked))
        return blocked
    }

    /**
     * Signs a completed checklist. Returns false, changing nothing, when a
     * critical check is outstanding — the regulation the checklist encodes is
     * not something a signature is allowed to override.
     */
    suspend fun signOff(runId: String, signerName: String, signatureStrokes: String?): Boolean {
        if (refreshBlockedState(runId)) return false
        val run = safetyDao.run(runId) ?: return false
        safetyDao.upsertRun(
            run.copy(
                completedAt = System.currentTimeMillis(),
                signedByName = signerName,
                signatureStrokes = signatureStrokes,
                blocked = false,
            ),
        )
        audit.record(ENTITY, runId, AuditTrail.Action.SIGN_OFF, signerName, "Checklist signed")
        return true
    }

    suspend fun reportIncident(incident: IncidentEntity) {
        safetyDao.upsertIncident(incident)
        audit.record(
            "incident", incident.id, AuditTrail.Action.CREATE, incident.reportedByName,
            incident.description.take(120),
        )
    }

    fun observeIncidents(): Flow<List<IncidentEntity>> = safetyDao.observeIncidents()

    private companion object {
        const val ENTITY = "checklist_run"
    }
}
