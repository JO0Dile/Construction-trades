package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.data.local.dao.DrawingDao
import il.co.tradesmanager.data.local.entity.DrawingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The drawing register, in memory. */
class FakeDrawingDao : DrawingDao {
    val rows = MutableStateFlow<List<DrawingEntity>>(emptyList())

    override suspend fun upsert(drawing: DrawingEntity) {
        rows.value = rows.value.filterNot { it.id == drawing.id } + drawing
    }

    override fun observeForProject(projectId: String): Flow<List<DrawingEntity>> =
        rows.map { all -> all.filter { it.projectId == projectId }.sortedByDescending { it.receivedAt } }

    override suspend fun forProject(projectId: String): List<DrawingEntity> =
        rows.value.filter { it.projectId == projectId }

    override suspend fun supersede(previous: DrawingEntity?, current: DrawingEntity) {
        previous?.let { upsert(it) }
        upsert(current)
    }

    override suspend fun all(): List<DrawingEntity> = rows.value

}

class DrawingRepositoryTest {

    private lateinit var dao: FakeDrawingDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: DrawingRepository

    @Before
    fun setUp() {
        dao = FakeDrawingDao()
        audit = FakeAuditDao()
        repo = DrawingRepository(dao, AuditTrail(audit))
    }

    private suspend fun record(revision: String, at: Long, title: String = "", number: String = "A-101", role: Role = Role.MANAGER) =
        repo.record(role, "job.1", number, title, revision, notes = " ", byName = "Office", now = at)

    @Test
    fun `a new revision supersedes the current one in the same step`() = runTest {
        val first = record("B", at = 10, title = " Ground floor plan ").getOrThrow()
        val second = record("C", at = 20, number = "a101").getOrThrow()

        assertEquals("Ground floor plan", first.title)
        assertEquals("a new revision keeps its drawing's title", "Ground floor plan", second.title)
        assertNull(second.supersededAt)
        assertEquals(20L, dao.rows.value.first { it.id == first.id }.supersededAt)
        assertEquals(1, dao.rows.value.count { it.supersededAt == null })

        val summary = Summary.parse(audit.entries.last().summary)!!
        assertEquals(Summaries.DRAWING_SUPERSEDED, summary.key)
        assertEquals(listOf("a101", "C", "B"), summary.arguments)
    }

    @Test
    fun `recording the current revision again changes nothing`() = runTest {
        record("B", at = 10).getOrThrow()
        val again = record(" b ", at = 20).exceptionOrNull() as DrawingRepository.Refused

        assertEquals(DrawingRepository.Refusal.ALREADY_CURRENT, again.refusal)
        assertEquals(1, dao.rows.value.size)
        assertNull(dao.rows.value.single().supersededAt)
    }

    @Test
    fun `somebody without the plan does not record drawings`() = runTest {
        val refused = record("A", at = 1, role = Role.SAFETY_OFFICER).exceptionOrNull() as DrawingRepository.Refused
        assertEquals(DrawingRepository.Refusal.NOT_ALLOWED, refused.refusal)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `a first revision is received, not replacing anything`() = runTest {
        record("A", at = 1).getOrThrow()
        assertEquals(Summaries.DRAWING_RECEIVED, Summary.parse(audit.entries.single().summary)!!.key)
    }
}
