package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.safety.SafetyStats
import il.co.tradesmanager.data.local.dao.SafetyStatsDao
import java.time.ZoneId

/** The company's safety statistics, counted from the registers each time they are asked for. */
class SafetyStatsRepository(private val dao: SafetyStatsDao) {

    /**
     * The sheet for [jobs] -- the company's own, which the caller passes --
     * over [span]. Null for a role that may not read the site's record: the
     * figures are not counted for it at all.
     */
    suspend fun sheet(
        role: Role,
        jobs: List<SafetyStats.Job>,
        span: SafetyStats.Span,
        zone: ZoneId = ZoneId.systemDefault(),
    ): SafetyStats.Sheet? {
        if (!mayRead(role)) return null
        if (jobs.isEmpty()) return SafetyStats.Sheet(rows = emptyList(), total = SafetyStats.Figures())
        val window = SafetyStats.window(span, zone)
        val from = window.first
        val to = window.last + 1
        // SQLite caps the number of values one query may be handed, so a
        // company with a great many jobs is asked about them in batches.
        val batches = jobs.map { it.id }.distinct().chunked(BATCH)
        val shifts = batches.flatMap { ids -> dao.shifts(ids, from, to) }
            .mapNotNull { shift -> shift.checkOutAt?.let { SafetyStats.Shift(shift.projectId, shift.checkInAt, it) } }
        val incidents = batches.flatMap { ids -> dao.incidents(ids, from, to) }
            .map { SafetyStats.Tally(it.projectId, it.kind, it.total) }
        val violations = batches.flatMap { ids -> dao.violations(ids, from, to) }
            .map { SafetyStats.Tally(it.projectId, null, it.total) }
        val talks = batches.flatMap { ids -> dao.talks(ids, from, to) }
            .map { SafetyStats.Tally(it.projectId, null, it.total) }
        val lastSerious = batches.mapNotNull { ids -> dao.lastSerious(ids) }.maxOrNull()
        return SafetyStats.sheetOf(jobs, window, shifts, incidents, violations, talks, lastSerious)
    }

    companion object {
        /** Well under the 999 values the oldest SQLite on a supported phone accepts. */
        const val BATCH = 500

        fun mayRead(role: Role): Boolean = role.canRead(Lens.EVIDENCE)
    }
}
