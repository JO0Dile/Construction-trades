package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.work.Submittals
import il.co.tradesmanager.data.local.dao.SubmittalDao
import il.co.tradesmanager.data.local.entity.SubmittalEntity
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The submittal register, in memory. */
class FakeSubmittalDao : SubmittalDao {
    val rows = MutableStateFlow<List<SubmittalEntity>>(emptyList())

    override suspend fun upsert(submittal: SubmittalEntity) {
        rows.value = rows.value.filterNot { it.id == submittal.id } + submittal
    }

    override suspend fun submittal(id: String): SubmittalEntity? = rows.value.firstOrNull { it.id == id }

    override fun observeForProject(projectId: String): Flow<List<SubmittalEntity>> =
        rows.map { all -> all.filter { it.projectId == projectId }.sortedBy { it.submittedAt } }

    override suspend fun countFirstSubmissions(projectId: String): Int =
        rows.value.count { it.projectId == projectId && it.resubmissionOf == null }

    override suspend fun resubmissionCount(rejectedId: String): Int = rows.value.count { it.resubmissionOf == rejectedId }

    override suspend fun all(): List<SubmittalEntity> = rows.value
}

class SubmittalRepositoryTest {

    private lateinit var dao: FakeSubmittalDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: SubmittalRepository
    private val zone = ZoneId.of("Asia/Jerusalem")

    @Before
    fun setUp() {
        dao = FakeSubmittalDao()
        audit = FakeAuditDao()
        repo = SubmittalRepository(dao, AuditTrail(audit))
    }

    private suspend fun submit(project: String = "job.1", role: Role = Role.MANAGER) = repo.submit(
        role = role,
        projectId = project,
        item = "  Porcelain tile 60x60, grey  ",
        supplier = " ",
        location = " Lobby floors ",
        submittedTo = " Architect ",
        neededBy = null,
        byName = "Site manager",
        now = 1_000_000L,
        zone = zone,
    )

    private fun lastSummary() = Summary.parse(audit.entries.last().summary)!!

    @Test
    fun `submittals are numbered per job, tidied, and audited`() = runTest {
        val first = submit().getOrThrow()
        val second = submit().getOrThrow()
        val elsewhere = submit(project = "job.2").getOrThrow()

        assertEquals("MS-001", first.reference)
        assertEquals(0, first.revision)
        assertEquals("MS-002", second.reference)
        assertEquals("MS-001", elsewhere.reference)
        assertEquals("Porcelain tile 60x60, grey", first.item)
        assertNull("a blank supplier is none", first.supplier)
        assertEquals("Lobby floors", first.location)
        assertEquals("Architect", first.submittedTo)
        assertEquals(Summaries.MS_SUBMITTED, Summary.parse(audit.entries.first().summary)!!.key)
    }

    @Test
    fun `an answer is written once, with the reviewer's name`() = runTest {
        val id = submit().getOrThrow().id
        val approved = repo.decide(
            Role.MANAGER, id, Submittals.Decision.APPROVED_AS_NOTED, " Noa Cohen ", " Grout to match sample ", "Office",
            now = 2_000_000L,
        ).getOrThrow()
        assertEquals("APPROVED_AS_NOTED", approved.decision)
        assertEquals("Noa Cohen", approved.reviewerName)
        assertEquals("Grout to match sample", approved.notes)
        assertEquals("Office", approved.decisionRecordedByName)
        assertEquals(Summaries.MS_APPROVED_AS_NOTED, lastSummary().key)

        val again = repo.decide(Role.MANAGER, id, Submittals.Decision.REJECTED, "Somebody", "No", "Office")
            .exceptionOrNull() as SubmittalRepository.Refused
        assertEquals(SubmittalRepository.Refusal.ALREADY_DECIDED, again.refusal)
    }

    @Test
    fun `a rejection keeps the number and goes up a revision, once`() = runTest {
        val first = submit().getOrThrow()
        submit().getOrThrow()
        repo.decide(Role.MANAGER, first.id, Submittals.Decision.REJECTED, "Noa Cohen", "Wrong shade", "Office").getOrThrow()
        assertEquals(Summaries.MS_REJECTED, lastSummary().key)

        val rev1 = repo.resubmit(Role.MANAGER, first.id, " Porcelain tile 60x60, light grey ", null, "Site manager", now = 3_000_000L, zone = zone)
            .getOrThrow()
        assertEquals("the same number", "MS-001", rev1.reference)
        assertEquals(1, rev1.revision)
        assertEquals(first.id, rev1.resubmissionOf)
        assertEquals("what is proposed now", "Porcelain tile 60x60, light grey", rev1.item)
        assertEquals("to the same person", "Architect", rev1.submittedTo)
        assertNull("a new revision has no answer", rev1.decision)
        assertNull(rev1.reviewerName)
        assertEquals(listOf("MS-001", "1"), lastSummary().arguments)

        val next = submit().getOrThrow()
        assertEquals("a revision does not take a new number", "MS-003", next.reference)

        val twice = repo.resubmit(Role.MANAGER, first.id, "Anything", null, "Site manager")
            .exceptionOrNull() as SubmittalRepository.Refused
        assertEquals(SubmittalRepository.Refusal.ALREADY_RESUBMITTED, twice.refusal)

        val notRejected = repo.resubmit(Role.MANAGER, next.id, "Anything", null, "Site manager")
            .exceptionOrNull() as SubmittalRepository.Refused
        assertEquals(SubmittalRepository.Refusal.NOT_REJECTED, notRejected.refusal)
    }

    @Test
    fun `somebody who does not plan the work does not submit or decide`() = runTest {
        val refused = submit(role = Role.FINANCE).exceptionOrNull() as SubmittalRepository.Refused
        assertEquals(SubmittalRepository.Refusal.NOT_ALLOWED, refused.refusal)

        val id = submit().getOrThrow().id
        val decided = repo.decide(Role.FINANCE, id, Submittals.Decision.APPROVED, "Noa Cohen", "", "Books")
            .exceptionOrNull() as SubmittalRepository.Refused
        assertEquals(SubmittalRepository.Refusal.NOT_ALLOWED, decided.refusal)
        assertNull(dao.submittal(id)!!.decision)
    }
}
