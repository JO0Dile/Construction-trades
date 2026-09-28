package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.safety.Examinations
import il.co.tradesmanager.data.local.dao.PlantExaminationDao
import il.co.tradesmanager.data.local.entity.PlantExaminationEntity
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

/** Examination certificates, in memory. */
class FakePlantExaminationDao : PlantExaminationDao {
    val rows = MutableStateFlow<List<PlantExaminationEntity>>(emptyList())

    override suspend fun upsert(examination: PlantExaminationEntity) {
        rows.value = rows.value.filterNot { it.id == examination.id } + examination
    }

    override fun observeLatest(): Flow<List<PlantExaminationEntity>> = rows.map { all ->
        all.groupBy { it.equipmentId }.values.map { exams ->
            exams.sortedWith(compareByDescending<PlantExaminationEntity> { it.examinedOnDay }.thenByDescending { it.recordedAt }).first()
        }
    }

    override fun observeFor(equipmentId: String): Flow<List<PlantExaminationEntity>> =
        rows.map { all -> all.filter { it.equipmentId == equipmentId }.sortedByDescending { it.examinedOnDay } }
}

class PlantExaminationRepositoryTest {

    private lateinit var dao: FakePlantExaminationDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: PlantExaminationRepository
    private val stopped = mutableListOf<String>()
    private val today = LocalDate.of(2026, 10, 1)

    @Before
    fun setUp() {
        dao = FakePlantExaminationDao()
        audit = FakeAuditDao()
        stopped.clear()
        repo = PlantExaminationRepository(dao, AuditTrail(audit)) { id, _ -> stopped += id }
    }

    private suspend fun record(
        result: Examinations.Result = Examinations.Result.PASSED,
        notes: String = "",
        role: Role = Role.MANAGER,
        nextDue: LocalDate? = today.plusMonths(12),
    ) = repo.record(
        role = role,
        equipmentId = "crane.1",
        equipmentName = "Tower crane",
        examinedOn = today,
        examinerName = "  Avi Mizrahi  ",
        certificateNumber = " ",
        result = result,
        nextDueOn = nextDue,
        notes = notes,
        byName = "Yard manager",
        today = today,
    )

    @Test
    fun `a pass is recorded with its next date, tidied, and audited against the machine`() = runTest {
        val exam = record().getOrThrow()
        assertEquals("Avi Mizrahi", exam.examinerName)
        assertNull("a blank certificate number is none", exam.certificateNumber)
        assertEquals(today.plusMonths(12).toEpochDay(), exam.nextDueDay)
        assertEquals("PASSED", exam.result)
        val parsed = Summary.parse(audit.entries.last().summary)!!
        assertEquals(Summaries.PE_PASSED, parsed.key)
        assertEquals(listOf("Tower crane", "Avi Mizrahi"), parsed.arguments)
        assertTrue("a pass leaves the machine alone", stopped.isEmpty())
    }

    @Test
    fun `a failure needs its reason, has no next date, and takes the machine out of service`() = runTest {
        val refused = record(result = Examinations.Result.FAILED).exceptionOrNull() as PlantExaminationRepository.Refused
        assertEquals(PlantExaminationRepository.Refusal.FAILED_WITHOUT_REASON, refused.refusal)
        assertTrue(stopped.isEmpty())

        val failed = record(result = Examinations.Result.FAILED, notes = "Worn hoist rope").getOrThrow()
        assertNull("a failed machine has to pass before it has a next date", failed.nextDueDay)
        assertEquals(listOf("crane.1"), stopped)
        assertEquals(Summaries.PE_FAILED, Summary.parse(audit.entries.last().summary)!!.key)
    }

    @Test
    fun `somebody who does not keep the plant register records nothing`() = runTest {
        val refused = record(role = Role.HR).exceptionOrNull() as PlantExaminationRepository.Refused
        assertEquals(PlantExaminationRepository.Refusal.NOT_ALLOWED, refused.refusal)
        assertTrue(dao.rows.value.isEmpty())
    }
}
