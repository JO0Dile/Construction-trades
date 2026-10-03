package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.data.local.dao.DesignQueryDao
import il.co.tradesmanager.data.local.entity.DesignQueryEntity
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

/** The designer query log, in memory. */
class FakeDesignQueryDao : DesignQueryDao {
    val rows = MutableStateFlow<List<DesignQueryEntity>>(emptyList())

    override suspend fun upsert(query: DesignQueryEntity) {
        rows.value = rows.value.filterNot { it.id == query.id } + query
    }

    override suspend fun query(id: String): DesignQueryEntity? = rows.value.firstOrNull { it.id == id }

    override fun observeForProject(projectId: String): Flow<List<DesignQueryEntity>> =
        rows.map { all -> all.filter { it.projectId == projectId }.sortedBy { it.askedAt } }

    override suspend fun countForProject(projectId: String): Int = rows.value.count { it.projectId == projectId }

    override suspend fun all(): List<DesignQueryEntity> = rows.value

}

class DesignQueryRepositoryTest {

    private lateinit var dao: FakeDesignQueryDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: DesignQueryRepository
    private val zone = ZoneId.of("Asia/Jerusalem")

    @Before
    fun setUp() {
        dao = FakeDesignQueryDao()
        audit = FakeAuditDao()
        repo = DesignQueryRepository(dao, AuditTrail(audit))
    }

    private suspend fun raise(project: String = "job.1", role: Role = Role.MANAGER) = repo.raise(
        role = role,
        projectId = project,
        question = "  Beam clashes with the duct at grid C  ",
        askedOf = " Structural engineer ",
        drawingNumber = " ",
        neededBy = null,
        byName = "Foreman",
        now = 1_000_000L,
        zone = zone,
    )

    @Test
    fun `questions are numbered per job, tidied, and audited`() = runTest {
        val first = raise().getOrThrow()
        val second = raise().getOrThrow()
        val elsewhere = raise(project = "job.2").getOrThrow()

        assertEquals("Q-001", first.reference)
        assertEquals("Q-002", second.reference)
        assertEquals("Q-001", elsewhere.reference)
        assertEquals("Beam clashes with the duct at grid C", first.question)
        assertEquals("Structural engineer", first.askedOf)
        assertNull("a blank drawing number is none", first.drawingNumber)
        assertEquals(Summaries.QUERY_RAISED, Summary.parse(audit.entries.first().summary)!!.key)
    }

    @Test
    fun `an answer is recorded once, with who typed it`() = runTest {
        val id = raise().getOrThrow().id
        val answered = repo.answer(Role.MANAGER, id, " Drop the duct 200 ", "Office", now = 2_000_000L).getOrThrow()

        assertEquals("Drop the duct 200", answered.answer)
        assertEquals(2_000_000L, answered.answeredAt)
        assertEquals("Office", answered.answerRecordedByName)

        val again = repo.answer(Role.MANAGER, id, "Something else", "Office").exceptionOrNull() as DesignQueryRepository.Refused
        assertEquals(DesignQueryRepository.Refusal.ALREADY_ANSWERED, again.refusal)
        assertEquals("Drop the duct 200", dao.query(id)!!.answer)
    }

    @Test
    fun `somebody without the plan does not ask or answer`() = runTest {
        val refused = raise(role = Role.FINANCE).exceptionOrNull() as DesignQueryRepository.Refused
        assertEquals(DesignQueryRepository.Refusal.NOT_ALLOWED, refused.refusal)
        assertTrue(dao.rows.value.isEmpty())
    }
}
