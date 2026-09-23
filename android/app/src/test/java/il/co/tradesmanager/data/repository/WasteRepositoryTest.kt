package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.evidence.Waste
import il.co.tradesmanager.data.local.dao.WasteDao
import il.co.tradesmanager.data.local.entity.WasteLoadEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The waste table, in memory. */
class FakeWasteDao : WasteDao {
    val rows = MutableStateFlow<List<WasteLoadEntity>>(emptyList())

    override suspend fun upsert(load: WasteLoadEntity) {
        rows.value = rows.value.filterNot { it.id == load.id } + load
    }

    override suspend fun load(id: String): WasteLoadEntity? = rows.value.firstOrNull { it.id == id }

    override fun observeForProject(projectId: String): Flow<List<WasteLoadEntity>> =
        rows.map { all -> all.filter { it.projectId == projectId }.sortedByDescending { it.removedAt } }
}

class WasteRepositoryTest {

    private lateinit var dao: FakeWasteDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: WasteRepository

    @Before
    fun setUp() {
        dao = FakeWasteDao()
        audit = FakeAuditDao()
        repo = WasteRepository(dao, AuditTrail(audit))
    }

    private suspend fun record(
        role: Role = Role.MANAGER,
        stream: Waste.Stream = Waste.Stream.CONCRETE_AND_MASONRY,
        quantity: Double = 12.0,
        facility: String = "  Hiriya  ",
        ticket: String? = null,
    ) = repo.record(
        role = role,
        projectId = "job.1",
        stream = stream,
        quantity = quantity,
        unit = Waste.Measurement.TONNES,
        destination = Waste.Destination.RECYCLING,
        facility = facility,
        hauler = " ",
        ticketNumber = ticket,
        notes = null,
        byAccountId = "acc.1",
        byName = "Foreman",
    )

    @Test
    fun `a load is recorded, tidied, and audited with its words as keys`() = runTest {
        val load = record().getOrThrow()

        assertEquals("Hiriya", load.facility)
        assertNull("a blank haulier is no haulier", load.hauler)
        assertNull(load.ticketNumber)
        val parsed = Summary.parse(audit.entries.single().summary)!!
        assertEquals(Summaries.WASTE_RECORDED, parsed.key)
        assertEquals(listOf("12", "@waste_unit_tonnes", "@waste_stream_concrete_and_masonry", "Hiriya"), parsed.arguments)
    }

    @Test
    fun `a load with no ticket is recorded, and counts as unproven`() = runTest {
        val load = record().getOrThrow()

        assertFalse(WasteRepository.asLoad(load, ticketPhotos = 0)!!.proven)
        assertTrue("a photograph of the ticket is enough", WasteRepository.asLoad(load, ticketPhotos = 1)!!.proven)
    }

    @Test
    fun `the rules refuse on the write, and nothing is stored`() = runTest {
        fun reason(result: Result<WasteLoadEntity>) = (result.exceptionOrNull() as WasteRepository.Refused).refusal

        assertEquals(WasteRepository.Refusal.NO_QUANTITY, reason(record(quantity = 0.0)))
        assertEquals(WasteRepository.Refusal.NO_DESTINATION, reason(record(facility = " ")))
        assertEquals(
            WasteRepository.Refusal.HAZARDOUS_WITHOUT_TICKET,
            reason(record(stream = Waste.Stream.HAZARDOUS)),
        )
        assertEquals(WasteRepository.Refusal.NOT_ALLOWED, reason(record(role = Role.FINANCE)))
        assertTrue(dao.rows.value.isEmpty())
        assertTrue(audit.entries.isEmpty())
    }

    @Test
    fun `a ticket number found later can be added, once`() = runTest {
        val load = record().getOrThrow()

        val numbered = repo.addTicketNumber(load.id, " T-5521 ", "Foreman").getOrThrow()
        assertEquals("T-5521", numbered.ticketNumber)
        assertTrue(WasteRepository.asLoad(numbered, 0)!!.proven)

        val again = repo.addTicketNumber(load.id, "T-9999", "Foreman").exceptionOrNull()
        assertTrue("a number already on the record is not replaced", again is WasteRepository.Refused)
        assertEquals("T-5521", dao.load(load.id)!!.ticketNumber)
    }

    @Test
    fun `a stored row this version cannot read is left out rather than guessed`() = runTest {
        val load = record().getOrThrow().copy(stream = "SOMETHING_NEW")

        assertNull(WasteRepository.asLoad(load, 0))
    }
}
