package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.safety.Heat
import il.co.tradesmanager.core.safety.Muster
import il.co.tradesmanager.data.local.dao.HeatDao
import il.co.tradesmanager.data.local.dao.ScheduleDao
import il.co.tradesmanager.data.local.entity.HeatCheckEntity
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

/**
 * Heat checks, from a reading typed at the site hut to the record.
 *
 * The rules in `core.safety.Heat` are applied again here rather than trusted
 * from the screen: whatever the screen showed, the band on the record is the
 * one the rules give for the numbers on the record.
 */
class HeatRepository(
    private val dao: HeatDao,
    private val schedule: ScheduleDao,
    private val audit: AuditTrail,
) {

    enum class Refusal {
        /** Not somebody who writes the safety register. */
        NOT_ALLOWED,

        /** A temperature that cannot be weather. */
        IMPLAUSIBLE_TEMPERATURE,

        /** A humidity outside nought to a hundred. */
        IMPLAUSIBLE_HUMIDITY,

        /**
         * Something other than a rule: a full disk, a database that would not
         * write. Named so a screen has a sentence for it instead of borrowing
         * one that blames the person's permissions.
         */
        UNKNOWN,
    }

    class Refused(val refusal: Refusal) : Exception(refusal.name)

    /** One firm's checks. Null is the one-man band, not everybody. */
    fun observeForCompany(companyId: String?): Flow<List<HeatCheckEntity>> =
        dao.observeForCompany(companyId)

    /**
     * Record a check.
     *
     * [measures] is what was done, and empty is accepted: see
     * [Heat.record] for why a check with nothing done is still a check.
     */
    suspend fun record(
        role: Role,
        companyId: String?,
        temperatureC: Double,
        humidityPercent: Double,
        inSun: Boolean,
        measures: Set<Heat.Measure>,
        note: String?,
        byAccountId: String,
        byName: String,
    ): Result<HeatCheckEntity> {
        if (!role.canWrite(Lens.EVIDENCE)) return Result.failure(Refused(Refusal.NOT_ALLOWED))
        val reading = when (val outcome = Heat.record(temperatureC, humidityPercent, inSun)) {
            is Heat.Outcome.Refused -> return Result.failure(
                Refused(
                    when (outcome.reason) {
                        Heat.Refusal.IMPLAUSIBLE_TEMPERATURE -> Refusal.IMPLAUSIBLE_TEMPERATURE
                        Heat.Refusal.IMPLAUSIBLE_HUMIDITY -> Refusal.IMPLAUSIBLE_HUMIDITY
                    },
                ),
            )
            is Heat.Outcome.Accepted -> outcome.reading
        }
        // The same answer a roll call gives to "which site is this": the one
        // job every open check-in agrees on, or none. Asked rather than
        // chosen, so the check is on the site the men are actually on.
        val site = Muster.siteOf(
            schedule.openCheckIns().map {
                Muster.OpenCheckIn(
                    personId = it.workerId,
                    name = it.workerName,
                    checkInAt = it.checkInAt,
                    projectId = it.projectId,
                )
            },
        )
        val check = HeatCheckEntity(
            id = UUID.randomUUID().toString(),
            projectId = site,
            companyId = companyId,
            checkedAt = System.currentTimeMillis(),
            temperatureC = reading.temperatureC,
            humidityPercent = reading.humidityPercent,
            inSun = reading.inSun,
            heatIndexC = reading.heatIndexC,
            band = reading.band.name,
            // In the order the rules list them, not the order they were ticked,
            // so two records of the same measures read the same.
            measures = Heat.Measure.entries.filter { it in measures }.map { it.name },
            note = note?.trim()?.ifBlank { null },
            checkedByAccountId = byAccountId,
            checkedByName = byName,
        )
        dao.upsert(check)
        audit.record(
            entityType = ENTITY,
            entityId = check.id,
            action = AuditTrail.Action.CREATE,
            actorName = byName,
            summary = Summary.of(
                Summaries.HEAT_CHECKED,
                reading.judgedOnC.roundToInt().toString(),
                Summary.nest(bandKey(reading.band)),
            ),
        )
        return Result.success(check)
    }

    companion object {
        /** The string a band is shown with, and the key an audit summary nests. */
        fun bandKey(band: Heat.Band): String = when (band) {
            Heat.Band.NONE -> "heat_band_none"
            Heat.Band.CAUTION -> "heat_band_caution"
            Heat.Band.EXTREME_CAUTION -> "heat_band_extreme_caution"
            Heat.Band.DANGER -> "heat_band_danger"
            Heat.Band.EXTREME_DANGER -> "heat_band_extreme_danger"
        }

        private const val ENTITY = "heat_check"
    }
}
