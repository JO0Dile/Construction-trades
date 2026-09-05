package il.co.tradesmanager.core.money

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A hire bill and a job sheet that disagree is an argument with a hire company
 * nobody wins, so the day count is pinned here rather than assumed.
 */
class HireCostTest {

    private val day = 24L * 60L * 60L * 1000L
    private val start = 1_760_000_000_000L
    private val cents = 0.005

    @Test
    fun `a machine still out charges to today`() {
        assertEquals(5L, HireCost.daysOnHire(start, null, start + 5 * day))
        assertEquals(2_500.0, HireCost.accrued(500.0, start, null, start + 5 * day), cents)
    }

    @Test
    fun `a machine returned last Tuesday stops charging on Tuesday`() {
        // The whole point. Without this a returned machine keeps billing for
        // the rest of the job and nobody notices until the invoice arrives.
        val returned = start + 3 * day
        assertEquals(3L, HireCost.daysOnHire(start, returned, start + 30 * day))
        assertEquals(1_500.0, HireCost.accrued(500.0, start, returned, start + 30 * day), cents)
    }

    @Test
    fun `part days count as whole days, because hire companies charge that way`() {
        assertEquals(1L, HireCost.daysOnHire(start, null, start + day / 2))
        assertEquals(4L, HireCost.daysOnHire(start, null, start + 3 * day + 1))
    }

    @Test
    fun `a machine that has not gone out yet costs nothing`() {
        assertEquals(0L, HireCost.daysOnHire(start, null, start))
        assertEquals(0L, HireCost.daysOnHire(null, null, start + 10 * day))
        assertEquals(0.0, HireCost.accrued(500.0, null, null, start + 10 * day), cents)
    }

    @Test
    fun `an off-hire date in the future does not bill the future`() {
        // Someone books the return in advance; it must not charge until then.
        assertEquals(2L, HireCost.daysOnHire(start, start + 10 * day, start + 2 * day))
    }

    @Test
    fun `owned plant has no rate and therefore no accrual`() {
        assertEquals(0.0, HireCost.accrued(null, start, null, start + 100 * day), cents)
    }
}
