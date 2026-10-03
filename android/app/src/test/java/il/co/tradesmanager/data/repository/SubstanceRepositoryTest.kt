package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.safety.Substances
import il.co.tradesmanager.data.local.dao.SubstanceDao
import il.co.tradesmanager.data.local.entity.SubstanceEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** The hazardous substances register, in memory. */
class FakeSubstanceDao : SubstanceDao {
    val rows = MutableStateFlow<List<SubstanceEntity>>(emptyList())

    override suspend fun upsert(substance: SubstanceEntity) {
        rows.value = rows.value.filterNot { it.id == substance.id } + substance
    }

    override suspend fun substance(id: String): SubstanceEntity? = rows.value.firstOrNull { it.id == id }

    override fun observeForProject(projectId: String): Flow<List<SubstanceEntity>> =
        rows.map { all -> all.filter { it.projectId == projectId }.sortedBy { it.addedAt } }

    override suspend fun countForProject(projectId: String): Int = rows.value.count { it.projectId == projectId }

    override suspend fun all(): List<SubstanceEntity> = rows.value
}

class SubstanceRepositoryTest {

    private lateinit var dao: FakeSubstanceDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: SubstanceRepository

    private val today = LocalDate.of(2026, 10, 8)

    @Before
    fun setUp() {
        dao = FakeSubstanceDao()
        audit = FakeAuditDao()
        repo = SubstanceRepository(dao, AuditTrail(audit))
    }

    private fun arrival(sheetOn: LocalDate? = LocalDate.of(2024, 5, 1), hazards: Set<Substances.Hazard> = setOf(Substances.Hazard.FLAMMABLE, Substances.Hazard.HARMFUL)) =
        SubstanceRepository.Arrival(
            name = "  Diesel  ",
            supplier = " ",
            hazards = hazards,
            keptWhere = " The locked cage by the gate ",
            quantity = "200 l drum",
            precautions = " Gloves, no smoking ",
            firstAid = "",
            sheetOn = sheetOn,
        )

    private suspend fun add(role: Role = Role.SAFETY_OFFICER, project: String = "job.1", sheetOn: LocalDate? = LocalDate.of(2024, 5, 1)) =
        repo.add(role, project, arrival(sheetOn), "Safety officer", today = today, now = 1_000L)

    @Test
    fun `substances are numbered per job, tidied, and audited with what they are`() = runTest {
        val first = add().getOrThrow()
        val second = add().getOrThrow()
        val elsewhere = add(project = "job.2").getOrThrow()
        assertEquals("HS-001", first.reference)
        assertEquals("HS-002", second.reference)
        assertEquals("HS-001", elsewhere.reference)
        assertEquals("Diesel", first.name)
        assertNull("a blank supplier is none", first.supplier)
        assertNull("a blank first aid is none", first.firstAid)
        assertEquals("The locked cage by the gate", first.keptWhere)
        assertEquals("FLAMMABLE,HARMFUL", first.hazards)
        assertEquals(LocalDate.of(2024, 5, 1).toEpochDay(), first.sheetOnDay)
        val parsed = Summary.parse(audit.entries.first().summary)!!
        assertEquals(Summaries.HS_ADDED, parsed.key)
        assertEquals(listOf("HS-001", "Diesel"), parsed.arguments)
    }

    @Test
    fun `a substance with no hazard is refused`() = runTest {
        val refused = repo.add(Role.SAFETY_OFFICER, "job.1", arrival(hazards = emptySet()), "Safety officer", today = today)
            .exceptionOrNull() as SubstanceRepository.Refused
        assertEquals(SubstanceRepository.Refusal.NO_HAZARD, refused.refusal)
        assertEquals(0, dao.rows.value.size)
    }

    @Test
    fun `a newer sheet replaces the date on file, and one no newer is refused`() = runTest {
        val id = add(sheetOn = null).getOrThrow().id
        val first = repo.newSheet(Role.SAFETY_OFFICER, id, LocalDate.of(2023, 1, 1), "Safety officer", today = today).getOrThrow()
        assertEquals(LocalDate.of(2023, 1, 1).toEpochDay(), first.sheetOnDay)
        val parsed = Summary.parse(audit.entries.last().summary)!!
        assertEquals(Summaries.HS_SHEET, parsed.key)
        assertEquals(listOf("HS-001", "2023-01-01"), parsed.arguments)
        val older = repo.newSheet(Role.SAFETY_OFFICER, id, LocalDate.of(2022, 1, 1), "Safety officer", today = today)
            .exceptionOrNull() as SubstanceRepository.Refused
        assertEquals(SubstanceRepository.Refusal.SHEET_NOT_NEWER, older.refusal)
    }

    @Test
    fun `taken off the site is a date on the row, once, and nothing more is recorded after it`() = runTest {
        val id = add().getOrThrow().id
        val removed = repo.remove(Role.SAFETY_OFFICER, id, "Site manager", now = 9_000L).getOrThrow()
        assertEquals(9_000L, removed.removedAt)
        assertEquals("Site manager", removed.removedByName)
        assertEquals(1, dao.rows.value.size)
        assertEquals(Summaries.HS_REMOVED, Summary.parse(audit.entries.last().summary)!!.key)
        val again = repo.remove(Role.SAFETY_OFFICER, id, "Site manager").exceptionOrNull() as SubstanceRepository.Refused
        assertEquals(SubstanceRepository.Refusal.ALREADY_REMOVED, again.refusal)
        val sheet = repo.newSheet(Role.SAFETY_OFFICER, id, today, "Safety officer", today = today).exceptionOrNull() as SubstanceRepository.Refused
        assertEquals(SubstanceRepository.Refusal.ALREADY_REMOVED, sheet.refusal)
    }

    @Test
    fun `somebody who may only read the site's record records nothing`() = runTest {
        val refused = add(role = Role.FINANCE).exceptionOrNull() as SubstanceRepository.Refused
        assertEquals(SubstanceRepository.Refusal.NOT_ALLOWED, refused.refusal)
    }
}
