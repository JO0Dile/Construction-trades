package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Party
import il.co.tradesmanager.core.money.Payments
import il.co.tradesmanager.core.work.Amendment
import il.co.tradesmanager.core.work.Assignment
import il.co.tradesmanager.data.local.entity.AssignmentEntity
import il.co.tradesmanager.data.local.entity.ContractEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The layer where the rules actually bite.
 *
 * `core` has tests for what the rules say. This has tests for whether the
 * repository asks them before it writes — which is a different question, and
 * the one that matters, because a rule the write path skips is not a rule.
 *
 * Every refusal here is asserted on the **stored row** as well as the result,
 * because "returned a failure" and "did not change the database" are not the
 * same thing, and only the second one is safety.
 */
class EngagementRepositoryTest {

    private val gc = "org.gc"
    private val first = "org.first"
    private val crew = "org.crew"
    private val stranger = "org.other"
    private val project = "proj.1"

    private lateinit var dao: FakeEngagementDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: EngagementRepository

    @Before
    fun setUp() {
        dao = FakeEngagementDao()
        audit = FakeAuditDao()
        repo = EngagementRepository(dao, AuditTrail(audit))
    }

    private suspend fun aPackage(
        payer: String = first,
        payee: String = crew,
        amount: Double = 7_000.0,
    ) = repo.createAssignment(
        projectId = project, contractId = null,
        payerOrgId = payer, payeeOrgId = payee,
        title = "Electrical rough-in, floors 1-7",
        stageId = "rough-in", scopeId = "electrical-rough",
        location = "Building A", amount = amount, actorName = "Foreman",
    )

    private suspend fun aContract(
        payer: String = gc,
        payee: String = first,
        amount: Double = 10_000.0,
    ) = repo.createContract(
        projectId = project, payerOrgId = payer, payeeOrgId = payee,
        title = "Main package", amount = amount,
        terms = Payments.Terms.SHOTEF_30,
        retentionRate = 0.05, retentionLimit = 0.05, actorName = "Director",
    )

    // ---- the chain -------------------------------------------------------

    @Test
    fun `a crew leader cannot appoint a general contractor beneath itself`() = runTest {
        val result = repo.engage(
            projectId = project, orgId = "org.new", orgName = "Somebody Ltd",
            party = Party.GENERAL_CONTRACTOR,
            engagedByOrgId = crew, engagedByParty = Party.SECOND_TIER,
            scopeSummary = null, actorName = "Crew leader",
        )
        assertTrue(result.isFailure)
        assertEquals(
            EngagementRepository.Refusal.NOT_YOURS_TO_ENGAGE,
            (result.exceptionOrNull() as EngagementRepository.Refused).refusal,
        )
        // Refused means nothing was written, not merely that a failure came back.
        assertTrue(dao.engagementRows().isEmpty())
    }

    @Test
    fun `a firm declaring its own position has nobody above it to check against`() = runTest {
        val result = repo.engage(
            projectId = project, orgId = first, orgName = "First Tier Ltd",
            party = Party.FIRST_TIER,
            engagedByOrgId = null, engagedByParty = null,
            scopeSummary = null, actorName = "Director",
        )
        assertTrue(result.isSuccess)
        assertEquals(1, dao.engagementRows().size)
    }

    // ---- who may move a package -----------------------------------------

    @Test
    fun `the crew accepts, and the payer cannot accept for it`() = runTest {
        val job = aPackage()
        repo.move(job, Assignment.Status.OFFERED, first, "Director")

        val byPayer = repo.move(
            dao.assignment(job.id)!!, Assignment.Status.ACCEPTED, first, "Director",
        )
        assertEquals(
            EngagementRepository.Refusal.WRONG_SIDE,
            (byPayer.exceptionOrNull() as EngagementRepository.Refused).refusal,
        )
        assertEquals(Assignment.Status.OFFERED, dao.assignment(job.id)!!.status)

        val byCrew = repo.move(
            dao.assignment(job.id)!!, Assignment.Status.ACCEPTED, crew, "Crew leader",
        )
        assertTrue(byCrew.isSuccess)
        assertEquals(Assignment.Status.ACCEPTED, dao.assignment(job.id)!!.status)
        assertNotNull(dao.assignment(job.id)!!.acceptedAt)
    }

    @Test
    fun `a crew cannot approve its own work`() = runTest {
        val job = submitted()
        val result = repo.move(job, Assignment.Status.APPROVED, crew, "Crew leader")
        assertEquals(
            EngagementRepository.Refusal.WRONG_SIDE,
            (result.exceptionOrNull() as EngagementRepository.Refused).refusal,
        )
        assertEquals(Assignment.Status.SUBMITTED, dao.assignment(job.id)!!.status)
    }

