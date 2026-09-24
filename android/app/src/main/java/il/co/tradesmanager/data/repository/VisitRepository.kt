package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.safety.Visits
import il.co.tradesmanager.data.local.dao.VisitDao
import il.co.tradesmanager.data.local.entity.SiteVisitEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * A job's visitor log, with the rules in `core.safety.Visits` applied on the
 * write rather than trusted from the screen.
 */
class VisitRepository(
    private val dao: VisitDao,
    private val audit: AuditTrail,
) {

    enum class Refusal {
        /** Not somebody who writes the site's records. */
        NOT_ALLOWED,
        BLANK_NAME,
        ALREADY_LEFT,

        /** Something other than a rule: a full disk, a visit that is not there. */
        UNKNOWN,
    }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    fun observeForProject(projectId: String): Flow<List<SiteVisitEntity>> =
        dao.observeForProject(projectId)

    suspend fun signIn(
        role: Role,
        projectId: String,
        companyId: String?,
        name: String,
        organisation: String?,
        phone: String?,
        hostName: String?,
        briefed: Boolean,
        signature: String?,
        byAccountId: String?,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<SiteVisitEntity> {
        if (!mayWrite(role)) return Result.failure(Refused(Refusal.NOT_ALLOWED))
        Visits.refusal(name)?.let { return Result.failure(Refused(Refusal.BLANK_NAME)) }
        val visit = SiteVisitEntity(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            companyId = companyId,
            name = name.trim(),
            organisation = organisation.tidy(),
            phone = phone.tidy(),
            hostName = hostName.tidy(),
            briefed = briefed,
            signature = signature?.takeIf { it.isNotBlank() },
            arrivedAt = now,
            signedInByAccountId = byAccountId,
            signedInByName = byName,
        )
        return runCatching {
            dao.upsert(visit)
            audit.record(
                ENTITY, visit.id, AuditTrail.Action.CREATE, byName,
                Summary.of(Summaries.VISITOR_SIGNED_IN, visit.name),
            )
            visit
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** They have gone. Only ever once: the time they left is the time they left. */
    suspend fun signOut(
        role: Role,
        visitId: String,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<SiteVisitEntity> {
        if (!mayWrite(role)) return Result.failure(Refused(Refusal.NOT_ALLOWED))
        val visit = dao.visit(visitId) ?: return Result.failure(Refused(Refusal.UNKNOWN))
        Visits.signOutRefusal(visit.leftAt)?.let { return Result.failure(Refused(Refusal.ALREADY_LEFT)) }
        val updated = visit.copy(leftAt = now)
        return runCatching {
            dao.upsert(updated)
            audit.record(
                ENTITY, visit.id, AuditTrail.Action.UPDATE, byName,
                Summary.of(Summaries.VISITOR_SIGNED_OUT, visit.name),
            )
            updated
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    private fun String?.tidy(): String? = this?.trim()?.ifBlank { null }

    companion object {
        fun mayWrite(role: Role): Boolean = role.canWrite(Lens.EVIDENCE)

        private const val ENTITY = "site_visit"
    }
}
