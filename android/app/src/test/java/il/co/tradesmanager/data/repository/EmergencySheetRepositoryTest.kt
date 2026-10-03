package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.safety.EmergencySheet
import il.co.tradesmanager.data.local.dao.JobEmergencyDao
import il.co.tradesmanager.data.local.entity.JobEmergencyEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The emergency sheets, in memory. */
class FakeJobEmergencyDao : JobEmergencyDao {
    val rows = MutableStateFlow<Map<String, JobEmergencyEntity>>(emptyMap())

    override suspend fun upsert(sheet: JobEmergencyEntity) {
        rows.value = rows.value + (sheet.projectId to sheet)
    }

    override suspend fun sheet(projectId: String): JobEmergencyEntity? = rows.value[projectId]

    override fun observe(projectId: String): Flow<JobEmergencyEntity?> = rows.map { it[projectId] }
}

class EmergencySheetRepositoryTest {

    private lateinit var dao: FakeJobEmergencyDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: EmergencySheetRepository

    @Before
    fun setUp() {
        dao = FakeJobEmergencyDao()
        audit = FakeAuditDao()
        repo = EmergencySheetRepository(dao, AuditTrail(audit))
    }

    @Test
    fun `saving replaces the job's one sheet, tidied, and the audit says how much was still missing`() = runTest {
        repo.save(Role.SAFETY_OFFICER, "job.1", EmergencySheet.Sheet(assemblyPoint = " Car park "), "Safety officer", now = 1_000L).getOrThrow()
        val second = repo.save(
            Role.SAFETY_OFFICER, "job.1",
            EmergencySheet.Sheet(hospitalName = "Soroka", assemblyPoint = "Car park", firstAiders = "Yossi", siteContactPhone = "054-1234567", gasShutOff = " "),
            "Site manager", now = 2_000L,
        ).getOrThrow()
        assertEquals(1, dao.rows.value.size)
        assertEquals("Soroka", dao.sheet("job.1")?.hospitalName)
        assertNull("blank is stored as nothing", second.gasShutOff)
        assertEquals("Site manager", second.updatedByName)
        val first = Summary.parse(audit.entries.first().summary)!!
        assertEquals(Summaries.ES_SAVED, first.key)
        assertEquals(listOf("3"), first.arguments)
        assertEquals(listOf("0"), Summary.parse(audit.entries.last().summary)!!.arguments)
        assertTrue(EmergencySheet.missing(EmergencySheetRepository.sheetOf(second)).isEmpty())
    }

    @Test
    fun `a phone field that is not a number is refused, and nothing is saved`() = runTest {
        val refused = repo.save(Role.SAFETY_OFFICER, "job.1", EmergencySheet.Sheet(hospitalPhone = "ask at the gate"), "Safety officer")
            .exceptionOrNull() as EmergencySheetRepository.Refused
        assertEquals(EmergencySheetRepository.Refusal.NOT_A_PHONE_NUMBER, refused.refusal)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun `everybody reads it, and only whoever keeps the site's record writes it`() = runTest {
        Role.entries.forEach { assertTrue(it.name, EmergencySheetRepository.mayRead(it)) }
        val refused = repo.save(Role.FINANCE, "job.1", EmergencySheet.Sheet(), "Accounts").exceptionOrNull() as EmergencySheetRepository.Refused
        assertEquals(EmergencySheetRepository.Refusal.NOT_ALLOWED, refused.refusal)
    }
}