    @Test
    fun `a firm that is neither party cannot touch it at all`() = runTest {
        val job = aPackage()
        val result = repo.move(job, Assignment.Status.OFFERED, stranger, "Nobody")
        assertEquals(
            EngagementRepository.Refusal.NOT_A_PARTY,
            (result.exceptionOrNull() as EngagementRepository.Refused).refusal,
        )
        assertEquals(Assignment.Status.DRAFT, dao.assignment(job.id)!!.status)
    }

    // ---- evidence --------------------------------------------------------

    @Test
    fun `work cannot be submitted without a photograph`() = runTest {
        val job = inProgress()
        val none = repo.move(job, Assignment.Status.SUBMITTED, crew, "Crew leader", photoCount = 0)
        assertEquals(
            EngagementRepository.Refusal.NO_EVIDENCE,
            (none.exceptionOrNull() as EngagementRepository.Refused).refusal,
        )
        assertEquals(Assignment.Status.IN_PROGRESS, dao.assignment(job.id)!!.status)

        val withOne = repo.move(
            dao.assignment(job.id)!!, Assignment.Status.SUBMITTED, crew, "Crew leader",
            photoCount = 1,
        )
        assertTrue(withOne.isSuccess)
    }

    @Test
    fun `a rejection without a reason is refused, and the reason clears on rework`() = runTest {
        val job = submitted()
        val silent = repo.move(job, Assignment.Status.REJECTED, first, "Director", reason = "  ")
        assertEquals(
            EngagementRepository.Refusal.INCOMPLETE,
            (silent.exceptionOrNull() as EngagementRepository.Refused).refusal,
        )

        repo.move(
            job, Assignment.Status.REJECTED, first, "Director",
            reason = "Pipe alignment, room 102",
        )
        assertEquals("Pipe alignment, room 102", dao.assignment(job.id)!!.rejectionReason)

        // Back to work clears it, so a fixed complaint stops being shown.
        repo.move(dao.assignment(job.id)!!, Assignment.Status.IN_PROGRESS, crew, "Crew leader")
        assertNull(dao.assignment(job.id)!!.rejectionReason)
    }

    // ---- approval is not payment ----------------------------------------

    @Test
    fun `only the crew raises the claim, and only once`() = runTest {
        val job = approved()

        val byPayer = repo.markInvoiced(job, first, "Director")
        assertEquals(
            EngagementRepository.Refusal.WRONG_SIDE,
            (byPayer.exceptionOrNull() as EngagementRepository.Refused).refusal,
        )
        assertNull(dao.assignment(job.id)!!.invoicedAt)

        assertTrue(repo.markInvoiced(job, crew, "Crew leader").isSuccess)
        assertNotNull(dao.assignment(job.id)!!.invoicedAt)

        val twice = repo.markInvoiced(dao.assignment(job.id)!!, crew, "Crew leader")
        assertEquals(
            EngagementRepository.Refusal.WRONG_STATE,
            (twice.exceptionOrNull() as EngagementRepository.Refused).refusal,
        )
    }

    @Test
    fun `work that is only submitted cannot be invoiced`() = runTest {
        val job = submitted()
        val result = repo.markInvoiced(job, crew, "Crew leader")
        assertEquals(
            EngagementRepository.Refusal.WRONG_STATE,
            (result.exceptionOrNull() as EngagementRepository.Refused).refusal,
        )
    }

    // ---- confidentiality -------------------------------------------------

    @Test
    fun `a contract reaches its two parties and nobody else`() = runTest {
        aContract()
        assertEquals(1, repo.observeContracts(project, gc).first().size)
        assertEquals(1, repo.observeContracts(project, first).first().size)
        assertTrue(repo.observeContracts(project, crew).first().isEmpty())
        assertTrue(repo.observeContracts(project, stranger).first().isEmpty())
    }

    @Test
    fun `an id that is a prefix of a disclosed one is not disclosed to`() = runTest {
        // The bug the delimiters exist to stop: "org.a" must not match a list
        // that discloses to "org.abc".
        val contract = aContract()
        dao.upsert(contract.copy(disclosedToOrgIds = ",org.abc,"))
        assertTrue(repo.observeContracts(project, "org.a").first().isEmpty())
        assertEquals(1, repo.observeContracts(project, "org.abc").first().size)
    }

    // ---- amendments ------------------------------------------------------

