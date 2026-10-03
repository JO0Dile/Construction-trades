package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.safety.PreUse
import il.co.tradesmanager.data.local.dao.EquipmentDao
import il.co.tradesmanager.data.local.entity.EquipmentEntity
import il.co.tradesmanager.data.local.entity.PlantCheckEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The plant tables, in memory. */
class FakeEquipmentDao : EquipmentDao {
    val machines = MutableStateFlow<List<EquipmentEntity>>(emptyList())
    val checks = mutableListOf<PlantCheckEntity>()

    override suspend fun upsert(equipment: EquipmentEntity) {
        machines.value = machines.value.filterNot { it.id == equipment.id } + equipment
    }

    override suspend fun delete(equipment: EquipmentEntity) {
        machines.value = machines.value.filterNot { it.id == equipment.id }
    }

    override fun observeAll(): Flow<List<EquipmentEntity>> = machines

    override fun observeForProject(projectId: String): Flow<List<EquipmentEntity>> =
        machines.map { all -> all.filter { it.assignedProjectId == projectId } }

    override suspend fun equipment(id: String): EquipmentEntity? =
        machines.value.firstOrNull { it.id == id }

    override fun observeServiceDueBefore(before: Long): Flow<List<EquipmentEntity>> =
        machines.map { all -> all.filter { (it.serviceDueOn ?: Long.MAX_VALUE) < before } }

    override suspend fun upsertCheck(check: PlantCheckEntity) {
        checks.removeAll { it.id == check.id }
        checks += check
    }

    override fun observeLatestChecks(): Flow<List<PlantCheckEntity>> =
        MutableStateFlow(
            checks.groupBy { it.equipmentId }.values.map { mine ->
                mine.sortedWith(compareByDescending<PlantCheckEntity> { it.checkedAt }.thenByDescending { it.id })
                    .first()
            },
        )

    override fun observeChecks(equipmentId: String, limit: Int): Flow<List<PlantCheckEntity>> =
        MutableStateFlow(checks.filter { it.equipmentId == equipmentId }.sortedByDescending { it.checkedAt }.take(limit))
}

/**
 * The half of the pre-use check the rules cannot enforce on their own: that a
 * defect actually stops the machine, everywhere the machine is shown.
 */
class EquipmentPreUseTest {

    private lateinit var dao: FakeEquipmentDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: EquipmentRepository

    private val allOk = PreUse.Item.entries.associateWith { PreUse.Answer.OK }
    private val leaking = allOk + (PreUse.Item.LEAKS to PreUse.Answer.DEFECT)

    @Before
    fun setUp() {
        dao = FakeEquipmentDao()
        audit = FakeAuditDao()
        repo = EquipmentRepository(dao, AuditTrail(audit))
    }

    private suspend fun aMachine(status: String = EquipmentRepository.Status.ON_SITE): EquipmentEntity {
        val added = repo.add("JCB 3CX", EquipmentRepository.Ownership.OWNED, null, null, null, "Office")
        val machine = added.copy(status = status)
        dao.upsert(machine)
        audit.entries.clear()
        return machine
    }

    private suspend fun statusOf(machine: EquipmentEntity) = dao.equipment(machine.id)!!.status

    @Test
    fun `a fit machine stays in service and the check is recorded`() = runTest {
        val machine = aMachine()

        val check = repo.recordPreUse(machine, allOk, null, "acc.1", "Driver").getOrThrow()

        assertEquals(PreUse.Outcome.FIT.name, check.outcome)
        assertNull(check.defectNote)
        assertEquals(EquipmentRepository.Status.ON_SITE, statusOf(machine))
        assertEquals(Summary.of(Summaries.PLANT_CHECKED_FIT, "JCB 3CX"), audit.entries.single().summary)
        assertEquals(PreUse.Item.entries.size, check.answers.size)
    }

    @Test
    fun `a defect takes the machine out of service`() = runTest {
        val machine = aMachine()

        val check = repo.recordPreUse(machine, leaking, "  boom ram weeping  ", "acc.1", "Driver").getOrThrow()

        assertEquals(PreUse.Outcome.UNFIT.name, check.outcome)
        assertEquals("boom ram weeping", check.defectNote)
        assertEquals(EquipmentRepository.Status.MAINTENANCE, statusOf(machine))
        assertEquals(
            Summary.of(Summaries.PLANT_CHECKED_UNFIT, "JCB 3CX", "boom ram weeping"),
            audit.entries.first().summary,
        )
        assertEquals("the status change is audited too", 2, audit.entries.size)
    }

    @Test
    fun `a clean check later does not put a stopped machine back`() = runTest {
        val machine = aMachine()
        repo.recordPreUse(machine, leaking, "boom ram weeping", null, "Driver").getOrThrow()

        repo.recordPreUse(dao.equipment(machine.id)!!, allOk, null, null, "Driver").getOrThrow()

        assertEquals(EquipmentRepository.Status.MAINTENANCE, statusOf(machine))
    }

    @Test
    fun `a machine off hire stays off hire`() = runTest {
        val machine = aMachine(EquipmentRepository.Status.OFF_HIRE)

        repo.recordPreUse(machine, leaking, "flat tyre", null, "Driver").getOrThrow()

        assertEquals(EquipmentRepository.Status.OFF_HIRE, statusOf(machine))
    }

    @Test
    fun `a refused check writes nothing and changes nothing`() = runTest {
        val machine = aMachine()

        val refused = repo.recordPreUse(machine, leaking, "   ", null, "Driver").exceptionOrNull()
            as EquipmentRepository.PreUseRefused

        assertEquals(PreUse.Refusal.DEFECT_NOT_DESCRIBED, refused.refusal)
        assertTrue(dao.checks.isEmpty())
        assertTrue(audit.entries.isEmpty())
        assertEquals(EquipmentRepository.Status.ON_SITE, statusOf(machine))
    }
}
