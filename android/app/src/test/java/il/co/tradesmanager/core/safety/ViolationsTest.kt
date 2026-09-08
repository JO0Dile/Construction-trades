package il.co.tradesmanager.core.safety

import il.co.tradesmanager.core.safety.Violations.Blocker
import il.co.tradesmanager.core.safety.Violations.Draft
import il.co.tradesmanager.core.safety.Violations.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViolationsTest {

    private fun draft(
        status: Status = Status.DRAFT,
        against: String = "acc-1",
        evidence: Int = 1,
        description: String = "No harness on the third floor edge",
    ) = Draft(status, against, evidence, description)

    @Test
    fun `a complete draft can be confirmed`() {
        assertNull(Violations.blocksConfirming(draft()))
        assertTrue(Violations.canConfirm(draft()))
    }

    @Test
    fun `nobody named is refused`() {
        // An unattributed violation accuses the whole site.
        assertEquals(Blocker.NOBODY_NAMED, Violations.blocksConfirming(draft(against = "")))
        assertEquals(Blocker.NOBODY_NAMED, Violations.blocksConfirming(draft(against = "   ")))
    }

    @Test
    fun `no photograph or video is refused`() {
        // One person's word, with a cost on it, and nothing the accused can
        // argue with.
        assertEquals(Blocker.NO_EVIDENCE, Violations.blocksConfirming(draft(evidence = 0)))
    }

    @Test
    fun `nothing written down is refused`() {
        assertEquals(Blocker.NOT_DESCRIBED, Violations.blocksConfirming(draft(description = " ")))
    }

    @Test
    fun `an already confirmed violation cannot be confirmed again`() {
        assertEquals(
            Blocker.NOT_A_DRAFT,
            Violations.blocksConfirming(draft(status = Status.CONFIRMED)),
        )
    }

    @Test
    fun `the missing person is reported before the missing photograph`() {
        // Both are wrong here. The screen shows one message, and "choose who
        // this is against" is the one that has to come first: attaching a
        // photograph to nobody is work thrown away.
        val empty = draft(against = "", evidence = 0, description = "")
        assertEquals(Blocker.NOBODY_NAMED, Violations.blocksConfirming(empty))
    }

    // Cancelling.

    @Test
    fun `a draft can be cancelled and a confirmed one cannot`() {
        assertTrue(Violations.canCancel(Status.DRAFT))
        assertFalse(Violations.canCancel(Status.CONFIRMED))
        assertFalse(Violations.canCancel(Status.CANCELLED))
    }

    @Test
    fun `every status is either cancellable or final, never both`() {
        Status.entries.forEach { status ->
            val cancellable = Violations.canCancel(status)
            val confirmable = Violations.canConfirm(draft(status = status))
            assertFalse(
                "$status cannot be both final and open",
                cancellable && status != Status.DRAFT,
            )
            if (status != Status.DRAFT) {
                assertFalse("$status must not be confirmable", confirmable)
            }
        }
    }

    // The charge.

    @Test
    fun `no charge and a charge of zero are different things`() {
        // Zero says this cost nothing. Null says nobody put a charge on it.
        // Collapsing them lets somebody argue either way afterwards.
        assertFalse(Violations.isChargeable(null))
        assertFalse(Violations.isChargeable(0.0))
        assertTrue(Violations.isChargeable(250.0))
    }

    @Test
    fun `a negative charge is refused`() {
        // That is a credit, and this is not a credit note.
        assertFalse(Violations.acceptableCost(-1.0))
        assertTrue(Violations.acceptableCost(null))
        assertTrue(Violations.acceptableCost(0.0))
        assertTrue(Violations.acceptableCost(250.0))
    }
}
