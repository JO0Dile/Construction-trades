package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.safety.FirePoints
import il.co.tradesmanager.data.local.dao.FirePointDao
import il.co.tradesmanager.data.local.entity.FirePointCheckEntity
import il.co.tradesmanager.data.local.entity.FirePointEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The fire points register, in memory. */
class FakeFirePointDao : FirePointDao {
    val points = MutableStateFlow<List<FirePointEntity>>(emptyList())
    val checks = MutableStateFlow<List<FirePointCheckEntity>>(emptyList())

    override suspend fun upsert(point: FirePointEntity) {
        points.value = points.value.filterNot { it.id == point.id } + point
    }

    override suspend fun point(id: String): FirePointEntity? = points.value.firstOrNull { it.id == id }

    override fun observeForProject(projectId: String): Flow<List<FirePointEntity>> =
        points.map { all -> all.filter { it.projectId == projectId }.sortedBy { it.addedAt } }

    override suspend fun countForProject(projectId: String): Int = points.value.count { it.projectId == projectId }

    override suspend fun all(): List<FirePointEntity> = points.value

    override suspend fun insertCheck(check: FirePointCheckEntity) {
        checks.value = checks.value + check
    }

    override fun observeChecksForProject(projectId: String): Flow<List<FirePointCheckEntity>> =
        checks.map { all -> all.filter { it.projectId == projectId }.sortedByDescending { it.checkedAt } }
}

class FirePointRepositoryTest {

    private lateinit var dao: FakeFirePointDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: FirePointRepository

    private val today = LocalDate.of(2026, 10, 8)

    @Before
    fun setUp() {
        dao = FakeFirePointDao()
        audit = FakeAuditDao()
        repo = FirePointRepository(dao, AuditTrail(audit))
    }

    private suspend fun add(role: Role = Role.SAFETY_OFFICER, project: String = "job.1") = repo.add(
        role = role,
        projectId = project,
        kind = FirePoints.Kind.CO2,
        location = "  By the stair, level 2 ",
        tagNumber = " ",
        serviceDueOn = today.plusMonths(8),
        byName = "Safety officer",
        today = today,
        now = 1_000L,
    )

    @Test
    fun `points are numbered per job, tidied, and audited with where they are`() = runTest {
        val first = add().getOrThrow()
        val second = add().getOrThrow()
        val elsewhere = add(project = "job.2").getOrThrow()
        assertEquals("FP-001", first.reference)
        assertEquals("FP-002", second.reference)
        assertEquals("FP-001", elsewhere.reference)
        assertEquals("By the stair, level 2", first.location)
        assertNull("a blank tag is none", first.tagNumber)
        assertEquals("CO2", first.kind)
        val parsed = Summary.parse(audit.entries.first().summary)!!
        assertEquals(Summaries.FP_ADDED, parsed.key)
        assertEquals(listOf("FP-001", "By the stair, level 2"), parsed.arguments)
    }

    @Test
    fun `every look is its own row, and a fault says what was wrong`() = runTest {
        val id = add().getOrThrow().id
        repo.check(Role.SAFETY_OFFICER, id, ok = true, note = "", byName = "Foreman", now = 2_000L).getOrThrow()
        val fault = repo.check(Role.SAFETY_OFFICER, id, ok = false, note = " Gauge in the red ", byName = "Foreman", now = 3_000L).getOrThrow()
        assertEquals(2, dao.checks.value.size)
        assertFalse(fault.ok)
        assertEquals("Gauge in the red", fault.note)
        assertEquals("job.1", fault.projectId)
        val parsed = Summary.parse(audit.entries.last().summary)!!
        assertEquals(Summaries.FP_FAULT, parsed.key)
        assertEquals(listOf("FP-001", "Gauge in the red"), parsed.arguments)
        assertEquals(fault.id, FirePointRepository.latestByPoint(dao.checks.value)[id]?.id)
        val silent = repo.check(Role.SAFETY_OFFICER, id, ok = false, note = "", byName = "Foreman").exceptionOrNull() as FirePointRepository.Refused
        assertEquals(FirePointRepository.Refusal.FAULT_NEEDS_NOTE, silent.refusal)
    }

    @Test
    fun `a service moves the label date on, and a date not ahead is refused`() = runTest {
        val id = add().getOrThrow().id
        val serviced = repo.serviced(Role.SAFETY_OFFICER, id, today.plusYears(1), "Safety officer", today = today).getOrThrow()
        assertEquals(today.plusYears(1).toEpochDay(), serviced.serviceDueOnDay)
        assertEquals(Summaries.FP_SERVICED, Summary.parse(audit.entries.last().summary)!!.key)
        val past = repo.serviced(Role.SAFETY_OFFICER, id, today, "Safety officer", today = today).exceptionOrNull() as FirePointRepository.Refused
        assertEquals(FirePointRepository.Refusal.SERVICE_DUE_IN_PAST, past.refusal)
    }

    @Test
    fun `taken away is a date, once, and no look is recorded after it`() = runTest {
        val id = add().getOrThrow().id
        val removed = repo.remove(Role.SAFETY_OFFICER, id, "Site manager", now = 9_000L).getOrThrow()
        assertEquals(9_000L, removed.removedAt)
        assertEquals(Summaries.FP_REMOVED, Summary.parse(audit.entries.last().summary)!!.key)
        val look = repo.check(Role.SAFETY_OFFICER, id, ok = true, note = "", byName = "Foreman").exceptionOrNull() as FirePointRepository.Refused
        assertEquals(FirePointRepository.Refusal.ALREADY_REMOVED, look.refusal)
        val again = repo.remove(Role.SAFETY_OFFICER, id, "Site manager").exceptionOrNull() as FirePointRepository.Refused
        assertEquals(FirePointRepository.Refusal.ALREADY_REMOVED, again.refusal)
    }

    @Test
    fun `somebody who may only read the site's record records nothing`() = runTest {
        val refused = add(role = Role.FINANCE).exceptionOrNull() as FirePointRepository.Refused
        assertEquals(FirePointRepository.Refusal.NOT_ALLOWED, refused.refusal)
    }
}
