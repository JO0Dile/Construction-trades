package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.evidence.DailyLog
import il.co.tradesmanager.data.local.dao.DailyLogDao
import il.co.tradesmanager.data.local.entity.DailyLogEntity
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * The daily site log.
 *
 * Two rules live here and nowhere else. A log is one per job per day, because
 * two accounts of a day are worse than none. And a signed log cannot be
 * touched — that is what signing means, and a daily log somebody can tidy up
 * after an accident is evidence of nothing.
 */
class DailyLogRepository(
    private val dao: DailyLogDao,
    private val audit: AuditTrail,
) {

    fun observeForProject(projectId: String): Flow<List<DailyLogEntity>> =
        dao.observeForProject(projectId)

    fun observe(id: String): Flow<DailyLogEntity?> = dao.observe(id)

    /**
     * The log for a day, opening one if nobody has yet.
     *
     * Opening it is not a decision anybody should have to make: the day
     * happened whether or not somebody pressed a button, and a manager sitting
     * down at five o'clock wants to fill one in, not create one first.
     */
    suspend fun openFor(projectId: String, logDate: Long, actorName: String): DailyLogEntity {
        dao.forDay(projectId, logDate)?.let { return it }
        val now = System.currentTimeMillis()
        val log = DailyLogEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            logDate = logDate,
            status = DailyLog.Status.DRAFT,
            weather = null,
            workforceCount = null,
            notes = null,
            preparedByName = actorName,
            preparedById = null,
            signature = null,
            signedAt = null,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(log)
        return log
    }

    /** What the app watched happen that day, counted rather than remembered. */
    suspend fun summarise(
        projectId: String,
        logDate: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): DailyLog.Summary {
        val window = DailyLog.windowFor(logDate, zone)
        val from = window.first
        val to = window.last
        return DailyLog.Summary(
            tasksCompleted = dao.tasksCompleted(projectId, from, to),
            deliveries = dao.deliveries(projectId, from, to),
            permitsIssued = dao.permitsIssued(projectId, from, to),
            talksHeld = dao.talksHeld(projectId, from, to),
            snagsRaised = dao.snagsRaised(projectId, from, to),
            incidents = dao.incidents(projectId, from, to),
        )
    }

    /** Refused once the log is signed. Returns false rather than throwing. */
    suspend fun update(
        logId: String,
        weather: String?,
        workforceCount: Int?,
        notes: String?,
    ): Boolean {
        val log = dao.log(logId) ?: return false
        if (!DailyLog.canEdit(log.status)) return false
        dao.upsert(
            log.copy(
                weather = weather?.trim()?.takeIf { it.isNotEmpty() },
                workforceCount = workforceCount?.takeIf { it >= 0 },
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    /**
     * Signs the day off. From here the log is a record and stops being a
     * document — [update] refuses, and there is no path back.
     */
    suspend fun sign(logId: String, signerName: String, signature: String): Boolean {
        val log = dao.log(logId) ?: return false
        if (!DailyLog.canSign(log.status, signature)) return false
        val now = System.currentTimeMillis()
        dao.upsert(
            log.copy(
                status = DailyLog.Status.SIGNED,
                preparedByName = signerName.trim().ifEmpty { log.preparedByName },
                signature = signature,
                signedAt = now,
                updatedAt = now,
            ),
        )
        audit.record(ENTITY, logId, AuditTrail.Action.SIGN_OFF, signerName, "Daily log signed")
        return true
    }

    private companion object {
        const val ENTITY = "daily_log"
    }
}
