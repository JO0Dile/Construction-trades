package il.co.tradesmanager.core.safety

import java.time.LocalDate
import java.time.ZoneId

/**
 * One job's week of safety, counted from its registers: the page a safety
 * officer signs off on a Friday and the one an inspector asks to see for
 * the week something happened.
 *
 * The week is the Israeli working week, Sunday to Saturday. Every figure is
 * counted from what was recorded, never typed, so the report and the
 * registers cannot disagree -- a near miss not written down is not in it,
 * which is the argument for writing near misses down.
 */
object WeeklySafety {

    /** The Sunday on or before [day]. */
    fun weekStarting(day: LocalDate): LocalDate = day.minusDays((day.dayOfWeek.value % 7).toLong())

    /** From Sunday's first moment up to, not including, the next Sunday's, on the site's clock. */
    fun window(weekStart: LocalDate, zone: ZoneId): LongRange {
        val start = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = weekStart.plusDays(DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
        return start until end
    }

    data class Report(
        /** Different people with a shift on the job that week. */
        val peopleOnSite: Int = 0,
        val visitors: Int = 0,
        val talksHeld: Int = 0,
        val permitsIssued: Int = 0,
        val nearMisses: Int = 0,
        /** Incidents where somebody was hurt or something damaged: everything that is not a near miss. */
        val incidents: Int = 0,
        val violations: Int = 0,
        val inspectionsPassed: Int = 0,
        val inspectionsFailed: Int = 0,
        val fireFaults: Int = 0,
        val complaintsReceived: Int = 0,
    ) {
        /** A week with no incident, no violation and no failure recorded. Said plainly on the report. */
        val nothingWentWrong: Boolean
            get() = incidents == 0 && violations == 0 && inspectionsFailed == 0 && fireFaults == 0
    }

    const val DAYS = 7L
}
