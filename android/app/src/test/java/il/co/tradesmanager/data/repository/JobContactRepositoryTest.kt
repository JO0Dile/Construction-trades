package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.work.Contacts
import il.co.tradesmanager.data.local.dao.JobContactDao
import il.co.tradesmanager.data.local.entity.JobContactEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** A job's contacts, in memory. */
class FakeJobContactDao : JobContactDao {
    val rows = MutableStateFlow<List<JobContactEntity>>(emptyList())

    override suspend fun upsert(contact: JobContactEntity) {
        rows.value = rows.value.filterNot { it.id == contact.id } + contact
    }

    override suspend fun contact(id: String): JobContactEntity? = rows.value.firstOrNull { it.id == id }

    override fun observeForProject(projectId: String): Flow<List<JobContactEntity>> =
        rows.map { all -> all.filter { it.projectId == projectId }.sortedBy { it.addedAt } }

    override suspend fun all(): List<JobContactEntity> = rows.value
}

class JobContactRepositoryTest {

    private lateinit var dao: FakeJobContactDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: JobContactRepository

    @Before
    fun setUp() {
        dao = FakeJobContactDao()
        audit = FakeAuditDao()
        repo = JobContactRepository(dao, AuditTrail(audit))
    }

    private fun entry(phone: String = " 054-1234567 ", email: String = "") = JobContactRepository.Entry(
        name = "  Dana Levi ",
        organisation = " ",
        kind = Contacts.Kind.SUPERVISOR,
        phone = phone,
        email = email,
        notes = "",
    )

    @Test
    fun `an entry is tidied and audited by name`() = runTest {
        val added = repo.add(Role.MANAGER, "job.1", entry(), "Site manager", now = 1_000L).getOrThrow()
        assertEquals("Dana Levi", added.name)
        assertNull(added.organisation)
        assertEquals("054-1234567", added.phone)
        assertNull(added.email)
        assertEquals("SUPERVISOR", added.kind)
        val parsed = Summary.parse(audit.entries.first().summary)!!
        assertEquals(Summaries.JC_ADDED, parsed.key)
        assertEquals(listOf("Dana Levi"), parsed.arguments)
    }

    @Test
    fun `a new number replaces the old one, and the change is audited`() = runTest {
        val id = repo.add(Role.MANAGER, "job.1", entry(), "Site manager", now = 1_000L).getOrThrow().id
        val corrected = repo.correct(Role.MANAGER, id, entry(phone = "052-7654321"), "Site manager", now = 2_000L).getOrThrow()
        assertEquals("052-7654321", corrected.phone)
        assertEquals(2_000L, corrected.updatedAt)
        assertEquals(1, dao.rows.value.size)
        assertEquals(Summaries.JC_CORRECTED, Summary.parse(audit.entries.last().summary)!!.key)
    }

    @Test
    fun `leaving the job is a date, and nothing is corrected after it`() = runTest {
        val id = repo.add(Role.MANAGER, "job.1", entry(), "Site manager").getOrThrow().id
        val gone = repo.remove(Role.MANAGER, id, "Site manager", now = 5_000L).getOrThrow()
        assertEquals(5_000L, gone.removedAt)
        assertEquals(1, dao.rows.value.size)
        val late = repo.correct(Role.MANAGER, id, entry(), "Site manager").exceptionOrNull() as JobContactRepository.Refused
        assertEquals(JobContactRepository.Refusal.ALREADY_REMOVED, late.refusal)
    }

    @Test
    fun `nobody to reach is refused, and only the plan's keepers write`() = runTest {
        val unreachable = repo.add(Role.MANAGER, "job.1", entry(phone = ""), "Site manager").exceptionOrNull() as JobContactRepository.Refused
        assertEquals(JobContactRepository.Refusal.NO_WAY_TO_REACH, unreachable.refusal)
        Role.entries.filter { !it.canWrite(Lens.PLAN) }.forEach { role ->
            val refused = repo.add(role, "job.1", entry(), "Somebody").exceptionOrNull() as JobContactRepository.Refused
            assertEquals(role.name, JobContactRepository.Refusal.NOT_ALLOWED, refused.refusal)
        }
    }
}
