package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.data.local.dao.RiskDao
import il.co.tradesmanager.data.local.entity.RiskAssessmentEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The risk register, in memory. */
class FakeRiskDao : RiskDao {
    val rows = MutableStateFlow<List<RiskAssessmentEntity>>(emptyList())

    override suspend fun upsert(risk: RiskAssessmentEntity) {
        rows.value = rows.value.filterNot { it.id == risk.id } + risk
    }

    override suspend fun risk(id: String): RiskAssessmentEntity? = rows.value.firstOrNull { it.id == id }

    override fun observeForProject(projectId: String): Flow<List<RiskAssessmentEntity>> =
        rows.map { all -> all.filter { it.projectId == projectId }.sortedBy { it.createdAt } }

    override suspend fun countForProject(projectId: String): Int = rows.value.count { it.projectId == projectId }

    override suspend fun all(): List<RiskAssessmentEntity> = rows.value
}

class RiskRepositoryTest {

    private lateinit var dao: FakeRiskDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: RiskRepository
    private val today = LocalDate.of(2026, 10, 1)

    @Before
    fun setUp() {
        dao = FakeRiskDao()
        audit = FakeAuditDao()
        repo = RiskRepository(dao, AuditTrail(audit))
    }

    private fun assessment(
        controls: String = " Edge protection ",
        likelihoodAfter: Int = 2,
        reviewOn: LocalDate? = today.plusDays(30),
    ) = RiskRepository.Assessment(
        activity = "  Formwork at the slab edge  ",
        hazard = " Fall from height ",
        whoAtRisk = " ",
        likelihoodBefore = 4,
        severityBefore = 5,
        controls = controls,
        likelihoodAfter = likelihoodAfter,
        severityAfter = 5,
        ownerName = " Site manager ",
        reviewOn = reviewOn,
    )

    private fun lastSummary() = Summary.parse(audit.entries.last().summary)!!

    @Test
    fun `an assessment is numbered per job, tidied, and audited with its residual score`() = runTest {
        val first = repo.assess(Role.SAFETY_OFFICER, "job.1", assessment(), "Safety officer", today).getOrThrow()
        val second = repo.assess(Role.SAFETY_OFFICER, "job.1", assessment(), "Safety officer", today).getOrThrow()
        assertEquals("RA-001", first.reference)
        assertEquals("RA-002", second.reference)
        assertEquals("Formwork at the slab edge", first.activity)
        assertNull("blank is none", first.whoAtRisk)
        assertEquals("Site manager", first.ownerName)
        assertEquals(today.plusDays(30).toEpochDay(), first.reviewOnDay)
        assertEquals(Summaries.RA_ASSESSED, lastSummary().key)
        assertEquals(listOf("RA-002", "10"), lastSummary().arguments)
    }

    @Test
    fun `a risk that comes down with no controls is refused`() = runTest {
        val refused = repo.assess(Role.SAFETY_OFFICER, "job.1", assessment(controls = " "), "Safety officer", today)
            .exceptionOrNull() as RiskRepository.Refused
        assertEquals(RiskRepository.Refusal.NO_CONTROLS, refused.refusal)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `a review rescores the row and the trail keeps the scores before and after`() = runTest {
        val id = repo.assess(Role.SAFETY_OFFICER, "job.1", assessment(), "Safety officer", today).getOrThrow().id
        val reviewed = repo.review(Role.SAFETY_OFFICER, id, assessment(likelihoodAfter = 1), "Safety officer", today, now = 9L).getOrThrow()
        assertEquals(1, reviewed.likelihoodAfter)
        assertEquals(9L, reviewed.lastReviewedAt)
        assertEquals("Safety officer", reviewed.lastReviewedByName)
        assertEquals(Summaries.RA_REVIEWED, lastSummary().key)
        assertEquals(listOf("RA-001", "10", "5"), lastSummary().arguments)
    }

    @Test
    fun `a closed risk stays on the record and is not reviewed again`() = runTest {
        val id = repo.assess(Role.SAFETY_OFFICER, "job.1", assessment(), "Safety officer", today).getOrThrow().id
        assertTrue(repo.close(Role.SAFETY_OFFICER, id, "Safety officer").getOrThrow().closed)
        assertEquals(Summaries.RA_CLOSED, lastSummary().key)
        val again = repo.review(Role.SAFETY_OFFICER, id, assessment(), "Safety officer", today).exceptionOrNull() as RiskRepository.Refused
        assertEquals(RiskRepository.Refusal.ALREADY_CLOSED, again.refusal)
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun `somebody who may only read the site's record assesses nothing`() = runTest {
        val refused = repo.assess(Role.HR, "job.1", assessment(), "HR", today).exceptionOrNull() as RiskRepository.Refused
        assertEquals(RiskRepository.Refusal.NOT_ALLOWED, refused.refusal)
    }
}
