package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.evidence.NonConformances
import il.co.tradesmanager.data.local.dao.NonConformanceDao
import il.co.tradesmanager.data.local.entity.NonConformanceEntity
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The register, in memory. */
private class FakeNonConformanceDao : NonConformanceDao {
    val rows = MutableStateFlow<List<NonConformanceEntity>>(emptyList())

    override suspend fun upsert(report: NonConformanceEntity) {
        rows.value = rows.value.filterNot { it.id == report.id } + report
    }

    override suspend fun report(id: String) = rows.value.firstOrNull { it.id == id }
    override fun observeForProject(projectId: String): Flow<List<NonConformanceEntity>> =
        rows.map { all -> all.filter { it.projectId == projectId }.sortedBy { it.raisedAt } }
    override suspend fun countForProject(projectId: String) = rows.value.count { it.projectId == projectId }
    override suspend fun all() = rows.value
}

class NonConformanceRepositoryTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val raisedOn = LocalDate.of(2026, 10, 3)
    private val raisedAt = raisedOn.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private lateinit var dao: FakeNonConformanceDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: NonConformanceRepository

    @Before
    fun setUp() {
        dao = FakeNonConformanceDao()
        audit = FakeAuditDao()
        repo = NonConformanceRepository(dao, AuditTrail(audit))
    }

    private suspend fun raise(project: String = "job.1", role: Role = Role.MANAGER) = repo.raise(
        role, project, " L3 slab, grid C-4 ", " Drawing S-102 rev C, cover 40 mm ", " Cover 25 mm at the edge ",
        NonConformances.FoundBy.SUPERVISOR, "Site manager", now = raisedAt,
    )

    @Test
    fun `only a role that writes the site's record raises one`() = runTest {
        Role.entries.forEach { role ->
            assertEquals(role.name, role.canWrite(Lens.EVIDENCE), raise(role = role).isSuccess)
        }
    }

    @Test
    fun `numbered per job, tidied, and audited with where it is`() = runTest {
        val first = raise().getOrThrow()
        val second = raise().getOrThrow()
        val elsewhere = raise(project = "job.2").getOrThrow()
        assertEquals("NCR-001", first.reference)
        assertEquals("NCR-002", second.reference)
        assertEquals("NCR-001", elsewhere.reference)
        assertEquals("L3 slab, grid C-4", first.element)
        assertEquals("Drawing S-102 rev C, cover 40 mm", first.requirement)
        val parsed = Summary.parse(audit.entries.first().summary)!!
        assertEquals(Summaries.NCR_RAISED, parsed.key)
        assertEquals(listOf("NCR-001", "L3 slab, grid C-4"), parsed.arguments)
    }

    @Test
    fun `decided once, with the disposition nested so the trail reads it in any language`() = runTest {
        val report = raise().getOrThrow()
        val decided = repo.decide(
            Role.MANAGER, report.id, NonConformances.Disposition.REPAIR, "", " Grind back and apply repair mortar ",
            raisedOn.plusDays(5), "Site manager", now = raisedAt + 1_000L, zone = zone,
        ).getOrThrow()
        assertEquals("REPAIR", decided.disposition)
        assertEquals("Grind back and apply repair mortar", decided.correction)
        assertNull(decided.acceptedBy)
        assertEquals(raisedOn.plusDays(5).toEpochDay(), decided.dueOnDay)
        val parsed = Summary.parse(audit.entries.last().summary)!!
        assertEquals(Summaries.NCR_DECIDED, parsed.key)
        assertEquals("ncr_disp_repair", Summary.nested(parsed.arguments[1]))
        val again = repo.decide(Role.MANAGER, report.id, NonConformances.Disposition.REWORK, "", "x", null, "Site manager", zone = zone)
            .exceptionOrNull() as NonConformanceRepository.Refused
        assertEquals(NonConformanceRepository.Refusal.ALREADY_DECIDED, again.refusal)
    }

    @Test
    fun `kept as it is needs who agreed, and a date cannot be before it was raised`() = runTest {
        val report = raise().getOrThrow()
        val nobody = repo.decide(Role.MANAGER, report.id, NonConformances.Disposition.ACCEPT_AS_IS, " ", "", null, "Site manager", zone = zone)
            .exceptionOrNull() as NonConformanceRepository.Refused
        assertEquals(NonConformanceRepository.Refusal.NO_ACCEPTOR, nobody.refusal)
        val early = repo.decide(Role.MANAGER, report.id, NonConformances.Disposition.REWORK, "", "Recast", raisedOn.minusDays(1), "Site manager", zone = zone)
            .exceptionOrNull() as NonConformanceRepository.Refused
        assertEquals(NonConformanceRepository.Refusal.DUE_BEFORE_RAISED, early.refusal)
        val kept = repo.decide(Role.MANAGER, report.id, NonConformances.Disposition.ACCEPT_AS_IS, " Dana Levi, supervisor ", "", null, "Site manager", zone = zone)
            .getOrThrow()
        assertEquals("Dana Levi, supervisor", kept.acceptedBy)
    }

    @Test
    fun `closed only once decided, with how it was checked, and only once`() = runTest {
        val report = raise().getOrThrow()
        val undecided = repo.close(Role.MANAGER, report.id, "Checked", "Site manager").exceptionOrNull() as NonConformanceRepository.Refused
        assertEquals(NonConformanceRepository.Refusal.NOT_DECIDED, undecided.refusal)
        repo.decide(Role.MANAGER, report.id, NonConformances.Disposition.REWORK, "", "Recast", null, "Site manager", zone = zone).getOrThrow()
        val closed = repo.close(Role.MANAGER, report.id, " Supervisor inspected on the 12th ", "Foreman", now = 9_000L).getOrThrow()
        assertEquals("Supervisor inspected on the 12th", closed.verification)
        assertEquals(9_000L, closed.closedAt)
        assertEquals(Summaries.NCR_CLOSED, Summary.parse(audit.entries.last().summary)!!.key)
        val twice = repo.close(Role.MANAGER, report.id, "Again", "Foreman").exceptionOrNull() as NonConformanceRepository.Refused
        assertEquals(NonConformanceRepository.Refusal.ALREADY_CLOSED, twice.refusal)
    }
}