    @Test
    fun `an unsigned contract cannot be amended`() = runTest {
        val contract = aContract()
        val result = repo.proposeAmendment(contract, 11_000.0, "Extra floor", gc, "Director")
        assertEquals(
            EngagementRepository.Refusal.WRONG_STATE,
            (result.exceptionOrNull() as EngagementRepository.Refused).refusal,
        )
        assertTrue(dao.amendmentRows().isEmpty())
    }

    @Test
    fun `nobody accepts their own amendment, and the counterparty can`() = runTest {
        val contract = signed()
        val proposal = repo.proposeAmendment(contract, 11_000.0, "Extra floor", gc, "Director")
            .getOrThrow()

        val own = repo.decideAmendment(contract, proposal, true, gc, "Director")
        assertEquals(
            EngagementRepository.Refusal.WRONG_SIDE,
            (own.exceptionOrNull() as EngagementRepository.Refused).refusal,
        )
        assertEquals(Amendment.Status.PROPOSED, dao.amendmentRows().single().status)

        assertTrue(repo.decideAmendment(contract, proposal, true, first, "Manager").isSuccess)
        assertEquals(Amendment.Status.ACCEPTED, dao.amendmentRows().single().status)
        assertEquals(11_000.0, repo.currentAmount(contract), 0.005)
    }

    @Test
    fun `one open amendment at a time`() = runTest {
        val contract = signed()
        repo.proposeAmendment(contract, 11_000.0, "Extra floor", gc, "Director").getOrThrow()
        val second = repo.proposeAmendment(contract, 12_000.0, "Another floor", gc, "Director")
        assertEquals(
            EngagementRepository.Refusal.ALREADY_OPEN,
            (second.exceptionOrNull() as EngagementRepository.Refused).refusal,
        )
        assertEquals(1, dao.amendmentRows().size)
    }

    @Test
    fun `a refused amendment leaves the figure where it was`() = runTest {
        val contract = signed()
        val proposal = repo.proposeAmendment(contract, 11_000.0, "Extra floor", gc, "Director")
            .getOrThrow()
        repo.decideAmendment(
            contract, proposal, accept = false, byOrgId = first, actorName = "Manager",
        )
        assertEquals(10_000.0, repo.currentAmount(contract), 0.005)
    }

    @Test
    fun `an outsider cannot propose against somebody else's contract`() = runTest {
        val contract = signed()
        val result = repo.proposeAmendment(contract, 11_000.0, "Because", stranger, "Nobody")
        assertEquals(
            EngagementRepository.Refusal.NOT_A_PARTY,
            (result.exceptionOrNull() as EngagementRepository.Refused).refusal,
        )
    }

    @Test
    fun `a contract signed by one side is an offer, not a contract`() = runTest {
        val contract = aContract()
        val once = repo.sign(contract, gc, "Director").getOrThrow()
        assertNull(once.signedAt)
        val twice = repo.sign(once, first, "Manager").getOrThrow()
        assertNotNull(twice.signedAt)
    }

    // ---- the trail -------------------------------------------------------

    @Test
    fun `every accepted move leaves an audit row and every refusal leaves none`() = runTest {
        val job = aPackage()
        val created = audit.entries.size
        // Wrong state: DRAFT cannot jump to APPROVED.
        repo.move(job, Assignment.Status.APPROVED, first, "Director")
        assertEquals("a refused write must not be logged as one", created, audit.entries.size)

        repo.move(job, Assignment.Status.OFFERED, first, "Director")
        assertTrue(audit.entries.size > created)
        assertTrue(audit.entries.last().summary.contains("DRAFT -> OFFERED"))
    }

    // ---- helpers ---------------------------------------------------------

    private suspend fun inProgress(): AssignmentEntity {
        val job = aPackage()
        repo.move(job, Assignment.Status.OFFERED, first, "Director")
        repo.move(dao.assignment(job.id)!!, Assignment.Status.ACCEPTED, crew, "Crew leader")
        repo.move(dao.assignment(job.id)!!, Assignment.Status.IN_PROGRESS, crew, "Crew leader")
        return dao.assignment(job.id)!!
    }

    private suspend fun submitted(): AssignmentEntity {
        val job = inProgress()
        repo.move(job, Assignment.Status.SUBMITTED, crew, "Crew leader", photoCount = 1)
        return dao.assignment(job.id)!!
    }

    private suspend fun approved(): AssignmentEntity {
        val job = submitted()
        repo.move(job, Assignment.Status.APPROVED, first, "Director")
        return dao.assignment(job.id)!!
    }

    private suspend fun signed(): ContractEntity {
        val contract = aContract()
        repo.sign(contract, gc, "Director")
        return repo.sign(dao.contractRows().single(), first, "Manager").getOrThrow()
    }
}
