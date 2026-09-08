package il.co.tradesmanager.core.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmendmentTest {

    private val payer = "org.first"
    private val payee = "org.crew"

    private fun version(n: Int, from: Double, to: Double, by: String = payer) = Amendment.Version(
        version = n, previousAmount = from, newAmount = to,
        reason = "Extra floor added to the scope",
        proposedByOrgId = by, acceptedByOrgId = if (by == payer) payee else payer,
        acceptedAt = 1_770_000_000_000L + n,
    )

    @Test
    fun `an unsigned contract is edited, not amended`() {
        assertFalse(Amendment.canPropose(contractSigned = false, openAmendments = 0))
        assertTrue(Amendment.canPropose(contractSigned = true, openAmendments = 0))
    }

    @Test
    fun `one open proposal at a time`() {
        // Two open proposals means two people believe two different numbers
        // are about to be true, and the second acceptance silently wins.
        assertFalse(Amendment.canPropose(contractSigned = true, openAmendments = 1))
    }

    @Test
    fun `a proposal without a reason is not complete`() {
        assertFalse(Amendment.isComplete(7_500.0, null))
        assertFalse(Amendment.isComplete(7_500.0, "  "))
        assertFalse(Amendment.isComplete(null, "because"))
        assertFalse(Amendment.isComplete(-1.0, "because"))
        assertTrue(Amendment.isComplete(7_500.0, "Extra floor"))
    }

    @Test
    fun `nobody accepts their own amendment`() {
        assertFalse(Amendment.canAccept(payer, payer, payee, payer))
        assertTrue(Amendment.canAccept(payer, payer, payee, payee))
        assertTrue(Amendment.canAccept(payee, payer, payee, payer))
    }

    @Test
    fun `an outsider cannot accept an amendment between two other firms`() {
        assertFalse(Amendment.canAccept(payer, payer, payee, "org.gc"))
        assertFalse(Amendment.canAccept(payer, payer, payee, ""))
    }

    @Test
    fun `the figure in force is the latest accepted version`() {
        val versions = listOf(version(1, 7_000.0, 7_500.0), version(2, 7_500.0, 8_100.0))
        assertEquals(8_100.0, Amendment.currentAmount(7_000.0, versions), 0.005)
        assertEquals(7_000.0, Amendment.currentAmount(7_000.0, emptyList()), 0.005)
    }

    @Test
    fun `version number decides, not the clock`() {
        // Two phones offline can accept in the same millisecond.
        val same = 1_770_000_000_000L
        val a = version(1, 7_000.0, 7_500.0).copy(acceptedAt = same)
        val b = version(2, 7_500.0, 8_100.0).copy(acceptedAt = same)
        assertEquals(8_100.0, Amendment.currentAmount(7_000.0, listOf(b, a)), 0.005)
    }

    @Test
    fun `every figure the contract has ever been is still readable`() {
        val versions = listOf(version(2, 7_500.0, 8_100.0), version(1, 7_000.0, 7_500.0))
        assertEquals(
            listOf(7_000.0, 7_500.0, 8_100.0),
            Amendment.history(7_000.0, versions),
        )
    }

    @Test
    fun `a reduction is an amendment like any other`() {
        val cut = version(1, 7_000.0, 6_500.0)
        assertEquals(-500.0, Amendment.delta(cut), 0.005)
        assertEquals(6_500.0, Amendment.currentAmount(7_000.0, listOf(cut)), 0.005)
    }
}
