package il.co.tradesmanager.core.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule these tests exist for: a permit that has run out authorises nothing,
 * whatever its status column says. Everything else here is arithmetic around
 * that one sentence.
 */
class PermitsTest {

    private val minute = 60L * 1000L
    private val hour = 60L * minute
    private val eightAm = 1_760_000_000_000L
    private val fivePm = eightAm + 9 * hour

    private fun stateAt(now: Long, validTo: Long? = fivePm) =
        Permits.state(Permits.Status.ISSUED, eightAm, validTo, now)

    /** Work with nothing left smouldering behind it: no fire watch to keep. */
    private fun closable(status: String, now: Long = fivePm) =
        Permits.canClose(status, Permits.Type.HEIGHT, workStoppedAt = null, validTo = fivePm, now = now)

    @Test
    fun `an issued permit inside its window authorises work`() {
        assertEquals(Permits.State.ACTIVE, stateAt(eightAm))
        assertEquals(Permits.State.ACTIVE, stateAt(eightAm + 4 * hour))
        assertTrue(stateAt(eightAm + 4 * hour).authorisesWork)
    }

    @Test
    fun `ten past five on a permit that ran out at five authorises nothing`() {
        // The reason this file exists. Nobody edits the status column at
        // knocking-off time, so the clock has to be what decides.
        val state = stateAt(fivePm + 10 * minute)
        assertEquals(Permits.State.EXPIRED, state)
        assertFalse(state.authorisesWork)
    }

    @Test
    fun `expiry is the moment itself, not the minute after`() {
        assertEquals(Permits.State.EXPIRED, stateAt(fivePm))
        assertEquals(Permits.State.EXPIRING, stateAt(fivePm - 1))
    }

    @Test
    fun `the last hour warns, and still authorises work`() {
        assertEquals(Permits.State.ACTIVE, stateAt(fivePm - hour - minute))
        assertEquals(Permits.State.EXPIRING, stateAt(fivePm - hour))
        assertEquals(Permits.State.EXPIRING, stateAt(fivePm - 5 * minute))
        assertTrue(stateAt(fivePm - 5 * minute).authorisesWork)
    }

    @Test
    fun `a permit signed for tomorrow does not authorise work today`() {
        val tomorrow = eightAm + 24 * hour
        val state = Permits.state(Permits.Status.ISSUED, tomorrow, tomorrow + 9 * hour, eightAm)
        assertEquals(Permits.State.PENDING, state)
        assertFalse(state.authorisesWork)
    }

    @Test
    fun `a permit with no end never expires, but is never the accident either`() {
        // Legitimate for a long electrical isolation. canIssue refuses to
        // create one by omission, so this shape only exists deliberately.
        assertEquals(Permits.State.ACTIVE, stateAt(eightAm + 400 * hour, validTo = null))
        assertFalse(
            Permits.canIssue(Permits.Status.DRAFT, 4, 4, validFrom = eightAm, validTo = null),
        )
    }

    @Test
    fun `draft, closed and cancelled ignore the clock entirely`() {
        val longGone = fivePm + 500 * hour
        assertEquals(
            Permits.State.DRAFT,
            Permits.state(Permits.Status.DRAFT, eightAm, fivePm, longGone),
        )
        assertEquals(
            Permits.State.CLOSED,
            Permits.state(Permits.Status.CLOSED, eightAm, fivePm, eightAm),
        )
        assertEquals(
            Permits.State.CANCELLED,
            Permits.state(Permits.Status.CANCELLED, eightAm, fivePm, eightAm),
        )
    }

    @Test
    fun `an unknown status is a draft, not an authorisation`() {
        // Least privilege, same as an unrecognised role. A status this build
        // does not understand must never be the one that lets work start.
        val state = Permits.state("SOMETHING_A_LATER_VERSION_ADDED", eightAm, fivePm, eightAm)
        assertEquals(Permits.State.DRAFT, state)
        assertFalse(state.authorisesWork)
    }

    @Test
    fun `minutes left floors, so nobody gets a minute that does not exist`() {
        assertEquals(60L, Permits.minutesLeft(fivePm, fivePm - hour))
        assertEquals(1L, Permits.minutesLeft(fivePm, fivePm - minute - 30_000L))
        assertEquals(0L, Permits.minutesLeft(fivePm, fivePm))
        assertEquals(0L, Permits.minutesLeft(fivePm, fivePm + 10 * hour))
    }

    @Test
    fun `a permit cannot be issued with a precaution outstanding`() {
        assertFalse(Permits.canIssue(Permits.Status.DRAFT, 4, 3, eightAm, fivePm))
        assertTrue(Permits.canIssue(Permits.Status.DRAFT, 4, 4, eightAm, fivePm))
    }

    @Test
    fun `a permit with no precautions at all cannot be issued`() {
        // An empty permit would tick its own box. The precautions are what the
        // permit consists of; there is nothing to authorise without them.
        assertFalse(Permits.canIssue(Permits.Status.DRAFT, 0, 0, eightAm, fivePm))
    }

    @Test
    fun `a window that ends before it starts is a typo, not a permit`() {
        assertFalse(Permits.canIssue(Permits.Status.DRAFT, 4, 4, fivePm, eightAm))
        assertFalse(Permits.canIssue(Permits.Status.DRAFT, 4, 4, eightAm, eightAm))
    }

