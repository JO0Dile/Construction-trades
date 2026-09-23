package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.safety.Muster
import il.co.tradesmanager.data.local.entity.TimeEntryEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Whether the roll call asks the rules before it writes.
 *
 * `MusterTest` covers what the rules say. This covers the write path: that a
 * refusal leaves the stored rows alone, that ending one records the names
 * rather than only a count, and that the list a roll call starts with is the
 * open check-ins and not something the caller handed in.
 */
class MusterRepositoryTest {

    private val hour = 60L * 60L * 1000L

    private lateinit var dao: FakeMusterDao
    private lateinit var schedule: FakeScheduleDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: MusterRepository

    @Before
    fun setUp() {
        dao = FakeMusterDao()
        schedule = FakeScheduleDao()
        audit = FakeAuditDao()
        repo = MusterRepository(dao, schedule, AuditTrail(audit))
    }

    private fun checkedIn(
        id: String,
        worker: String?,
        name: String,
        agoHours: Long,
        job: String? = null,
    ) {
        schedule.timeEntries += TimeEntryEntity(
            id = id,
            projectId = job,
            workerId = worker,
            workerName = name,
            checkInAt = System.currentTimeMillis() - agoHours * hour,
        )
    }

    private fun checkedOut(id: String, worker: String, name: String) {
        schedule.timeEntries += TimeEntryEntity(
            id = id,
            workerId = worker,
            workerName = name,
            checkInAt = System.currentTimeMillis() - 8 * hour,
            checkOutAt = System.currentTimeMillis() - hour,
        )
    }

    private suspend fun start(role: Role = Role.SAFETY_OFFICER) = repo.start(
        role = role,
        companyId = "co.1",
        reason = Muster.Reason.EMERGENCY,
        note = " gas smell, floor 3 ",
        byAccountId = "acc.1",
        byName = "Foreman",
    )

    @Test
    fun `the list is the open check-ins, and nobody who has clocked out`() = runTest {
        checkedIn("t1", "w1", "Ahmad", agoHours = 3)
        checkedIn("t2", "w2", "Yossi", agoHours = 40)
        checkedOut("t3", "w3", "Gone home")

        val muster = start().getOrThrow()
        val people = dao.people(muster.id)

        assertEquals(setOf("Ahmad", "Yossi"), people.map { it.name }.toSet())
        assertTrue(people.all { it.state == Muster.State.UNACCOUNTED.name })
        assertTrue("the forty-hour check-in is flagged", people.first { it.name == "Yossi" }.staleCheckIn)
        assertFalse(people.first { it.name == "Ahmad" }.staleCheckIn)
    }

    @Test
    fun `the site comes from the check-ins when they agree, and is blank when they do not`() =
        runTest {
            checkedIn("t1", "w1", "Ahmad", agoHours = 3, job = "site.a")
            checkedIn("t2", "w2", "Yossi", agoHours = 2, job = "site.a")

            assertEquals("site.a", start().getOrThrow().projectId)

            repo.end(dao.liveNow()!!.id, "Foreman").getOrThrow()
            checkedIn("t3", "w3", "Dani", agoHours = 1, job = "site.b")

            assertNull("two sites is not one site", start().getOrThrow().projectId)
        }

    @Test
    fun `a roll call with nobody checked in still starts`() = runTest {
        val muster = start().getOrThrow()

        assertTrue(dao.people(muster.id).isEmpty())
        assertNotNull(dao.liveNow())
    }

    @Test
    fun `an office role cannot start one, and nothing is written`() = runTest {
        checkedIn("t1", "w1", "Ahmad", agoHours = 3)

        val refused = start(Role.FINANCE).exceptionOrNull() as MusterRepository.Refused

        assertEquals(MusterRepository.Refusal.NOT_ALLOWED, refused.refusal)
        assertNull(dao.liveNow())
        assertTrue(audit.entries.isEmpty())
    }

    @Test
    fun `anybody on site can start one`() = runTest {
        assertTrue(start(Role.WORKER).isSuccess)
    }

    @Test
    fun `a second roll call is refused while one is running`() = runTest {
        start().getOrThrow()

        val refused = start().exceptionOrNull() as MusterRepository.Refused

        assertEquals(MusterRepository.Refusal.ALREADY_RUNNING, refused.refusal)
        assertEquals(1, audit.entries.size)
    }

    @Test
    fun `starting one is recorded with the reason and the head count`() = runTest {
        checkedIn("t1", "w1", "Ahmad", agoHours = 3)
        checkedIn("t2", "w2", "Yossi", agoHours = 2)

        val muster = start().getOrThrow()

        val entry = audit.entries.single()
        assertEquals(AuditTrail.Action.CREATE, entry.action)
        val parsed = Summary.parse(entry.summary)!!
        assertEquals(Summaries.MUSTER_STARTED, parsed.key)
        assertEquals("muster_reason_emergency", Summary.nested(parsed.arguments[0]))
        assertEquals("2", parsed.arguments[1])
        assertEquals("gas smell, floor 3", dao.muster(muster.id)!!.note)
    }

    @Test
    fun `safe elsewhere needs an account, and the row does not move without one`() = runTest {
        checkedIn("t1", "w1", "Ahmad", agoHours = 3)
        val muster = start().getOrThrow()
        val row = dao.people(muster.id).single()

        val refused = repo.settle(row.id, Muster.State.ACCOUNTED_ELSEWHERE, "  ")
            .exceptionOrNull() as MusterRepository.Refused

        assertEquals(MusterRepository.Refusal.NO_ACCOUNT_GIVEN, refused.refusal)
        assertEquals(Muster.State.UNACCOUNTED.name, dao.person(row.id)!!.state)

        repo.settle(row.id, Muster.State.ACCOUNTED_ELSEWHERE, "rang him, left at four").getOrThrow()
        assertEquals("rang him, left at four", dao.person(row.id)!!.account)
    }

