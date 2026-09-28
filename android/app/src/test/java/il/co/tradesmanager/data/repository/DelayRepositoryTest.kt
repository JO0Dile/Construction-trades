package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.work.Delays
import il.co.tradesmanager.data.local.dao.DelayDao
import il.co.tradesmanager.data.local.entity.DelayEventEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The delay register, in memory. */
class FakeDelayDao : DelayDao {
    val rows = MutableStateFlow<List<DelayEventEntity>>(emptyList())

    override suspend fun upsert(event: DelayEventEntity) {
        rows.value = rows.value.filterNot { it.id == event.id } + event
    }

    override suspend fun event(id: String): DelayEventEntity? = rows.value.firstOrNull { it.id == id }

    override fun observeForProject(projectId: String): Flow<List<DelayEventEntity>> =
        rows.map { all -> all.filter { it.projectId == projectId }.sortedByDescending { it.startedOnDay } }

    override suspend fun countForProject(projectId: String): Int = rows.value.count { it.projectId == projectId }

    override suspend fun all(): List<DelayEventEntity> = rows.value
}

class DelayRepositoryTest {

    private lateinit var dao: FakeDelayDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: DelayRepository
    private val sunday = LocalDate.of(2026, 10, 4)

    @Before
    fun setUp() {
        dao = FakeDelayDao()
        audit = FakeAuditDao()
        repo = DelayRepository(dao, AuditTrail(audit))
    }

    private suspend fun record(project: String = "job.1", role: Role = Role.MANAGER, started: LocalDate = sunday) = repo.record(
        role = role,
        projectId = project,
        cause = Delays.Cause.WEATHER,
        description = "  Rain from 6am  ",
        affectedWork = " ",
        startedOn = started,
        relatedReference = " Q-004 ",
        byName = "Site manager",
        today = sunday.plusDays(1),
    )

    private fun lastSummary() = Summary.parse(audit.entries.last().summary)!!

    @Test
    fun `delays are numbered per job, kept as calendar days, tidied and audited`() = runTest {
        val first = record().getOrThrow()
        val second = record().getOrThrow()
        val elsewhere = record(project = "job.2").getOrThrow()

        assertEquals("DE-001", first.reference)
        assertEquals("DE-002", second.reference)
        assertEquals("DE-001", elsewhere.reference)
        assertEquals("Rain from 6am", first.description)
        assertNull("blank work is none", first.affectedWork)
        assertEquals("Q-004", first.relatedReference)
        assertEquals(sunday.toEpochDay(), first.startedOnDay)
        assertEquals("WEATHER", first.cause)
        assertEquals(Summaries.DE_RECORDED, lastSummary().key)
        assertEquals(listOf("DE-001", "2026-10-04"), Summary.parse(audit.entries.first().summary)!!.arguments)
    }

    @Test
    fun `a delay that has not begun is not recorded`() = runTest {
        val refused = record(started = sunday.plusDays(5)).exceptionOrNull() as DelayRepository.Refused
        assertEquals(DelayRepository.Refusal.STARTS_IN_FUTURE, refused.refusal)
    }

    @Test
    fun `the end and the notice are each written once`() = runTest {
        val id = record().getOrThrow().id

        val early = repo.end(Role.MANAGER, id, sunday.minusDays(1), "Site manager").exceptionOrNull() as DelayRepository.Refused
        assertEquals(DelayRepository.Refusal.ENDS_BEFORE_START, early.refusal)

        val ended = repo.end(Role.MANAGER, id, sunday.plusDays(1), "Site manager").getOrThrow()
        assertEquals(sunday.plusDays(1).toEpochDay(), ended.endedOnDay)
        assertEquals(Summaries.DE_ENDED, lastSummary().key)
        val again = repo.end(Role.MANAGER, id, sunday.plusDays(2), "Site manager").exceptionOrNull() as DelayRepository.Refused
        assertEquals(DelayRepository.Refusal.ALREADY_ENDED, again.refusal)

        val notified = repo.notice(Role.MANAGER, id, " The supervisor ", sunday, "Site manager").getOrThrow()
        assertEquals("The supervisor", notified.notifiedTo)
        assertEquals(sunday.toEpochDay(), notified.notifiedOnDay)
        assertEquals(listOf("DE-001", "The supervisor"), lastSummary().arguments)
        val twice = repo.notice(Role.MANAGER, id, "The client", sunday, "Site manager").exceptionOrNull() as DelayRepository.Refused
        assertEquals(DelayRepository.Refusal.NOTICE_ALREADY_GIVEN, twice.refusal)
    }

    @Test
    fun `somebody who does not plan the work records nothing`() = runTest {
        val refused = record(role = Role.FINANCE).exceptionOrNull() as DelayRepository.Refused
        assertEquals(DelayRepository.Refusal.NOT_ALLOWED, refused.refusal)

        val id = record().getOrThrow().id
        val ended = repo.end(Role.FINANCE, id, sunday, "Books").exceptionOrNull() as DelayRepository.Refused
        assertEquals(DelayRepository.Refusal.NOT_ALLOWED, ended.refusal)
        assertNull(dao.event(id)!!.endedOnDay)
    }
}
