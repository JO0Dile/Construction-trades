package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.evidence.Inspections
import il.co.tradesmanager.data.local.dao.InspectionDao
import il.co.tradesmanager.data.local.entity.InspectionEntity
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The inspection register, in memory. */
class FakeInspectionDao : InspectionDao {
    val rows = MutableStateFlow<List<InspectionEntity>>(emptyList())

    override suspend fun upsert(inspection: InspectionEntity) {
        rows.value = rows.value.filterNot { it.id == inspection.id } + inspection
    }

    override suspend fun inspection(id: String): InspectionEntity? = rows.value.firstOrNull { it.id == id }

    override fun observeForProject(projectId: String): Flow<List<InspectionEntity>> =
        rows.map { all -> all.filter { it.projectId == projectId }.sortedBy { it.requestedAt } }

    override suspend fun countForProject(projectId: String): Int = rows.value.count { it.projectId == projectId }

    override suspend fun reinspectionCount(failedId: String): Int = rows.value.count { it.reinspectionOf == failedId }

    override suspend fun all(): List<InspectionEntity> = rows.value
}

class InspectionRepositoryTest {

    private lateinit var dao: FakeInspectionDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: InspectionRepository
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val pours = mapOf(
        "pour.1" to InspectionRepository.PourRef("job.1", "POUR-001"),
        "pour.2" to InspectionRepository.PourRef("job.1", "POUR-002"),
        "pour.elsewhere" to InspectionRepository.PourRef("job.2", "POUR-003"),
    )

    @Before
    fun setUp() {
        dao = FakeInspectionDao()
        audit = FakeAuditDao()
        repo = InspectionRepository(dao, AuditTrail(audit)) { pours[it] }
    }

    private suspend fun request(
        project: String = "job.1",
        kind: Inspections.Kind = Inspections.Kind.REINFORCEMENT,
        role: Role = Role.MANAGER,
    ) = repo.request(
        role = role,
        projectId = project,
        kind = kind,
        element = "  Level 3 slab, grids A-D  ",
        requestedOf = " Structural engineer ",
        wantedOn = null,
        byName = "Foreman",
        now = 1_000_000L,
        zone = zone,
    )

    private fun lastSummaryKey() = Summary.parse(audit.entries.last().summary)!!.key

    @Test
    fun `requests are numbered per job, tidied, and audited`() = runTest {
        val first = request().getOrThrow()
        val second = request().getOrThrow()
        val elsewhere = request(project = "job.2").getOrThrow()

        assertEquals("IR-001", first.reference)
        assertEquals("IR-002", second.reference)
        assertEquals("IR-001", elsewhere.reference)
        assertEquals("Level 3 slab, grids A-D", first.element)
        assertEquals("Structural engineer", first.requestedOf)
        assertEquals("REINFORCEMENT", first.kind)
        assertEquals(Summaries.IR_REQUESTED, Summary.parse(audit.entries.first().summary)!!.key)
    }

    @Test
    fun `a result is written once, with the inspector's name and who typed it`() = runTest {
        val id = request().getOrThrow().id
        val passed = repo.decide(
            Role.MANAGER, id, Inspections.Result.PASSED, " Dana Levi ", "  ", "Office", now = 2_000_000L,
        ).getOrThrow()

        assertEquals("PASSED", passed.result)
        assertEquals("Dana Levi", passed.inspectorName)
        assertNull("blank comments are none", passed.comments)
        assertEquals(2_000_000L, passed.decidedAt)
        assertEquals("Office", passed.resultRecordedByName)
        assertEquals(Summaries.IR_PASSED, lastSummaryKey())

        val again = repo.decide(Role.MANAGER, id, Inspections.Result.FAILED, "Somebody", "No", "Office")
            .exceptionOrNull() as InspectionRepository.Refused
        assertEquals(InspectionRepository.Refusal.ALREADY_DECIDED, again.refusal)
        assertEquals("PASSED", dao.inspection(id)!!.result)
    }

