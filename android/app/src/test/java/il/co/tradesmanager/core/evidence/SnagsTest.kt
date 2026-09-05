package il.co.tradesmanager.core.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule these exist for: a snag somebody says they fixed is not a snag
 * anybody has checked, and it stays on the count until they have.
 */
class SnagsTest {

    private val day = 24L * 60L * 60L * 1000L
    private val now = 1_760_000_000_000L
    private val yesterday = now - day
    private val nextWeek = now + 7 * day

    private fun snag(status: String, dueOn: Long? = null, blocks: Boolean = true) =
        Triple(status, dueOn, blocks)

    @Test
    fun `fixed is a claim, not a completion`() {
        val state = Snags.state(Snags.Status.FIXED)
        assertEquals(Snags.State.AWAITING_CHECK, state)
        assertTrue(state.isOutstanding)
    }

    @Test
    fun `only closed is finished`() {
        assertFalse(Snags.state(Snags.Status.CLOSED).isOutstanding)
        assertTrue(Snags.state(Snags.Status.OPEN).isOutstanding)
        assertTrue(Snags.state(Snags.Status.REJECTED).isOutstanding)
    }

    @Test
    fun `an unknown status is open, never closed`() {
        // A status a later version added must not make a defect vanish off the
        // list on a phone running this build.
        val state = Snags.state("SOMETHING_ELSE")
        assertEquals(Snags.State.OPEN, state)
        assertTrue(state.isOutstanding)
    }

    @Test
    fun `a snag claimed fixed yesterday and never checked is still overdue`() {
        // The date is a date by which it has to be done. Nobody has
        // established that it is.
        assertTrue(Snags.isOverdue(Snags.Status.FIXED, yesterday, now))
        assertTrue(Snags.isOverdue(Snags.Status.OPEN, yesterday, now))
        assertTrue(Snags.isOverdue(Snags.Status.REJECTED, yesterday, now))
        assertFalse(Snags.isOverdue(Snags.Status.CLOSED, yesterday, now))
    }

    @Test
    fun `overdue starts at the date, not the day after`() {
        assertTrue(Snags.isOverdue(Snags.Status.OPEN, now, now))
        assertFalse(Snags.isOverdue(Snags.Status.OPEN, now + 1, now))
    }

    @Test
    fun `a snag with no date is never overdue`() {
        assertFalse(Snags.isOverdue(Snags.Status.OPEN, null, now))
        assertEquals(0L, Snags.daysOverdue(null, now))
    }

    @Test
    fun `days overdue floors and never goes negative`() {
        assertEquals(0L, Snags.daysOverdue(nextWeek, now))
        assertEquals(0L, Snags.daysOverdue(now - day + 1, now))
        assertEquals(1L, Snags.daysOverdue(yesterday, now))
        assertEquals(9L, Snags.daysOverdue(now - 9 * day, now))
    }

    @Test
    fun `a snag cannot be closed without somebody claiming it first`() {
        // Collapsing the two steps into one takes the point of the list with it.
        assertFalse(Snags.canVerify(Snags.Status.OPEN))
        assertTrue(Snags.canVerify(Snags.Status.FIXED))
        assertFalse(Snags.canVerify(Snags.Status.CLOSED))
        assertFalse(Snags.canVerify(Snags.Status.REJECTED))
    }

    @Test
    fun `a rejected snag can be claimed again`() {
        assertTrue(Snags.canMarkFixed(Snags.Status.OPEN))
        assertTrue(Snags.canMarkFixed(Snags.Status.REJECTED))
        assertFalse(Snags.canMarkFixed(Snags.Status.FIXED))
        assertFalse(Snags.canMarkFixed(Snags.Status.CLOSED))
    }

    @Test
    fun `handover counts a claim as outstanding`() {
        val result = Snags.handover(
            listOf(
                snag(Snags.Status.CLOSED),
                snag(Snags.Status.FIXED),
                snag(Snags.Status.OPEN),
            ),
            now,
        )
        assertEquals(3, result.total)
        assertEquals(1, result.closed)
        assertEquals(2, result.outstanding)
        assertEquals(1, result.awaitingCheck)
        assertFalse(result.isReady)
    }

    @Test
    fun `a job is ready when nothing outstanding blocks it`() {
        // A scuff to touch up next week is a real snag that stays on the list
        // and does not hold up a building.
        val result = Snags.handover(
            listOf(
                snag(Snags.Status.CLOSED),
                snag(Snags.Status.OPEN, blocks = false),
            ),
            now,
        )
        assertEquals(1, result.outstanding)
        assertEquals(0, result.blocking)
        assertTrue(result.isReady)
    }

    @Test
    fun `one blocking snag holds the job, however many are closed`() {
        val result = Snags.handover(
            List(20) { snag(Snags.Status.CLOSED) } + snag(Snags.Status.FIXED, blocks = true),
            now,
        )
        assertEquals(1, result.blocking)
        assertFalse(result.isReady)
    }

    @Test
    fun `a job with no snags is not counted as finished work`() {
        val result = Snags.handover(emptyList(), now)
        assertEquals(0, result.total)
        assertEquals(0.0, result.fraction, 0.0001)
        // Nothing blocks it, because nothing has been found — which is a
        // different statement from "it has been inspected and it is clean".
        assertTrue(result.isReady)
    }

    @Test
    fun `overdue is counted only on snags that are not done`() {
        val result = Snags.handover(
            listOf(
                snag(Snags.Status.CLOSED, dueOn = yesterday),
                snag(Snags.Status.OPEN, dueOn = yesterday),
                snag(Snags.Status.FIXED, dueOn = yesterday),
                snag(Snags.Status.OPEN, dueOn = nextWeek),
            ),
            now,
        )
        assertEquals(2, result.overdue)
    }

    @Test
    fun `the fraction is closed over total, not claimed over total`() {
        val result = Snags.handover(
            listOf(
                snag(Snags.Status.CLOSED),
                snag(Snags.Status.FIXED),
                snag(Snags.Status.FIXED),
                snag(Snags.Status.OPEN),
            ),
            now,
        )
        assertEquals(0.25, result.fraction, 0.0001)
    }
}
