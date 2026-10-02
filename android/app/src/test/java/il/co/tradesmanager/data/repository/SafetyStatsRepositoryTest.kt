package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.safety.SafetyStats
import il.co.tradesmanager.data.local.dao.JobCount
import il.co.tradesmanager.data.local.dao.JobTally
import il.co.tradesmanager.data.local.dao.SafetyStatsDao
import il.co.tradesmanager.data.local.dao.ShiftSpan
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Answers from fixed lists, filtered the way the queries are, and remembers what it was asked. */
private class FakeSafetyStatsDao(
    private val shiftRows: List<ShiftSpan> = emptyList(),
    private val incidentRows: List<JobTally> = emptyList(),
    private val lastSeriousByJob: Map<String, Long> = emptyMap(),
) : SafetyStatsDao {
    val batches = mutableListOf<List<String>>()
    var from = 0L
    var to = 0L

    override suspend fun shifts(projectIds: List<String>, from: Long, to: Long): List<ShiftSpan> {
        batches += projectIds
        this.from = from
        this.to = to
        return shiftRows.filter { it.projectId in projectIds && (it.checkOutAt ?: Long.MIN_VALUE) > from && it.checkInAt < to }
    }

    override suspend fun incidents(projectIds: List<String>, from: Long, to: Long) = incidentRows.filter { it.projectId in projectIds }
    override suspend fun violations(projectIds: List<String>, from: Long, to: Long) = emptyList<JobCount>()
    override suspend fun talks(projectIds: List<String>, from: Long, to: Long) = listOf(JobCount(projectIds.first(), 1))
    override suspend fun lastSerious(projectIds: List<String>): Long? = projectIds.mapNotNull { lastSeriousByJob[it] }.maxOrNull()
}

class SafetyStatsRepositoryTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val span = SafetyStats.Span(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
    private val window = SafetyStats.window(span, zone)
    private val hour = 3_600_000L

    @Test
    fun `only a role that reads the site's record is counted anything`() = runTest {
        val repo = SafetyStatsRepository(FakeSafetyStatsDao())
        Role.entries.forEach { role ->
            val sheet = repo.sheet(role, listOf(SafetyStats.Job("a", null)), span, zone)
            if (role.canRead(Lens.EVIDENCE)) assertNotNull(role.name, sheet) else assertNull(role.name, sheet)
        }
    }

    @Test
    fun `the window is the period's, with the end exclusive, and open shifts are not hours`() = runTest {
        val dao = FakeSafetyStatsDao(
            shiftRows = listOf(
                ShiftSpan("a", window.first + hour, window.first + 9 * hour),
                ShiftSpan("a", window.first + 20 * hour, null),
            ),
            incidentRows = listOf(JobTally("a", "MINOR", 1)),
            lastSeriousByJob = mapOf("a" to 5L),
        )
        val sheet = SafetyStatsRepository(dao).sheet(Role.SAFETY_OFFICER, listOf(SafetyStats.Job("a", null)), span, zone)!!
        assertEquals(window.first, dao.from)
        assertEquals(window.last + 1, dao.to)
        assertEquals(8 * hour, sheet.total.workedMillis)
        assertEquals(1, sheet.total.shifts)
        assertEquals(1, sheet.total.minorInjuries)
        assertEquals(5L, sheet.lastSeriousAt)
    }

    @Test
    fun `a great many jobs are asked about in batches, every one of them once`() = runTest {
        val jobs = (1..1_234).map { SafetyStats.Job("job.$it", null) }
        val dao = FakeSafetyStatsDao()
        val sheet = SafetyStatsRepository(dao).sheet(Role.OWNER, jobs, span, zone)!!
        assertTrue(dao.batches.all { it.size <= SafetyStatsRepository.BATCH })
        assertEquals(jobs.map { it.id }, dao.batches.flatten())
        // One talk answered per batch, each on a different job.
        assertEquals(dao.batches.size, sheet.total.talksHeld)
    }

    @Test
    fun `a company with no jobs has an empty sheet, not an error`() = runTest {
        val dao = FakeSafetyStatsDao()
        val sheet = SafetyStatsRepository(dao).sheet(Role.OWNER, emptyList(), span, zone)!!
        assertTrue(sheet.rows.isEmpty())
        assertTrue(dao.batches.isEmpty())
    }
}
