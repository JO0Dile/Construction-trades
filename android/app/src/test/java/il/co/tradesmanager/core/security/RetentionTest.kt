package il.co.tradesmanager.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    @Test
    fun `the default keeps everything`() {
        assertEquals(0, Retention.CHOICES.first())
        assertNull(Retention.cutoff(now, 0))
    }

    @Test
    fun `a period counts back from now`() {
        assertEquals(now - day, Retention.cutoff(now, 1))
        assertEquals(now - 365 * day, Retention.cutoff(now, 365))
    }

    @Test
    fun `a negative period keeps everything rather than deleting everything`() {
        // The dangerous reading: -5 days is a cutoff five days in the future,
        // which would take the whole trail. Storing it is refused; this is the
        // second line of defence if that is ever bypassed.
        assertNull(Retention.cutoff(now, -5))
        assertNull(Retention.cutoff(now, Int.MIN_VALUE))
    }

    @Test
    fun `a very long period does not overflow into deleting everything`() {
        // Int days times a Long millisecond count stays in range; if it wrapped
        // it could land after now and take the lot.
        val far = Retention.cutoff(now, Int.MAX_VALUE)!!
        assertTrue("a longer period must never delete more", far < now - 7 * 365 * day)
    }

    @Test
    fun `every offered choice is a whole number of years or keep everything`() {
        assertEquals(listOf(0, 365, 1095, 2555), Retention.CHOICES)
        Retention.CHOICES.forEach { assertTrue(it >= 0) }
    }
}
