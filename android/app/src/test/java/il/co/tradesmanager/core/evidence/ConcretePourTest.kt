package il.co.tradesmanager.core.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Concrete that went in late looks exactly like concrete that did not, for
 * years. The clock is the only thing that can tell anybody, so it is pinned.
 */
class ConcretePourTest {

    private val minute = 60L * 1000L
    private val batched = 1_760_000_000_000L

    private fun at(minutes: Long) = batched + minutes * minute

    @Test
    fun `ninety minutes is the ordinary working life`() {
        assertEquals(90L, ConcretePour.workingLifeMinutes(null))
        assertEquals(90L, ConcretePour.workingLifeMinutes(24.0))
        assertEquals(90L, ConcretePour.minutesLeft(batched, batched))
        assertEquals(30L, ConcretePour.minutesLeft(batched, at(60)))
    }

    @Test
    fun `an Israeli summer afternoon cuts it to an hour`() {
        assertEquals(60L, ConcretePour.workingLifeMinutes(30.0))
        assertEquals(60L, ConcretePour.workingLifeMinutes(38.0))
        // The same truck at the same moment: fine in winter, out of time in a
        // heatwave. This is the reason a pour gets moved to first thing.
        assertEquals(
            ConcretePour.State.ON_SITE,
            ConcretePour.state(batched, at(5), null, at(65), temperatureCelsius = 20.0),
        )
        assertEquals(
            ConcretePour.State.EXPIRED,
            ConcretePour.state(batched, at(5), null, at(65), temperatureCelsius = 34.0),
        )
    }

    @Test
    fun `the limit itself is expired, not the minute after`() {
        assertEquals(
            ConcretePour.State.EXPIRING,
            ConcretePour.state(batched, at(5), null, at(89)),
        )
        assertEquals(
            ConcretePour.State.EXPIRED,
            ConcretePour.state(batched, at(5), null, at(90)),
        )
    }

    @Test
    fun `the last quarter of an hour warns, and warrants interrupting somebody`() {
        assertEquals(
            ConcretePour.State.ON_SITE,
            ConcretePour.state(batched, at(5), null, at(74)),
        )
        val warning = ConcretePour.state(batched, at(5), null, at(75))
        assertEquals(ConcretePour.State.EXPIRING, warning)
        assertTrue(warning.needsAttention)
        assertTrue(ConcretePour.State.EXPIRED.needsAttention)
        assertFalse(ConcretePour.State.ON_SITE.needsAttention)
    }

    @Test
    fun `a truck that has not arrived is en route, not on site`() {
        assertEquals(
            ConcretePour.State.EN_ROUTE,
            ConcretePour.state(batched, arrivedAt = null, dischargedAt = null, now = at(20)),
        )
    }

    @Test
    fun `once it is in, the clock stops`() {
        // Discharged is discharged. Whether it went in late is a separate
        // question, asked afterwards with an engineer in the room.
        assertEquals(
            ConcretePour.State.PLACED,
            ConcretePour.state(batched, at(5), at(40), now = at(500)),
        )
    }

    @Test
    fun `whether it went in late is answered from the discharge time`() {
        assertFalse(ConcretePour.wasPlacedLate(batched, at(89)))
        assertFalse(ConcretePour.wasPlacedLate(batched, at(90)))
        assertTrue(ConcretePour.wasPlacedLate(batched, at(91)))
        // Same load, hot day: in time by the ordinary figure, late by the one
        // that applied.
        assertFalse(ConcretePour.wasPlacedLate(batched, at(70)))
        assertTrue(ConcretePour.wasPlacedLate(batched, at(70), temperatureCelsius = 33.0))
    }

    @Test
    fun `a load still in the drum has not been placed late yet`() {
        assertFalse(ConcretePour.wasPlacedLate(batched, dischargedAt = null))
    }

    @Test
    fun `minutes left floors and never goes negative`() {
        assertEquals(1L, ConcretePour.minutesLeft(batched, at(89)))
        assertEquals(0L, ConcretePour.minutesLeft(batched, at(90)))
        assertEquals(0L, ConcretePour.minutesLeft(batched, at(900)))
    }

    @Test
    fun `urgency puts the truck about to run out first and placed loads last`() {
        val soon = ConcretePour.urgency(batched, null, at(80), null)
        val later = ConcretePour.urgency(batched, null, at(20), null)
        val done = ConcretePour.urgency(batched, at(40), at(80), null)
        assertTrue(soon < later)
        assertTrue(later < done)
    }

    @Test
    fun `a rejected truck stops counting down instead of pinning itself to the top`() {
        // Sent away an hour ago, so it is well past ninety minutes and would
        // otherwise read as the most urgent thing on the screen for the rest
        // of the day, above the load somebody could still act on.
        val rejected = ConcretePour.urgency(batched, null, at(200), null, rejected = true)
        val live = ConcretePour.urgency(at(150), null, at(200), null)
        assertTrue(live < rejected)
        assertEquals(Long.MAX_VALUE, rejected)
    }
}
