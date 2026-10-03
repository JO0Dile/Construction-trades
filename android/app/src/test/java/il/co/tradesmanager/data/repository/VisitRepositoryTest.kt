package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VisitRepositoryTest {

    private lateinit var dao: FakeVisitDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: VisitRepository

    @Before
    fun setUp() {
        dao = FakeVisitDao()
        audit = FakeAuditDao()
        repo = VisitRepository(dao, AuditTrail(audit))
    }

    private suspend fun signIn(role: Role = Role.WORKER, name: String = "  Dana Levi ") = repo.signIn(
        role = role,
        projectId = "job.1",
        companyId = "co.1",
        name = name,
        organisation = " Council ",
        phone = " ",
        hostName = null,
        briefed = false,
        signature = "",
        byAccountId = "acc.gate",
        byName = "Gate",
        now = 1_000L,
    )

    @Test
    fun `a visitor is signed in, tidied, and audited by name`() = runTest {
        val visit = signIn().getOrThrow()

        assertEquals("Dana Levi", visit.name)
        assertEquals("Council", visit.organisation)
        assertNull("a blank phone is no phone", visit.phone)
        assertNull("an empty pad is no signature", visit.signature)
        assertNull(visit.leftAt)
        assertEquals(listOf(visit), dao.stillHere())

        val parsed = Summary.parse(audit.entries.single().summary)!!
        assertEquals(Summaries.VISITOR_SIGNED_IN, parsed.key)
        assertEquals(listOf("Dana Levi"), parsed.arguments)
    }

    @Test
    fun `a visitor needs a name and nothing else`() = runTest {
        val refused = signIn(name = "   ").exceptionOrNull() as VisitRepository.Refused
        assertEquals(VisitRepository.Refusal.BLANK_NAME, refused.refusal)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `somebody who only reads the record does not sign people in`() = runTest {
        val refused = signIn(role = Role.FINANCE).exceptionOrNull() as VisitRepository.Refused
        assertEquals(VisitRepository.Refusal.NOT_ALLOWED, refused.refusal)
    }

    @Test
    fun `signing out takes them off the roll call, once`() = runTest {
        val id = signIn().getOrThrow().id

        val out = repo.signOut(Role.WORKER, id, "Gate", now = 5_000L).getOrThrow()
        assertEquals(5_000L, out.leftAt)
        assertTrue(dao.stillHere().isEmpty())

        val again = repo.signOut(Role.WORKER, id, "Gate", now = 9_000L).exceptionOrNull() as VisitRepository.Refused
        assertEquals(VisitRepository.Refusal.ALREADY_LEFT, again.refusal)
        assertEquals("the time they left is not moved", 5_000L, dao.visit(id)!!.leftAt)
        assertEquals(Summaries.VISITOR_SIGNED_OUT, Summary.parse(audit.entries.last().summary)!!.key)
    }
}
