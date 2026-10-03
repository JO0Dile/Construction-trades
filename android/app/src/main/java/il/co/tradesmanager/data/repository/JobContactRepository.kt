package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.work.Contacts
import il.co.tradesmanager.data.local.dao.JobContactDao
import il.co.tradesmanager.data.local.entity.JobContactEntity
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A job's contacts, with the rules in `core.work.Contacts` applied on every write. */
class JobContactRepository(
    private val dao: JobContactDao,
    private val audit: AuditTrail,
) {

    enum class Refusal {
        NOT_ALLOWED,
        BLANK_NAME,
        NO_WAY_TO_REACH,
        NOT_A_PHONE_NUMBER,
        NOT_AN_EMAIL,
        ALREADY_REMOVED,
        UNKNOWN,
    }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    /** What a person types for one entry: the same fields when it is added and when it is corrected. */
    data class Entry(
        val name: String,
        val organisation: String,
        val kind: Contacts.Kind,
        val phone: String,
        val email: String,
        val notes: String,
    )

    private val lock = Mutex()

    fun observeForProject(projectId: String): Flow<List<JobContactEntity>> = dao.observeForProject(projectId)

    /** Every job's entries, recent first, for the search box. */
    suspend fun all(): List<JobContactEntity> = dao.all()

    suspend fun add(
        role: Role,
        projectId: String,
        entry: Entry,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<JobContactEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        Contacts.refusal(entry.name, entry.phone, entry.email)?.let { return@withLock Result.failure(Refused(it.asRefusal())) }
        runCatching {
            val contact = JobContactEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                name = entry.name.trim(),
                organisation = entry.organisation.trim().ifBlank { null },
                kind = entry.kind.name,
                phone = entry.phone.trim().ifBlank { null },
                email = entry.email.trim().ifBlank { null },
                notes = entry.notes.trim().ifBlank { null },
                addedAt = now,
                addedByName = byName,
                updatedAt = now,
            )
            dao.upsert(contact)
            audit.record(ENTITY, contact.id, AuditTrail.Action.CREATE, byName, Summary.of(Summaries.JC_ADDED, contact.name))
            contact
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** A new number, a new firm, a spelling put right. */
    suspend fun correct(
        role: Role,
        contactId: String,
        entry: Entry,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<JobContactEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val contact = dao.contact(contactId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        if (contact.removedAt != null) return@withLock Result.failure(Refused(Refusal.ALREADY_REMOVED))
        Contacts.refusal(entry.name, entry.phone, entry.email)?.let { return@withLock Result.failure(Refused(it.asRefusal())) }
        val corrected = contact.copy(
            name = entry.name.trim(),
            organisation = entry.organisation.trim().ifBlank { null },
            kind = entry.kind.name,
            phone = entry.phone.trim().ifBlank { null },
            email = entry.email.trim().ifBlank { null },
            notes = entry.notes.trim().ifBlank { null },
            updatedAt = now,
        )
        runCatching {
            dao.upsert(corrected)
            audit.record(ENTITY, contact.id, AuditTrail.Action.UPDATE, byName, Summary.of(Summaries.JC_CORRECTED, corrected.name))
            corrected
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** No longer on the job. Kept, marked off. */
    suspend fun remove(
        role: Role,
        contactId: String,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<JobContactEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val contact = dao.contact(contactId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        if (contact.removedAt != null) return@withLock Result.failure(Refused(Refusal.ALREADY_REMOVED))
        val removed = contact.copy(removedAt = now, removedByName = byName, updatedAt = now)
        runCatching {
            dao.upsert(removed)
            audit.record(ENTITY, contact.id, AuditTrail.Action.UPDATE, byName, Summary.of(Summaries.JC_REMOVED, contact.name))
            removed
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    private fun Contacts.Refusal.asRefusal(): Refusal = when (this) {
        Contacts.Refusal.BLANK_NAME -> Refusal.BLANK_NAME
        Contacts.Refusal.NO_WAY_TO_REACH -> Refusal.NO_WAY_TO_REACH
        Contacts.Refusal.NOT_A_PHONE_NUMBER -> Refusal.NOT_A_PHONE_NUMBER
        Contacts.Refusal.NOT_AN_EMAIL -> Refusal.NOT_AN_EMAIL
        Contacts.Refusal.ALREADY_REMOVED -> Refusal.ALREADY_REMOVED
    }

    companion object {
        /** Kept by whoever keeps the plan. */
        fun mayWrite(role: Role): Boolean = role.canWrite(Lens.PLAN)

        /** Read by whoever works on the plan or keeps the site's record: the supervisor and the inspector are both. */
        fun mayRead(role: Role): Boolean = role.canRead(Lens.PLAN) || role.canRead(Lens.EVIDENCE)

        private const val ENTITY = "job_contact"
    }
}
