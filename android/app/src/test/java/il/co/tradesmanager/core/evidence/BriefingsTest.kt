package il.co.tradesmanager.core.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BriefingsTest {

    private val day = 24L * 60L * 60L * 1000L
    private val now = 1_760_000_000_000L

    @Test
    fun `somebody who has never been briefed is the row that matters`() {
        assertEquals(Briefings.State.NEVER, Briefings.state(null, now))
    }

    @Test
    fun `a quarter is the line`() {
        assertEquals(Briefings.State.RECENT, Briefings.state(now - 89 * day, now))
        assertEquals(Briefings.State.DUE, Briefings.state(now - 90 * day, now))
        assertEquals(Briefings.State.DUE, Briefings.state(now - 200 * day, now))
    }

    @Test
    fun `a talk dated tomorrow is a typo, not time travel`() {
        // Without the floor this reads as -1 days ago and sorts to the top of
        // the overdue list, above people who genuinely have never attended.
        assertEquals(0L, Briefings.daysSince(now + 5 * day, now))
        assertEquals(Briefings.State.RECENT, Briefings.state(now + 5 * day, now))
    }

    @Test
    fun `never sorts above the longest overdue`() {
        val never = Briefings.urgency(null, now)
        val ancient = Briefings.urgency(now - 900 * day, now)
        val yesterday = Briefings.urgency(now - day, now)
        assertTrue(never < ancient)
        assertTrue(ancient < yesterday)
    }
}
