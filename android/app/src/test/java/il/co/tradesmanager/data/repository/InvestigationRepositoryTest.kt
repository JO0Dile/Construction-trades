package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.safety.Investigations
import il.co.tradesmanager.data.local.dao.InvestigationDao
import il.co.tradesmanager.data.local.entity.IncidentActionEntity
import il.co.tradesmanager.data.local.entity.IncidentEntity
import il.co.tradesmanager.data.local.entity.IncidentInvestigationEntity
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Investigations and their actions, in memory. The per-job counts are the database's and are not faked. */
private class FakeInvestigationDao : InvestigationDao {
    val investigations = MutableStateFlow<List<IncidentInvestigationEntity>>(emptyList())
    val actionRows = MutableStateFlow<List<IncidentActionEntity>>(emptyList())

    override suspend fun upsert(investigation: IncidentInvestigationEntity) {
        investigations.value = investigations.value.filterNot { it.incidentId == investigation.incidentId } + investigation
    }

    override suspend fun investigation(incidentId: String) = investigations.value.firstOrNull { it.incidentId == incidentId }
    override fun observe(incidentId: String): Flow<IncidentInvestigationEntity?> =
        investigations.map { all -> all.firstOrNull { it.incidentId == incidentId } }
    override fun observeAll(): Flow<List<IncidentInvestigationEntity>> = investigations

    override suspend fun upsertAction(action: IncidentActionEntity) {
        actionRows.value = actionRows.value.filterNot { it.id == action.id } + action
    }

    override suspend fun action(id: String) = actionRows.value.firstOrNull { it.id == id }
    override fun observeActions(incidentId: String): Flow<List<IncidentActionEntity>> =
        actionRows.map { all -> all.filter { it.incidentId == incidentId }.sortedBy { it.number } }
    override suspend fun actions(incidentId: String) = actionRows.value.filter { it.incidentId == incidentId }
    override suspend fun countActions(incidentId: String) = actionRows.value.count { it.incidentId == incidentId }
    override fun observeOutstandingForProject(projectId: String): Flow<Int> = MutableStateFlow(0)
    override fun observeOverdueActionsForProject(projectId: String, today: Long): Flow<Int> = MutableStateFlow(0)
}

class InvestigationRepositoryTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val incidentDay = LocalDate.of(2026, 10, 1)
    private val incident = IncidentEntity(
        id = "inc.1",
        projectId = "job.1",
        severity = "SERIOUS",
        description = "Fell from the second rung",
        occurredAt = incidentDay.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
        reportedByName = "Foreman",
        createdAt = 0L,
        companyId = "co.1",
    )

    private lateinit var dao: FakeInvestigationDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: InvestigationRepository

    @Before
    fun setUp() {
        dao = FakeInvestigationDao()
        audit = FakeAuditDao()
        repo = InvestigationRepository(dao, { id -> incident.takeIf { it.id == id } }, AuditTrail(audit))
    }

    private suspend fun start(role: Role = Role.SAFETY_OFFICER) = repo.save(
        role, "inc.1", " The board slid ", setOf(Investigations.Cause.SITE_CONDITIONS), " ", "Safety officer", now = 1_000L,
    )

    @Test
    fun `only a role that writes the site's record investigates`() = runTest {
        Role.entries.forEach { role ->
            val result = InvestigationRepository(FakeInvestigationDao(), { incident }, AuditTrail(FakeAuditDao()))
                .save(role, "inc.1", "x", emptySet(), "", "someone")
            assertEquals(role.name, role.canWrite(Lens.EVIDENCE), result.isSuccess)
        }
    }

    @Test
    fun `starting is audited once, and writing more is an update that keeps who started it`() = runTest {
        val first = start().getOrThrow()
        assertEquals("The board slid", first.immediateCause)
        assertNull(first.findings)
        assertEquals("SITE_CONDITIONS", first.causes)
        val second = repo.save(
            Role.MANAGER, "inc.1", "The board slid", setOf(Investigations.Cause.SITE_CONDITIONS, Investigations.Cause.SUPERVISION),
            "Nobody checked the footing", "Manager", now = 2_000L,
        ).getOrThrow()
        assertEquals("Safety officer", second.startedByName)
        assertEquals(1_000L, second.startedAt)
        assertEquals(2_000L, second.updatedAt)
        assertEquals(listOf(Summaries.INV_STARTED, Summaries.INV_UPDATED), audit.entries.map { Summary.parse(it.summary)!!.key })
    }

    @Test
    fun `an unknown incident, or an action before the investigation, is refused`() = runTest {
        val unknown = repo.save(Role.OWNER, "inc.9", "x", emptySet(), "", "Owner").exceptionOrNull() as InvestigationRepository.Refused
        assertEquals(InvestigationRepository.Refusal.UNKNOWN, unknown.refusal)
        val early = repo.raise(Role.OWNER, "inc.1", "Brief the crew", "", null, "Owner", zone = zone).exceptionOrNull() as InvestigationRepository.Refused
        assertEquals(InvestigationRepository.Refusal.NOT_STARTED, early.refusal)
    }

    @Test
    fun `actions are numbered, cannot be due before the incident, and close once with what was done`() = runTest {
        start().getOrThrow()
        val one = repo.raise(Role.SAFETY_OFFICER, "inc.1", " Brief the crew on ladders ", " Foreman ", incidentDay.plusDays(3), "Safety officer", zone = zone).getOrThrow()
        val two = repo.raise(Role.SAFETY_OFFICER, "inc.1", "Buy ladder feet", "", null, "Safety officer", zone = zone).getOrThrow()
        assertEquals(1, one.number)
        assertEquals(2, two.number)
        assertEquals("Brief the crew on ladders", one.text)
        assertEquals("Foreman", one.ownerName)
        assertNull(two.ownerName)
        assertEquals(listOf("2", "—"), Summary.parse(audit.entries.last { it.entityId == two.id }.summary)!!.arguments)
        val tooEarly = repo.raise(Role.SAFETY_OFFICER, "inc.1", "x", "", incidentDay.minusDays(1), "Safety officer", zone = zone)
            .exceptionOrNull() as InvestigationRepository.Refused
        assertEquals(InvestigationRepository.Refusal.DUE_BEFORE_INCIDENT, tooEarly.refusal)
        val closed = repo.closeAction(Role.SAFETY_OFFICER, one.id, " Briefed on the 4th ", "Foreman", now = 5_000L).getOrThrow()
        assertEquals("Briefed on the 4th", closed.closingNote)
        assertEquals(5_000L, closed.closedAt)
        val again = repo.closeAction(Role.SAFETY_OFFICER, one.id, "Again", "Foreman").exceptionOrNull() as InvestigationRepository.Refused
        assertEquals(InvestigationRepository.Refusal.ACTION_ALREADY_CLOSED, again.refusal)
    }

    @Test
    fun `it closes once every action is done, and nothing is added after`() = runTest {
        start().getOrThrow()
        val action = repo.raise(Role.SAFETY_OFFICER, "inc.1", "Brief the crew", "Foreman", null, "Safety officer", zone = zone).getOrThrow()
        val open = repo.close(Role.SAFETY_OFFICER, "inc.1", "Safety officer").exceptionOrNull() as InvestigationRepository.Refused
        assertEquals(InvestigationRepository.Refusal.ACTIONS_OPEN, open.refusal)
        repo.closeAction(Role.SAFETY_OFFICER, action.id, "Briefed", "Foreman").getOrThrow()
        val closed = repo.close(Role.SAFETY_OFFICER, "inc.1", "Safety officer", now = 9_000L).getOrThrow()
        assertEquals(9_000L, closed.closedAt)
        assertEquals(Summaries.INV_CLOSED, Summary.parse(audit.entries.last().summary)!!.key)
        val late = repo.raise(Role.SAFETY_OFFICER, "inc.1", "One more", "", null, "Safety officer", zone = zone).exceptionOrNull() as InvestigationRepository.Refused
        assertEquals(InvestigationRepository.Refusal.ALREADY_CLOSED, late.refusal)
        val rewrite = repo.save(Role.SAFETY_OFFICER, "inc.1", "Something else", emptySet(), "", "Safety officer").exceptionOrNull() as InvestigationRepository.Refused
        assertEquals(InvestigationRepository.Refusal.ALREADY_CLOSED, rewrite.refusal)
        assertTrue(dao.investigations.value.single().closedAt != null)
    }

    @Test
    fun `it does not close without saying why`() = runTest {
        repo.save(Role.OWNER, "inc.1", " ", emptySet(), "", "Owner").getOrThrow()
        val blank = repo.close(Role.OWNER, "inc.1", "Owner").exceptionOrNull() as InvestigationRepository.Refused
        assertEquals(InvestigationRepository.Refusal.BLANK_IMMEDIATE_CAUSE, blank.refusal)
        repo.save(Role.OWNER, "inc.1", "The board slid", emptySet(), "", "Owner").getOrThrow()
        val noCause = repo.close(Role.OWNER, "inc.1", "Owner").exceptionOrNull() as InvestigationRepository.Refused
        assertEquals(InvestigationRepository.Refusal.NO_CAUSE, noCause.refusal)
    }
}
