package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.audit.Summary
import il.co.tradesmanager.core.i18n.localizedTextOf
import il.co.tradesmanager.data.local.dao.PpeDao
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import il.co.tradesmanager.data.local.entity.PpeIssueEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The equipment table, in memory. */
class FakePpeDao : PpeDao {
    val rows = MutableStateFlow<List<PpeIssueEntity>>(emptyList())

    override suspend fun upsert(issue: PpeIssueEntity) {
        rows.value = rows.value.filterNot { it.id == issue.id } + issue
    }

    override suspend fun issue(id: String): PpeIssueEntity? = rows.value.firstOrNull { it.id == id }

    override fun observeForCompany(companyId: String?): Flow<List<PpeIssueEntity>> =
        rows.map { all -> all.filter { it.companyId == companyId }.sortedByDescending { it.issuedAt } }
}

class PpeRepositoryTest {

    private lateinit var dao: FakePpeDao
    private lateinit var stock: FakeInventoryDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: PpeRepository

    private val signed = "0.1,0.1 0.6,0.4"

    @Before
    fun setUp() {
        dao = FakePpeDao()
        stock = FakeInventoryDao()
        audit = FakeAuditDao()
        val trail = AuditTrail(audit)
        repo = PpeRepository(dao, InventoryRepository(stock, trail), trail)
    }

    private suspend fun helmets(quantity: Double): String {
        stock.upsert(
            InventoryItemEntity(
                id = "ge.safety.helmet",
                catalogItemId = null,
                tradeId = null,
                kind = "SAFETY",
                category = "ppe",
                unit = "PIECE",
                names = localizedTextOf("en" to "Safety helmet"),
                spec = localizedTextOf(),
                attributes = emptyMap(),
                tags = emptyList(),
                quantity = quantity,
                minStock = 0.0,
                supplierId = null,
                purchasePrice = null,
                barcode = null,
                searchIndex = "safety helmet",
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
        return "ge.safety.helmet"
    }

    private suspend fun issue(
        role: Role = Role.SAFETY_OFFICER,
        itemId: String? = null,
        quantity: Int = 1,
        signature: String = signed,
        replaceBy: Long? = null,
        now: Long = 1_000_000L,
    ) = repo.issue(
        role = role,
        companyId = "co.1",
        accountId = "acc.worker",
        holderName = "  Yossi  ",
        inventoryItemId = itemId,
        itemName = " Safety helmet ",
        quantity = quantity,
        size = " ",
        replaceBy = replaceBy,
        signature = signature,
        byAccountId = "acc.officer",
        byName = "Officer",
        now = now,
    )

    @Test
    fun `an issue is recorded, tidied, taken off the shelf and audited`() = runTest {
        val item = helmets(10.0)
        val done = issue(itemId = item, quantity = 2).getOrThrow()

        assertEquals("Yossi", done.issue.holderName)
        assertEquals("Safety helmet", done.issue.itemName)
        assertNull("a blank size is no size", done.issue.size)
        assertNull("ten on the shelf covers two", done.stockShortBy)
        assertEquals(8.0, stock.item(item)!!.quantity, 0.0)

        val movement = stock.movements.single()
        assertEquals(-2.0, movement.delta, 0.0)
        assertEquals(Summaries.PPE_ISSUED_TO, Summary.parse(movement.reason)!!.key)

        val entry = audit.entries.single { it.entityType == "ppe_issue" }
        val parsed = Summary.parse(entry.summary)!!
        assertEquals(Summaries.PPE_ISSUED, parsed.key)
        assertEquals(listOf("2", "Safety helmet", "Yossi"), parsed.arguments)
    }

    @Test
    fun `a short shelf does not stop the issue and is reported`() = runTest {
        val item = helmets(1.0)
        val done = issue(itemId = item, quantity = 3).getOrThrow()

        assertEquals(2.0, done.stockShortBy!!, 0.0)
        assertEquals(0.0, stock.item(item)!!.quantity, 0.0)
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun `something that never came off the stock list touches no stock`() = runTest {
        val done = issue(itemId = null).getOrThrow()

        assertNull(done.stockShortBy)
        assertTrue(stock.movements.isEmpty())
    }

    @Test
    fun `nothing is written without the holder's signature`() = runTest {
        val item = helmets(5.0)
        val refused = issue(itemId = item, signature = "").exceptionOrNull() as PpeRepository.Refused

        assertEquals(PpeRepository.Refusal.NOT_SIGNED, refused.refusal)
        assertTrue(dao.rows.value.isEmpty())
        assertEquals("the shelf is untouched", 5.0, stock.item(item)!!.quantity, 0.0)
        assertTrue(audit.entries.isEmpty())
    }

    @Test
    fun `a worker does not hand equipment out, and HR only reads the register`() = runTest {
        for (role in listOf(Role.WORKER, Role.HR, Role.FINANCE)) {
            val refused = issue(role = role).exceptionOrNull() as PpeRepository.Refused
            assertEquals(role.name, PpeRepository.Refusal.NOT_ALLOWED, refused.refusal)
        }
        assertTrue(PpeRepository.mayRead(Role.HR))
        assertTrue(!PpeRepository.mayRead(Role.WORKER))
        assertTrue(PpeRepository.mayIssue(Role.MANAGER))
        assertTrue(PpeRepository.mayIssue(Role.OWNER))
    }

    @Test
    fun `a replace-by date before the issue is refused`() = runTest {
        val refused = issue(replaceBy = 999_999L, now = 1_000_000L).exceptionOrNull() as PpeRepository.Refused
        assertEquals(PpeRepository.Refusal.REPLACE_BY_TOO_EARLY, refused.refusal)
    }

    @Test
    fun `handing back dates the row once, and puts nothing back on the shelf`() = runTest {
        val item = helmets(3.0)
        val id = issue(itemId = item).getOrThrow().issue.id

        val back = repo.handBack(Role.SAFETY_OFFICER, id, "Officer", now = 2_000_000L).getOrThrow()
        assertEquals(2_000_000L, back.handedBackAt)
        assertEquals(2.0, stock.item(item)!!.quantity, 0.0)

        val again = repo.handBack(Role.SAFETY_OFFICER, id, "Officer").exceptionOrNull() as PpeRepository.Refused
        assertEquals(PpeRepository.Refusal.NOT_HELD, again.refusal)
        assertEquals(2_000_000L, dao.issue(id)!!.handedBackAt)

        val summary = Summary.parse(audit.entries.last { it.entityType == "ppe_issue" }.summary)!!
        assertEquals(Summaries.PPE_HANDED_BACK, summary.key)
        assertEquals(listOf("Safety helmet", "Yossi"), summary.arguments)
    }

    @Test
    fun `a stored row reads back as the rules see it`() = runTest {
        issue().getOrThrow()
        val row = repo.observeForCompany("co.1").first().single()
        val rules = PpeRepository.asIssue(row)
        assertEquals("acc.worker", rules.holderKey)
        assertNotNull(rules.itemName)
    }
}
