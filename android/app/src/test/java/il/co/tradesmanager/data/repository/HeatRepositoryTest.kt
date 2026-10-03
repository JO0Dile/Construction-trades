package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.safety.Heat
import il.co.tradesmanager.data.local.dao.HeatDao
import il.co.tradesmanager.data.local.entity.HeatCheckEntity
import il.co.tradesmanager.data.local.entity.TimeEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The heat table, in memory, with the firm scoping of the real query. */
class FakeHeatDao : HeatDao {
    val rows = MutableStateFlow<List<HeatCheckEntity>>(emptyList())

    override suspend fun upsert(check: HeatCheckEntity) {
        rows.value = rows.value.filterNot { it.id == check.id } + check
    }

    override fun observeForCompany(companyId: String?, limit: Int): Flow<List<HeatCheckEntity>> =
        rows.map { all ->
            all.filter { it.companyId == companyId }.sortedByDescending { it.checkedAt }.take(limit)
        }
}

class HeatRepositoryTest {

    private lateinit var dao: FakeHeatDao
    private lateinit var schedule: FakeScheduleDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: HeatRepository

    @Before
    fun setUp() {
        dao = FakeHeatDao()
        schedule = FakeScheduleDao()
        audit = FakeAuditDao()
        repo = HeatRepository(dao, schedule, AuditTrail(audit))
    }

    private suspend fun record(
        role: Role = Role.SAFETY_OFFICER,
        temperatureC: Double = 32.0,
        humidityPercent: Double = 70.0,
        inSun: Boolean = false,
        measures: Set<Heat.Measure> = setOf(Heat.Measure.WATER),
    ) = repo.record(
        role = role,
        companyId = "co.1",
        temperatureC = temperatureC,
        humidityPercent = humidityPercent,
        inSun = inSun,
        measures = measures,
        note = "  tar laying, roof  ",
        byAccountId = "acc.1",
        byName = "Foreman",
    )

    @Test
    fun `the band on the record is the one the rules give, not one the screen sent`() = runTest {
        val check = record().getOrThrow()

        assertEquals(Heat.Band.DANGER.name, check.band)
        assertEquals(Heat.heatIndexCelsius(32.0, 70.0), check.heatIndexC, 1e-9)
        assertEquals("tar laying, roof", check.note)
    }

    @Test
    fun `sun is judged on the worst case and the shade index is what is stored`() = runTest {
        val check = record(temperatureC = 33.0, humidityPercent = 40.0, inSun = true).getOrThrow()

        assertEquals(Heat.Band.DANGER.name, check.band)
        assertEquals(Heat.heatIndexCelsius(33.0, 40.0), check.heatIndexC, 1e-9)
        assertTrue(check.inSun)
    }

    @Test
    fun `a check with nothing done is recorded, not refused`() = runTest {
        val check = record(measures = emptySet()).getOrThrow()

        assertTrue(check.measures.isEmpty())
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun `measures are stored in the rules' order, however they were ticked`() = runTest {
        val check = record(
            measures = setOf(Heat.Measure.HEAVY_WORK_MOVED, Heat.Measure.WATER, Heat.Measure.SHADE),
        ).getOrThrow()

        assertEquals(listOf("WATER", "SHADE", "HEAVY_WORK_MOVED"), check.measures)
    }

    @Test
    fun `an office role cannot record one, and nothing is written`() = runTest {
        val refused = record(role = Role.FINANCE).exceptionOrNull() as HeatRepository.Refused

        assertEquals(HeatRepository.Refusal.NOT_ALLOWED, refused.refusal)
        assertTrue(dao.rows.value.isEmpty())
        assertTrue(audit.entries.isEmpty())
    }

    @Test
    fun `numbers that cannot be weather write nothing`() = runTest {
        val hot = record(temperatureC = 75.0).exceptionOrNull() as HeatRepository.Refused
        val wet = record(humidityPercent = 140.0).exceptionOrNull() as HeatRepository.Refused

        assertEquals(HeatRepository.Refusal.IMPLAUSIBLE_TEMPERATURE, hot.refusal)
        assertEquals(HeatRepository.Refusal.IMPLAUSIBLE_HUMIDITY, wet.refusal)
        assertTrue(dao.rows.value.isEmpty())
        assertTrue(audit.entries.isEmpty())
    }

    @Test
    fun `the audit line carries the level as a key, translated when it is read`() = runTest {
        record().getOrThrow()

        val parsed = Summary.parse(audit.entries.single().summary)!!
        assertEquals(Summaries.HEAT_CHECKED, parsed.key)
        assertEquals("40", parsed.arguments[0])
        assertEquals("heat_band_danger", Summary.nested(parsed.arguments[1]))
    }

    @Test
    fun `the site is taken from who is checked in, when they agree`() = runTest {
        schedule.timeEntries += TimeEntryEntity(
            id = "t1", projectId = "site.a", workerId = "w1", workerName = "Ahmad",
            checkInAt = System.currentTimeMillis(),
        )
        assertEquals("site.a", record().getOrThrow().projectId)

        schedule.timeEntries += TimeEntryEntity(
            id = "t2", projectId = "site.b", workerId = "w2", workerName = "Yossi",
            checkInAt = System.currentTimeMillis(),
        )
        assertNull(record().getOrThrow().projectId)
    }
}
