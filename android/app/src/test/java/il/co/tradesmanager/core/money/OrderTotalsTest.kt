package il.co.tradesmanager.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

class OrderTotalsTest {

    private val cents = 0.005

    @Test
    fun `a line is worth what was ordered at the agreed price`() {
        assertEquals(1_250.0, OrderTotals.lineValue(50.0, 25.0), cents)
    }

    @Test
    fun `a part delivery leaves the rest outstanding`() {
        assertEquals(10.0, OrderTotals.outstandingQuantity(50.0, 40.0), cents)
        assertEquals(250.0, OrderTotals.outstandingValue(50.0, 40.0, 25.0), cents)
    }

    @Test
    fun `a generous supplier does not credit the job`() {
        // Fifty-two turn up when fifty were ordered. Without the floor this
        // goes negative and the job quietly looks cheaper than it is.
        assertEquals(0.0, OrderTotals.outstandingQuantity(50.0, 52.0), cents)
        assertEquals(0.0, OrderTotals.outstandingValue(50.0, 52.0, 25.0), cents)
    }

    @Test
    fun `a fully delivered line commits nothing further`() {
        assertEquals(0.0, OrderTotals.outstandingValue(50.0, 50.0, 25.0), cents)
    }

    @Test
    fun `nothing delivered yet means the whole line is still to come`() {
        assertEquals(1_250.0, OrderTotals.outstandingValue(50.0, 0.0, 25.0), cents)
    }
}
