package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.audit.Summaries
import il.co.tradesmanager.core.i18n.localizedTextOf
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The plus and the minus, which people press quickly.
 *
 * Both tests here are regressions for the same report: "the add and remove
 * buttons in stock are buggy". They were, in two separate ways, and neither
 * showed up as a crash or a failing check.
 */
class InventoryRepositoryTest {

    private lateinit var dao: FakeInventoryDao
    private lateinit var audit: FakeAuditDao
    private lateinit var repo: InventoryRepository

    @Before
    fun setUp() {
        dao = FakeInventoryDao()
        audit = FakeAuditDao()
        repo = InventoryRepository(dao, AuditTrail(audit))
    }

    private suspend fun anItem(quantity: Double): String {
        val id = "item.1"
        dao.upsert(
            InventoryItemEntity(
                id = id,
                catalogItemId = null,
                tradeId = null,
                kind = "MATERIAL",
                category = "cable",
                unit = "PIECE",
                names = localizedTextOf("en" to "Cable tie"),
                spec = localizedTextOf(),
                attributes = emptyMap(),
                tags = emptyList(),
                quantity = quantity,
                minStock = 0.0,
                supplierId = null,
                purchasePrice = null,
                barcode = null,
                searchIndex = "cable tie",
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
        return id
    }

    @Test
    fun `ten taps on plus put ten on the shelf, however fast they land`() = runTest {
        val id = anItem(quantity = 0.0)

        // Every tap is its own coroutine, which is exactly what the screen
        // does -- viewModelScope.launch per press. Before the adjustment was
        // serialised, these read the same count and wrote over each other, so
        // ten taps left two or three.
        (1..10).map { async { repo.adjustStock(id, 1.0, Summaries.RESTOCKED, "Foreman") } }
            .awaitAll()

        assertEquals(10.0, dao.item(id)!!.quantity, 0.0)
        assertEquals("one movement per tap", 10, dao.movements.size)
        assertEquals(10, audit.entries.size)
    }

    @Test
    fun `taps in both directions cannot lose each other`() = runTest {
        val id = anItem(quantity = 50.0)

        val work = (1..8).map { async { repo.adjustStock(id, 1.0, Summaries.RESTOCKED, "A") } } +
            (1..5).map { async { repo.adjustStock(id, -1.0, Summaries.USED_ON_SITE, "B") } }
        work.awaitAll()

        assertEquals(53.0, dao.item(id)!!.quantity, 0.0)
        assertEquals(13, dao.movements.size)
        assertEquals(
            "the last movement agrees with the shelf",
            53.0,
            dao.movements.last().resultingQuantity,
            0.0,
        )
    }

    @Test
    fun `minus on an empty shelf records nothing at all`() = runTest {
        val id = anItem(quantity = 0.0)

        val after = repo.adjustStock(id, -1.0, Summaries.USED_ON_SITE, "Foreman")

        assertEquals(0.0, after, 0.0)
        assertTrue("no movement of nothing", dao.movements.isEmpty())
        assertTrue("no audit line about nothing", audit.entries.isEmpty())
    }

    @Test
    fun `a partial take is clamped but still recorded, because stock did move`() = runTest {
        val id = anItem(quantity = 0.5)

        val after = repo.adjustStock(id, -1.0, Summaries.USED_ON_SITE, "Foreman")

        assertEquals(0.0, after, 0.0)
        assertEquals(1, dao.movements.size)
        assertEquals(-0.5, dao.movements.single().delta, 0.0)
        assertEquals(1, audit.entries.size)
    }

    @Test
    fun `an item that is not there moves nothing and says nothing`() = runTest {
        val after = repo.adjustStock("nobody", 1.0, Summaries.RESTOCKED, "Foreman")

        assertEquals(0.0, after, 0.0)
        assertTrue(dao.movements.isEmpty())
        assertTrue(audit.entries.isEmpty())
    }
}