    @Test
    fun `only a draft can be issued, and only an issued permit can be closed`() {
        assertFalse(Permits.canIssue(Permits.Status.ISSUED, 4, 4, eightAm, fivePm))
        assertFalse(Permits.canIssue(Permits.Status.CLOSED, 4, 4, eightAm, fivePm))

        assertTrue(closable(Permits.Status.ISSUED))
        assertFalse(closable(Permits.Status.DRAFT))
        assertFalse(closable(Permits.Status.CLOSED))
        assertFalse(closable(Permits.Status.CANCELLED))
    }

    @Test
    fun `an expired permit can still be signed back`() {
        // Work stopping and the area being checked are two different events,
        // and the second one is the one that gets written down.
        assertTrue(closable(Permits.Status.ISSUED))
        assertEquals(Permits.State.EXPIRED, stateAt(fivePm + hour))
    }

    @Test
    fun `urgency sorts the permit about to run out to the top`() {
        val soon = Permits.urgency(Permits.Status.ISSUED, fivePm, fivePm - 10 * minute)
        val later = Permits.urgency(Permits.Status.ISSUED, fivePm + 5 * hour, fivePm - 10 * minute)
        val closed = Permits.urgency(Permits.Status.CLOSED, fivePm, fivePm - 10 * minute)
        assertTrue(soon < later)
        assertTrue(later < closed)
    }

    @Test
    fun `hot work is the kind that leaves something behind`() {
        assertTrue(Permits.needsFireWatch(Permits.Type.HOT_WORK))
        assertFalse(Permits.needsFireWatch(Permits.Type.HEIGHT))
        assertFalse(Permits.needsFireWatch(Permits.Type.CONFINED_SPACE))
        assertFalse(Permits.needsFireWatch(Permits.Type.ELECTRICAL))
    }

    @Test
    fun `a hot work permit cannot be signed back the moment the torch goes out`() {
        val stopped = fivePm - hour
        assertFalse(hotWorkClosable(workStoppedAt = stopped, now = stopped))
        assertFalse(hotWorkClosable(workStoppedAt = stopped, now = stopped + 59 * minute))
        assertTrue(hotWorkClosable(workStoppedAt = stopped, now = stopped + 60 * minute))
    }

    @Test
    fun `the watch is measured from when the work stopped, not from the window`() {
        // Welding finished at two on a permit that ran until five. The hour is
        // owed from two, so the permit is closable at three — and the window
        // ending later does not extend it.
        val stopped = fivePm - 3 * hour
        assertTrue(hotWorkClosable(workStoppedAt = stopped, now = stopped + hour))
    }

    @Test
    fun `with no stop time recorded the watch runs from the end of the window`() {
        // The latest the work could have gone on for. Conservative on purpose:
        // it can only hold the permit open longer than the truth, never let it
        // close sooner.
        assertFalse(hotWorkClosable(workStoppedAt = null, now = fivePm))
        assertFalse(hotWorkClosable(workStoppedAt = null, now = fivePm + 59 * minute))
        assertTrue(hotWorkClosable(workStoppedAt = null, now = fivePm + hour))
    }

    @Test
    fun `a permit with neither a stop time nor a window still owes the full hour`() {
        // Otherwise leaving both fields empty would be a way to close it.
        assertEquals(
            Permits.FIRE_WATCH_MINUTES,
            Permits.fireWatchMinutesLeft(workStoppedAt = null, validTo = null, now = fivePm),
        )
        assertFalse(
            Permits.canClose(
                Permits.Status.ISSUED,
                Permits.Type.HOT_WORK,
                workStoppedAt = null,
                validTo = null,
                now = fivePm,
            ),
        )
    }

    @Test
    fun `the fire watch rounds up where the permit window rounds down`() {
        val stopped = fivePm
        // Thirty seconds of watch left is a minute left, not none. Flooring it
        // the way minutesLeft floors would let the permit be signed back while
        // somebody still had half a minute to stand there.
        assertEquals(
            1L,
            Permits.fireWatchMinutesLeft(stopped, fivePm, stopped + 59 * minute + 30_000L),
        )
        assertEquals(0L, Permits.fireWatchMinutesLeft(stopped, fivePm, stopped + hour))
        assertEquals(0L, Permits.fireWatchMinutesLeft(stopped, fivePm, stopped + 2 * hour))
        // The window, meanwhile, still floors: ninety seconds left reads as one.
        assertEquals(1L, Permits.minutesLeft(fivePm, fivePm - 90_000L))
    }

    @Test
    fun `work that leaves nothing smouldering closes as soon as it stops`() {
        assertTrue(
            Permits.canClose(
                Permits.Status.ISSUED,
                Permits.Type.HEIGHT,
                workStoppedAt = fivePm,
                validTo = fivePm,
                now = fivePm,
            ),
        )
    }

    private fun hotWorkClosable(workStoppedAt: Long?, now: Long) = Permits.canClose(
        Permits.Status.ISSUED,
        Permits.Type.HOT_WORK,
        workStoppedAt = workStoppedAt,
        validTo = fivePm,
        now = now,
    )
}
