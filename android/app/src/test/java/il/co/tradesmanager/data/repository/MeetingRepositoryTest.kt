package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.work.Meetings
import il.co.tradesmanager.data.local.dao.MeetingDao
import il.co.tradesmanager.data.local.entity.MeetingActionEntity
import il.co.tradesmanager.data.local.entity.MeetingEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Meetings and their points, in memory. */
class FakeMeetingDao : MeetingDao {
    val meetings = MutableStateFlow<List<MeetingEntity>>(emptyList())
    val actions = MutableStateFlow<List<MeetingActionEntity>>(emptyList())

    override suspend fun upsert(meeting: MeetingEntity) {
        meetings.value = meetings.value.filterNot { it.id == meeting.id } + meeting
    }

    override suspend fun meeting(id: String): MeetingEntity? = meetings.value.firstOrNull { it.id == id }

    override fun observeForProject(projectId: String): Flow<List<MeetingEntity>> =
        meetings.map { all -> all.filter { it.projectId == projectId }.sortedByDescending { it.heldOnDay } }

    override suspend fun countForProject(projectId: String): Int = meetings.value.count { it.projectId == projectId }

    override suspend fun upsertAction(action: MeetingActionEntity) {
        actions.value = actions.value.filterNot { it.id == action.id } + action
    }

    override suspend fun action(id: String): MeetingActionEntity? = actions.value.firstOrNull { it.id == id }

    override suspend fun countActionsFor(meetingId: String): Int = actions.value.count { it.meetingId == meetingId }

    override fun observeActionsForProject(projectId: String): Flow<List<MeetingActionEntity>> =
        actions.map { all -> all.filter { it.projectId == projectId }.sortedBy { it.raisedAt } }

    override suspend fun allActions(): List<MeetingActionEntity> = actions.value
}

class MeetingRepositoryTest {

    private lateinit var dao: FakeMeetingDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: MeetingRepository

    private val today = LocalDate.of(2026, 10, 8)

    @Before
    fun setUp() {
        dao = FakeMeetingDao()
        audit = FakeAuditDao()
        repo = MeetingRepository(dao, AuditTrail(audit))
    }

    private suspend fun meet(role: Role = Role.MANAGER, project: String = "job.1") = repo.record(
        role = role,
        projectId = project,
        kind = Meetings.Kind.COORDINATION,
        heldOn = today,
        attendees = " Site manager, electrician ",
        notes = " ",
        byName = "Site manager",
        today = today,
        now = 1_000L,
    )

    @Test
    fun `meetings are numbered per job, tidied, and audited with the day`() = runTest {
        val first = meet().getOrThrow()
        val second = meet().getOrThrow()
        val elsewhere = meet(project = "job.2").getOrThrow()
        assertEquals("MT-001", first.reference)
        assertEquals("MT-002", second.reference)
        assertEquals("MT-001", elsewhere.reference)
        assertEquals("Site manager, electrician", first.attendees)
        assertNull(first.notes)
        val parsed = Summary.parse(audit.entries.first().summary)!!
        assertEquals(Summaries.MT_RECORDED, parsed.key)
        assertEquals(listOf("MT-001", "2026-10-08"), parsed.arguments)
    }

    @Test
    fun `points are numbered under their meeting and carry the job`() = runTest {
        val meeting = meet().getOrThrow()
        val other = meet().getOrThrow()
        val one = repo.raise(Role.MANAGER, meeting.id, " Send the revised slab drawing ", " Architect ", today.plusDays(7), "Site manager").getOrThrow()
        val two = repo.raise(Role.MANAGER, meeting.id, "Book the pump", "", null, "Site manager").getOrThrow()
        val firstOfOther = repo.raise(Role.MANAGER, other.id, "Fence the gap", "Foreman", null, "Site manager").getOrThrow()
        assertEquals("MT-001/1", one.reference)
        assertEquals("MT-001/2", two.reference)
        assertEquals("MT-002/1", firstOfOther.reference)
        assertEquals("Send the revised slab drawing", one.text)
        assertEquals("Architect", one.ownerName)
        assertNull(two.ownerName)
        assertEquals("job.1", one.projectId)
        assertEquals(listOf("MT-001/2", "—"), Summary.parse(audit.entries.last { it.entityId == two.id }.summary)!!.arguments)
        val early = repo.raise(Role.MANAGER, meeting.id, "Too early", "", today.minusDays(1), "Site manager")
            .exceptionOrNull() as MeetingRepository.Refused
        assertEquals(MeetingRepository.Refusal.DUE_BEFORE_MEETING, early.refusal)
    }

    @Test
    fun `a point is closed once, with what was done and who said so`() = runTest {
        val meeting = meet().getOrThrow()
        val point = repo.raise(Role.MANAGER, meeting.id, "Send the drawing", "Architect", null, "Site manager").getOrThrow()
        val closed = repo.close(Role.MANAGER, point.id, " Received rev C ", "Foreman", now = 5_000L).getOrThrow()
        assertEquals(5_000L, closed.closedAt)
        assertEquals("Foreman", closed.closedByName)
        assertEquals("Received rev C", closed.closingNote)
        assertEquals(Summaries.MT_ACTION_CLOSED, Summary.parse(audit.entries.last().summary)!!.key)
        val again = repo.close(Role.MANAGER, point.id, "Again", "Foreman").exceptionOrNull() as MeetingRepository.Refused
        assertEquals(MeetingRepository.Refusal.ALREADY_CLOSED, again.refusal)
    }

    @Test
    fun `somebody who may not write the plan records nothing`() = runTest {
        val refused = meet(role = Role.FINANCE).exceptionOrNull() as MeetingRepository.Refused
        assertEquals(MeetingRepository.Refusal.NOT_ALLOWED, refused.refusal)
    }
}