    @Test
    fun `present is recorded without an account, and can be undone`() = runTest {
        checkedIn("t1", "w1", "Ahmad", agoHours = 3)
        val muster = start().getOrThrow()
        val row = dao.people(muster.id).single()

        repo.settle(row.id, Muster.State.PRESENT).getOrThrow()
        assertEquals(Muster.State.PRESENT.name, dao.person(row.id)!!.state)
        assertNull(dao.person(row.id)!!.account)
        assertNotNull(dao.person(row.id)!!.settledAt)

        repo.settle(row.id, Muster.State.UNACCOUNTED).getOrThrow()
        assertEquals(Muster.State.UNACCOUNTED.name, dao.person(row.id)!!.state)
        assertNull(dao.person(row.id)!!.settledAt)
    }

    @Test
    fun `somebody added at the gate starts unaccounted and is recorded`() = runTest {
        val muster = start().getOrThrow()

        val added = repo.add(muster.id, "  Delivery driver ", "Foreman").getOrThrow()

        assertEquals("Delivery driver", added.name)
        assertEquals(Muster.State.UNACCOUNTED.name, added.state)
        assertTrue(added.addedDuringRollCall)
        assertEquals(
            Summary.of(Summaries.MUSTER_PERSON_ADDED, "Delivery driver"),
            audit.entries.last().summary,
        )
    }

    @Test
    fun `a blank name is refused and adds no row`() = runTest {
        val muster = start().getOrThrow()

        val refused = repo.add(muster.id, "   ", "Foreman").exceptionOrNull()
            as MusterRepository.Refused

        assertEquals(MusterRepository.Refusal.BLANK_NAME, refused.refusal)
        assertTrue(dao.people(muster.id).isEmpty())
    }

    @Test
    fun `ending with somebody missing records the names, not just the count`() = runTest {
        checkedIn("t1", "w1", "Ahmad", agoHours = 3)
        checkedIn("t2", "w2", "Yossi", agoHours = 2)
        val muster = start().getOrThrow()
        val yossi = dao.people(muster.id).first { it.name == "Yossi" }
        repo.settle(yossi.id, Muster.State.PRESENT).getOrThrow()

        val ended = repo.end(muster.id, "Foreman").getOrThrow()

        assertFalse(ended.everybodyOut)
        assertEquals(listOf("Ahmad"), ended.unaccounted.map { it.name })
        assertEquals(1, dao.muster(muster.id)!!.unaccountedAtEnd)
        assertNotNull(dao.muster(muster.id)!!.endedAt)
        val parsed = Summary.parse(audit.entries.last().summary)!!
        assertEquals(Summaries.MUSTER_ENDED_MISSING, parsed.key)
        assertEquals("1", parsed.arguments[0])
        assertEquals("Ahmad", parsed.arguments[1])
    }

    @Test
    fun `ending with everybody out says so, and zero is not null`() = runTest {
        checkedIn("t1", "w1", "Ahmad", agoHours = 3)
        val muster = start().getOrThrow()
        repo.settle(dao.people(muster.id).single().id, Muster.State.PRESENT).getOrThrow()

        val ended = repo.end(muster.id, "Foreman").getOrThrow()

        assertTrue(ended.everybodyOut)
        assertEquals(0, dao.muster(muster.id)!!.unaccountedAtEnd)
        assertEquals(
            Summary.of(Summaries.MUSTER_ENDED_ALL_OUT),
            audit.entries.last().summary,
        )
    }

    @Test
    fun `an ended roll call cannot be edited or ended again`() = runTest {
        checkedIn("t1", "w1", "Ahmad", agoHours = 3)
        val muster = start().getOrThrow()
        val row = dao.people(muster.id).single()
        repo.end(muster.id, "Foreman").getOrThrow()
        val recorded = audit.entries.size

        assertEquals(
            MusterRepository.Refusal.ALREADY_ENDED,
            (repo.settle(row.id, Muster.State.PRESENT).exceptionOrNull()
                as MusterRepository.Refused).refusal,
        )
        assertEquals(
            MusterRepository.Refusal.ALREADY_ENDED,
            (repo.add(muster.id, "Late arrival", "Foreman").exceptionOrNull()
                as MusterRepository.Refused).refusal,
        )
        assertEquals(
            MusterRepository.Refusal.ALREADY_ENDED,
            (repo.end(muster.id, "Foreman").exceptionOrNull()
                as MusterRepository.Refused).refusal,
        )
        assertEquals(Muster.State.UNACCOUNTED.name, dao.person(row.id)!!.state)
        assertEquals("nothing more was recorded", recorded, audit.entries.size)
    }

    @Test
    fun `once one has ended another can start`() = runTest {
        val first = start().getOrThrow()
        repo.end(first.id, "Foreman").getOrThrow()

        assertTrue(start().isSuccess)
    }

    @Test
    fun `settling a row that does not exist is refused`() = runTest {
        assertEquals(
            MusterRepository.Refusal.UNKNOWN,
            (repo.settle("nobody", Muster.State.PRESENT).exceptionOrNull()
                as MusterRepository.Refused).refusal,
        )
    }

    @Test
    fun `a state nobody recognises reads back as unaccounted, never as safe`() = runTest {
        checkedIn("t1", "w1", "Ahmad", agoHours = 3)
        val muster = start().getOrThrow()
        val row = dao.people(muster.id).single()
        dao.upsertPerson(row.copy(state = "SOMETHING_ELSE"))

        val roll = repo.rollOf(dao.muster(muster.id)!!, dao.people(muster.id))

        assertEquals(1, roll.unaccountedCount)
        assertFalse(roll.everyoneAccountedFor)
    }
}
