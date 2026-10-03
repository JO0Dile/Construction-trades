package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.safety.WeeklySafety
import il.co.tradesmanager.data.local.dao.SafetyReportDao
import java.time.LocalDate
import java.time.ZoneId

/** The weekly safety report, counted from the registers each time it is asked for. */
class SafetyReportRepository(private val dao: SafetyReportDao) {

    /** Null for a role that may not read the site's record: the figures are not counted for it at all. */
    suspend fun week(
        role: Role,
        projectId: String,
        weekStart: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): WeeklySafety.Report? {
        if (!mayRead(role)) return null
        val window = WeeklySafety.window(weekStart, zone)
        val from = window.first
        val to = window.last
        return WeeklySafety.Report(
            peopleOnSite = dao.peopleOnSite(projectId, from, to),
            visitors = dao.visitors(projectId, from, to),
            talksHeld = dao.talksHeld(projectId, from, to),
            permitsIssued = dao.permitsIssued(projectId, from, to),
            nearMisses = dao.nearMisses(projectId, from, to),
            incidents = dao.incidents(projectId, from, to),
            violations = dao.violations(projectId, from, to),
            inspectionsPassed = dao.inspectionsPassed(projectId, from, to),
            inspectionsFailed = dao.inspectionsFailed(projectId, from, to),
            fireFaults = dao.fireFaults(projectId, from, to),
            complaintsReceived = dao.complaintsReceived(projectId, from, to),
        )
    }

    companion object {
        fun mayRead(role: Role): Boolean = role.canRead(Lens.EVIDENCE)
    }
}
