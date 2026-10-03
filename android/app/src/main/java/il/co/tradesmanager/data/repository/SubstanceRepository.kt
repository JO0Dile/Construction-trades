package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.safety.Substances
import il.co.tradesmanager.data.local.dao.SubstanceDao
import il.co.tradesmanager.data.local.entity.SubstanceEntity
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A job's hazardous substances register, with the rules in
 * `core.safety.Substances` applied on every write, under one lock for the
 * numbering and for a newer sheet racing the substance going off site.
 */
class SubstanceRepository(
    private val dao: SubstanceDao,
    private val audit: AuditTrail,
) {

    enum class Refusal {
        NOT_ALLOWED,
        BLANK_NAME,
        NO_HAZARD,
        BLANK_KEPT_WHERE,
        SHEET_IN_FUTURE,
        SHEET_NOT_NEWER,
        ALREADY_REMOVED,
        UNKNOWN,
    }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    /** What a person types when a substance comes onto the site. */
    data class Arrival(
        val name: String,
        val supplier: String,
        val hazards: Set<Substances.Hazard>,
        val keptWhere: String,
        val quantity: String,
        val precautions: String,
        val firstAid: String,
        val sheetOn: LocalDate?,
    )

    private val lock = Mutex()

    /** Every job's rows, recent first, for the search box. */
    suspend fun all(): List<SubstanceEntity> = dao.all()

    fun observeForProject(projectId: String): Flow<List<SubstanceEntity>> = dao.observeForProject(projectId)

    suspend fun add(
        role: Role,
        projectId: String,
        arrival: Arrival,
        byName: String,
        today: LocalDate = LocalDate.now(),
        now: Long = System.currentTimeMillis(),
    ): Result<SubstanceEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        Substances.addRefusal(arrival.name, arrival.hazards, arrival.keptWhere, arrival.sheetOn, today)?.let {
            return@withLock Result.failure(Refused(it.asRefusal()))
        }
        runCatching {
            val substance = SubstanceEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                reference = Substances.reference(dao.countForProject(projectId)),
                name = arrival.name.trim(),
                supplier = arrival.supplier.trim().ifBlank { null },
                hazards = Substances.encode(arrival.hazards),
                keptWhere = arrival.keptWhere.trim(),
                quantity = arrival.quantity.trim().ifBlank { null },
                precautions = arrival.precautions.trim().ifBlank { null },
                firstAid = arrival.firstAid.trim().ifBlank { null },
                sheetOnDay = arrival.sheetOn?.toEpochDay(),
                addedAt = now,
                addedByName = byName,
            )
            dao.upsert(substance)
            audit.record(
                ENTITY, substance.id, AuditTrail.Action.CREATE, byName,
                Summary.of(Summaries.HS_ADDED, substance.reference, substance.name),
            )
            substance
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** The supplier sent the current sheet; its date replaces the one on file. */
    suspend fun newSheet(
        role: Role,
        substanceId: String,
        sheetOn: LocalDate,
        byName: String,
        today: LocalDate = LocalDate.now(),
    ): Result<SubstanceEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val substance = dao.substance(substanceId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        Substances.sheetRefusal(
            sheetOn = sheetOn,
            current = substance.sheetOnDay?.let(LocalDate::ofEpochDay),
            removed = substance.removedAt != null,
            today = today,
        )?.let { return@withLock Result.failure(Refused(it.asRefusal())) }
        val updated = substance.copy(sheetOnDay = sheetOn.toEpochDay())
        runCatching {
            dao.upsert(updated)
            audit.record(
                ENTITY, substance.id, AuditTrail.Action.UPDATE, byName,
                Summary.of(Summaries.HS_SHEET, substance.reference, sheetOn.toString()),
            )
            updated
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    /** Used up or taken away. The row stays: what was here last month is still asked about. */
    suspend fun remove(
        role: Role,
        substanceId: String,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<SubstanceEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        val substance = dao.substance(substanceId) ?: return@withLock Result.failure(Refused(Refusal.UNKNOWN))
        Substances.removeRefusal(substance.removedAt != null)?.let { return@withLock Result.failure(Refused(it.asRefusal())) }
        val removed = substance.copy(removedAt = now, removedByName = byName)
        runCatching {
            dao.upsert(removed)
            audit.record(ENTITY, substance.id, AuditTrail.Action.UPDATE, byName, Summary.of(Summaries.HS_REMOVED, substance.reference))
            removed
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    private fun Substances.Refusal.asRefusal(): Refusal = when (this) {
        Substances.Refusal.BLANK_NAME -> Refusal.BLANK_NAME
        Substances.Refusal.NO_HAZARD -> Refusal.NO_HAZARD
        Substances.Refusal.BLANK_KEPT_WHERE -> Refusal.BLANK_KEPT_WHERE
        Substances.Refusal.SHEET_IN_FUTURE -> Refusal.SHEET_IN_FUTURE
        Substances.Refusal.SHEET_NOT_NEWER -> Refusal.SHEET_NOT_NEWER
        Substances.Refusal.ALREADY_REMOVED -> Refusal.ALREADY_REMOVED
    }

    companion object {
        /** The site's safety record, kept by whoever keeps the rest of it. */
        fun mayWrite(role: Role): Boolean = role.canWrite(Lens.EVIDENCE)

        fun mayRead(role: Role): Boolean = role.canRead(Lens.EVIDENCE)

        private const val ENTITY = "substance"
    }
}