    @Test
    fun `a failure needs a reason, and is followed by a new request that points back at it`() = runTest {
        val id = request().getOrThrow().id
        val noReason = repo.decide(Role.MANAGER, id, Inspections.Result.FAILED, "Dana Levi", " ", "Office")
            .exceptionOrNull() as InspectionRepository.Refused
        assertEquals(InspectionRepository.Refusal.FAILED_WITHOUT_REASON, noReason.refusal)

        repo.decide(Role.MANAGER, id, Inspections.Result.FAILED, "Dana Levi", "Laps short at grid C", "Office").getOrThrow()
        assertEquals(Summaries.IR_FAILED, lastSummaryKey())

        val again = repo.reinspect(Role.MANAGER, id, wantedOn = null, byName = "Foreman", now = 3_000_000L, zone = zone)
            .getOrThrow()
        assertEquals("IR-002", again.reference)
        assertEquals(id, again.reinspectionOf)
        assertEquals("the same work", "Level 3 slab, grids A-D", again.element)
        assertEquals("of the same person", "Structural engineer", again.requestedOf)
        assertNull("a new request has no result", again.result)
        assertEquals(Summaries.IR_REQUESTED_AGAIN, lastSummaryKey())

        val twice = repo.reinspect(Role.MANAGER, id, null, "Foreman").exceptionOrNull() as InspectionRepository.Refused
        assertEquals(InspectionRepository.Refusal.ALREADY_REINSPECTED, twice.refusal)
    }

    @Test
    fun `only a failed inspection is asked again`() = runTest {
        val waiting = request().getOrThrow().id
        val refused = repo.reinspect(Role.MANAGER, waiting, null, "Foreman").exceptionOrNull() as InspectionRepository.Refused
        assertEquals(InspectionRepository.Refusal.NOT_FAILED, refused.refusal)
    }

    @Test
    fun `a passed inspection of the steel clears one pour on its own job`() = runTest {
        val steel = request().getOrThrow().id
        val early = repo.clearPour(Role.MANAGER, steel, "pour.1", "Foreman").exceptionOrNull() as InspectionRepository.Refused
        assertEquals("not passed yet", InspectionRepository.Refusal.NOT_PASSED, early.refusal)

        repo.decide(Role.MANAGER, steel, Inspections.Result.PASSED, "Dana Levi", "", "Office").getOrThrow()
        val elsewhere = repo.clearPour(Role.MANAGER, steel, "pour.elsewhere", "Foreman")
            .exceptionOrNull() as InspectionRepository.Refused
        assertEquals(InspectionRepository.Refusal.OTHER_JOB, elsewhere.refusal)

        val cleared = repo.clearPour(Role.MANAGER, steel, "pour.1", "Foreman").getOrThrow()
        assertEquals("pour.1", cleared.clearedPourId)
        assertEquals(Summaries.IR_CLEARED_POUR, lastSummaryKey())
        assertEquals(listOf("IR-001", "POUR-001"), Summary.parse(audit.entries.last().summary)!!.arguments)

        val next = repo.clearPour(Role.MANAGER, steel, "pour.2", "Foreman").exceptionOrNull() as InspectionRepository.Refused
        assertEquals("the next slab is new steel", InspectionRepository.Refusal.ALREADY_CLEARED_A_POUR, next.refusal)

        val membrane = request(kind = Inspections.Kind.WATERPROOFING).getOrThrow().id
        repo.decide(Role.MANAGER, membrane, Inspections.Result.PASSED, "Dana Levi", "", "Office").getOrThrow()
        val wrongKind = repo.clearPour(Role.MANAGER, membrane, "pour.2", "Foreman").exceptionOrNull() as InspectionRepository.Refused
        assertEquals(InspectionRepository.Refusal.NOT_FOR_A_POUR, wrongKind.refusal)
    }

    @Test
    fun `somebody who does not keep the site's record does not ask, decide or clear`() = runTest {
        val refused = request(role = Role.FINANCE).exceptionOrNull() as InspectionRepository.Refused
        assertEquals(InspectionRepository.Refusal.NOT_ALLOWED, refused.refusal)

        val id = request().getOrThrow().id
        val decided = repo.decide(Role.FINANCE, id, Inspections.Result.PASSED, "Dana Levi", "", "Books")
            .exceptionOrNull() as InspectionRepository.Refused
        assertEquals(InspectionRepository.Refusal.NOT_ALLOWED, decided.refusal)
        assertNull(dao.inspection(id)!!.result)
    }
}
