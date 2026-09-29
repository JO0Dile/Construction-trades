package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.safety.WeeklySafety
import il.co.tradesmanager.data.local.dao.SafetyReportDao
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Answers every count with a fixed number, and remembers the window it was asked about. */
private class FakeSafetyReportDao : SafetyReportDao {
    var from = 0L
    var to = 0L
    private fun seen(f: Long, t: Long, answer: Int): Int {
        from = f
        to = t
        return answer
    }
    override suspend fun peopleOnSite(projectId: String, from: Long, to: Long) = seen(from, to, 12)
    override suspend fun visitors(projectId: String, from: Long, to: Long) = seen(from, to, 3)
    override suspend fun talksHeld(projectId: String, from: Long, to: Long) = seen(from, to, 2)
    override suspend fun permitsIssued(projectId: String, from: Long, to: Long) = seen(from, to, 4)
    override suspend fun nearMisses(projectId: String, from: Long, to: Long) = seen(from, to, 1)
    override suspend fun incidents(projectId: String, from: Long, to: Long) = seen(from, to, 0)
    override suspend fun violations(projectId: String, from: Long, to: Long) = seen(from, to, 0)
    override suspend fun inspectionsPassed(projectId: String, from: Long, to: Long) = seen(from, to, 5)
    override suspend fun inspectionsFailed(projectId: String, from: Long, to: Long) = seen(from, to, 0)
    override suspend fun fireFaults(projectId: String, from: Long, to: Long) = seen(from, to, 0)
    override suspend fun complaintsReceived(projectId: String, from: Long, to: Long) = seen(from, to, 1)
}

class SafetyReportRepositoryTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val sunday = LocalDate.of(2026, 10, 4)

    @Test
    fun `the week is counted over its own window, every register once`() = runTest {
        val dao = FakeSafetyReportDao()
        val report = SafetyReportRepository(dao).week(Role.SAFETY_OFFICER, "job.1", sunday, zone)!!
        assertEquals(
            WeeklySafety.Report(
                peopleOnSite = 12, visitors = 3, talksHeld = 2, permitsIssued = 4, nearMisses = 1,
                inspectionsPassed = 5, complaintsReceived = 1,
            ),
            report,
        )
        assertEquals(WeeklySafety.window(sunday, zone).first, dao.from)
        assertEquals(WeeklySafety.window(sunday, zone).last, dao.to)
    }

    @Test
    fun `only a role that reads the site's record is counted anything`() = runTest {
        val repo = SafetyReportRepository(FakeSafetyReportDao())
        Role.entries.forEach { role ->
            val report = repo.week(role, "job.1", sunday, zone)
            if (role.canRead(Lens.EVIDENCE)) assertNotNull(role.name, report) else assertNull(role.name, report)
        }
    }
}
