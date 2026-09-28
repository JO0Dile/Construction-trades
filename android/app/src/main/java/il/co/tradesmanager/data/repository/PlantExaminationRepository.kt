package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.safety.Examinations
import il.co.tradesmanager.data.local.dao.PlantExaminationDao
import il.co.tradesmanager.data.local.entity.PlantExaminationEntity
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Plant examination certificates, with the rules in
 * `core.safety.Examinations` applied on the write.
 *
 * [takeOutOfService] is how a failed examination reaches the machine: it is
 * set to maintenance through the plant register itself, so the register, the
 * dashboard and the audit trail all say the same thing, as a defect found on
 * the morning walk-round does.
 */
class PlantExaminationRepository(
    private val dao: PlantExaminationDao,
    private val audit: AuditTrail,
    private val takeOutOfService: suspend (equipmentId: String, byName: String) -> Unit,
) {

    enum class Refusal {
        NOT_ALLOWED,
        NO_EXAMINER,
        EXAMINED_IN_FUTURE,
        DUE_BEFORE_EXAMINED,
        FAILED_WITHOUT_REASON,
        UNKNOWN,
    }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    fun observeLatest(): Flow<List<PlantExaminationEntity>> = dao.observeLatest()

    fun observeFor(equipmentId: String): Flow<List<PlantExaminationEntity>> = dao.observeFor(equipmentId)

    suspend fun record(
        role: Role,
        equipmentId: String,
        equipmentName: String,
        examinedOn: LocalDate,
        examinerName: String,
        certificateNumber: String?,
        result: Examinations.Result,
        nextDueOn: LocalDate?,
        notes: String,
        byName: String,
        today: LocalDate = LocalDate.now(),
        now: Long = System.currentTimeMillis(),
    ): Result<PlantExaminationEntity> {
        if (!mayWrite(role)) return Result.failure(Refused(Refusal.NOT_ALLOWED))
        Examinations.refusal(examinerName, examinedOn, nextDueOn, result, notes, today)?.let {
            return Result.failure(Refused(it.asRefusal()))
        }
        val examination = PlantExaminationEntity(
            id = UUID.randomUUID().toString(),
            equipmentId = equipmentId,
            examinedOnDay = examinedOn.toEpochDay(),
            examinerName = examinerName.trim(),
            certificateNumber = certificateNumber?.trim()?.ifBlank { null },
            result = result.name,
            // A failed machine has no next date: it has to pass first.
            nextDueDay = if (result == Examinations.Result.PASSED) nextDueOn?.toEpochDay() else null,
            notes = notes.trim().ifBlank { null },
            recordedByName = byName,
            recordedAt = now,
        )
        return runCatching {
            dao.upsert(examination)
            audit.record(
                ENTITY, equipmentId, AuditTrail.Action.CREATE, byName,
                Summary.of(
                    if (result == Examinations.Result.PASSED) Summaries.PE_PASSED else Summaries.PE_FAILED,
                    equipmentName,
                    examination.examinerName,
                ),
            )
            if (result == Examinations.Result.FAILED) takeOutOfService(equipmentId, byName)
            examination
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    private fun Examinations.Refusal.asRefusal(): Refusal = when (this) {
        Examinations.Refusal.NO_EXAMINER -> Refusal.NO_EXAMINER
        Examinations.Refusal.EXAMINED_IN_FUTURE -> Refusal.EXAMINED_IN_FUTURE
        Examinations.Refusal.DUE_BEFORE_EXAMINED -> Refusal.DUE_BEFORE_EXAMINED
        Examinations.Refusal.FAILED_WITHOUT_REASON -> Refusal.FAILED_WITHOUT_REASON
    }

    companion object {
        /** Whoever keeps the plant register records its certificates. */
        fun mayWrite(role: Role): Boolean = role.canWrite(Lens.STUFF)

        fun mayRead(role: Role): Boolean = role.canRead(Lens.STUFF)

        /** Filed against the machine, so its trail reads as one story. */
        private const val ENTITY = "equipment"
    }
}
