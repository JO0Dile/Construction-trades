package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.safety.EmergencySheet
import il.co.tradesmanager.data.local.dao.JobEmergencyDao
import il.co.tradesmanager.data.local.entity.JobEmergencyEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Each job's emergency sheet. Replaced whole when it is saved, and the audit
 * trail keeps who saved it and how much was still missing.
 */
class EmergencySheetRepository(
    private val dao: JobEmergencyDao,
    private val audit: AuditTrail,
) {

    enum class Refusal { NOT_ALLOWED, NOT_A_PHONE_NUMBER, UNKNOWN }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    private val lock = Mutex()

    fun observe(projectId: String): Flow<JobEmergencyEntity?> = dao.observe(projectId)

    suspend fun sheet(projectId: String): JobEmergencyEntity? = dao.sheet(projectId)

    suspend fun save(
        role: Role,
        projectId: String,
        sheet: EmergencySheet.Sheet,
        byName: String,
        now: Long = System.currentTimeMillis(),
    ): Result<JobEmergencyEntity> = lock.withLock {
        if (!mayWrite(role)) return@withLock Result.failure(Refused(Refusal.NOT_ALLOWED))
        if (EmergencySheet.refusal(sheet) != null) return@withLock Result.failure(Refused(Refusal.NOT_A_PHONE_NUMBER))
        runCatching {
            val saved = JobEmergencyEntity(
                projectId = projectId,
                hospitalName = sheet.hospitalName.tidy(),
                hospitalAddress = sheet.hospitalAddress.tidy(),
                hospitalPhone = sheet.hospitalPhone.tidy(),
                assemblyPoint = sheet.assemblyPoint.tidy(),
                firstAiders = sheet.firstAiders.tidy(),
                siteContactName = sheet.siteContactName.tidy(),
                siteContactPhone = sheet.siteContactPhone.tidy(),
                electricityShutOff = sheet.electricityShutOff.tidy(),
                waterShutOff = sheet.waterShutOff.tidy(),
                gasShutOff = sheet.gasShutOff.tidy(),
                notes = sheet.notes.tidy(),
                updatedAt = now,
                updatedByName = byName,
            )
            dao.upsert(saved)
            audit.record(
                ENTITY, projectId, AuditTrail.Action.UPDATE, byName,
                Summary.of(Summaries.ES_SAVED, EmergencySheet.missing(sheetOf(saved)).size.toString()),
            )
            saved
        }.recoverCatching { throw Refused(Refusal.UNKNOWN) }
    }

    private fun String.tidy(): String? = trim().ifBlank { null }

    companion object {
        /**
         * Everybody on the job reads it: it is the sheet on the site office
         * wall, and the labourer who finds somebody on the ground needs it
         * more than the office does.
         */
        @Suppress("UNUSED_PARAMETER")
        fun mayRead(role: Role): Boolean = true

        /** Kept by whoever keeps the site's safety record. */
        fun mayWrite(role: Role): Boolean = role.canWrite(Lens.EVIDENCE)

        /** The stored row as the fields a person edits; blank where nothing is stored. */
        fun sheetOf(row: JobEmergencyEntity?): EmergencySheet.Sheet? = row?.let {
            EmergencySheet.Sheet(
                hospitalName = it.hospitalName.orEmpty(),
                hospitalAddress = it.hospitalAddress.orEmpty(),
                hospitalPhone = it.hospitalPhone.orEmpty(),
                assemblyPoint = it.assemblyPoint.orEmpty(),
                firstAiders = it.firstAiders.orEmpty(),
                siteContactName = it.siteContactName.orEmpty(),
                siteContactPhone = it.siteContactPhone.orEmpty(),
                electricityShutOff = it.electricityShutOff.orEmpty(),
                waterShutOff = it.waterShutOff.orEmpty(),
                gasShutOff = it.gasShutOff.orEmpty(),
                notes = it.notes.orEmpty(),
            )
        }

        private const val ENTITY = "job_emergency"
    }
}
