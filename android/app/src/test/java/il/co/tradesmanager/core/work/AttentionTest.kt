package il.co.tradesmanager.core.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttentionTest {

    @Test
    fun `zeros and absent items are left out, and the rest keep the order they matter in`() {
        val lines = Attention.lines(
            mapOf(
                Attention.Item.DELAYS_RUNNING to 2,
                Attention.Item.QUERIES_OVERDUE to 0,
                Attention.Item.RISKS_EXTREME to 1,
                Attention.Item.MATERIALS_REJECTED to 3,
            ),
        )
        assertEquals(
            listOf(Attention.Item.RISKS_EXTREME, Attention.Item.MATERIALS_REJECTED, Attention.Item.DELAYS_RUNNING),
            lines.map { it.item },
        )
        assertEquals(listOf(1, 3, 2), lines.map { it.count })
    }

    @Test
    fun `a job with nothing wrong has nothing to say`() {
        assertTrue(Attention.lines(emptyMap()).isEmpty())
        assertTrue(Attention.lines(Attention.Item.entries.associateWith { 0 }).isEmpty())
    }

    @Test
    fun `a negative count, which only a broken query could give, is not shown`() {
        assertTrue(Attention.lines(mapOf(Attention.Item.INSPECTIONS_OVERDUE to -1)).isEmpty())
    }
}
