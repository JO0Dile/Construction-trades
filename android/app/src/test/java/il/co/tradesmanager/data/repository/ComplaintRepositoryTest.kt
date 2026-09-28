package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.evidence.Complaints
import il.co.tradesmanager.data.local.dao.ComplaintDao
import il.co.tradesmanager.data.local.entity.ComplaintEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The complaints register, in memory. */
class FakeComplaintDao : ComplaintDao {
    val rows = MutableStateFlow<List<ComplaintEntity>>(emptyList())

    override suspend fun upsert(complaint: ComplaintEntity) {
        rows.value = rows.value.filterNot { it.id == complaint.id } + complaint
    }

    override suspend fun complaint(id: String): ComplaintEntity? = rows.value.firstOrNull { it.id == id }

    override fun observeForProject(projectId: String): Flow<List<ComplaintEntity>> =
        rows.map { all -> all.filter { it.projectId == projectId }.sortedBy { it.receivedAt } }

    override suspend fun countForProject(projectId: String): Int = rows.value.count { it.projectId == projectId }

    override suspend fun all(): List<ComplaintEntity> = rows.value
}

class ComplaintRepositoryTest {

    private lateinit var dao: FakeComplaintDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: ComplaintRepository

    @Before
    fun setUp() {
        dao = FakeComplaintDao()
        audit = FakeAuditDao()
        repo = ComplaintRepository(dao, AuditTrail(audit))
    }

    private suspend fun receive(role: Role = Role.SAFETY_OFFICER, project: String = "job.1") = repo.receive(
        role = role,
        projectId = project,
        fromWhom = "  Neighbour at 12 Herzl  ",
        contact = " ",
        channel = Complaints.Channel.PHONE,
        subject = Complaints.Subject.NOISE,
        description = " The pump started at six ",
        receivedAt = 1_000L,
        byName = "Safety officer",
        now = 2_000L,
    )

    @Test
    fun `complaints are numbered per job, tidied, and audited with who made them`() = runTest {
        val first = receive().getOrThrow()
        val second = receive().getOrThrow()
        val elsewhere = receive(project = "job.2").getOrThrow()
        assertEquals("CP-001", first.reference)
        assertEquals("CP-002", second.reference)
        assertEquals("CP-001", elsewhere.reference)
        assertEquals("Neighbour at 12 Herzl", first.fromWhom)
        assertNull("a blank contact is none", first.contact)
        assertEquals("NOISE", first.subject)
        assertEquals("PHONE", first.channel)
        val parsed = Summary.parse(audit.entries.first().summary)!!
        assertEquals(Summaries.CP_RECEIVED, parsed.key)
        assertEquals(listOf("CP-001", "Neighbour at 12 Herzl"), parsed.arguments)
    }

    @Test
    fun `an answer is recorded once, with who gave it`() = runTest {
        val id = receive().getOrThrow().id
        val answered = repo.answer(Role.SAFETY_OFFICER, id, " Pump moved to the far side ", "Site manager", now = 5_000L).getOrThrow()
        assertEquals("Pump moved to the far side", answered.response)
        assertEquals(5_000L, answered.answeredAt)
        assertEquals("Site manager", answered.answeredByName)
        assertEquals(Summaries.CP_ANSWERED, Summary.parse(audit.entries.last().summary)!!.key)
        val again = repo.answer(Role.SAFETY_OFFICER, id, "Something else", "Site manager").exceptionOrNull() as ComplaintRepository.Refused
        assertEquals(ComplaintRepository.Refusal.ALREADY_ANSWERED, again.refusal)
    }

    @Test
    fun `somebody who may only read the site's record records nothing`() = runTest {
        val refused = receive(role = Role.FINANCE).exceptionOrNull() as ComplaintRepository.Refused
        assertEquals(ComplaintRepository.Refusal.NOT_ALLOWED, refused.refusal)
    }
}
