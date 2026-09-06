package il.co.tradesmanager.core.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignmentTest {

    private val S = Assignment.Status

    @Test
    fun `the ordinary run of a package`() {
        val path = listOf(S.DRAFT, S.OFFERED, S.ACCEPTED, S.IN_PROGRESS, S.SUBMITTED, S.APPROVED)
        path.zipWithNext().forEach { (from, to) ->
            assertTrue("$from -> $to should be allowed", Assignment.canMove(from, to))
        }
    }

    @Test
    fun `a rejection goes back to work, not back to the offer`() {
        assertTrue(Assignment.canMove(S.SUBMITTED, S.REJECTED))
        assertTrue(Assignment.canMove(S.REJECTED, S.IN_PROGRESS))
        // The price was agreed once. A rejection is not a chance to reprice.
        assertFalse(Assignment.canMove(S.REJECTED, S.OFFERED))
        assertFalse(Assignment.canMove(S.REJECTED, S.ACCEPTED))
    }

    @Test
    fun `approved work cannot be walked back`() {
        Assignment.terminal.forEach { state ->
            assertTrue("$state should be terminal", Assignment.next(state).isEmpty())
        }
    }

    @Test
    fun `only the crew accepts and submits`() {
        assertEquals(Assignment.Side.PAYEE, Assignment.actor(S.ACCEPTED))
        assertEquals(Assignment.Side.PAYEE, Assignment.actor(S.SUBMITTED))
        // A contractor that could submit on the crew's behalf could
        // manufacture a rejection against it.
        assertFalse(Assignment.mayMove(S.IN_PROGRESS, S.SUBMITTED, Assignment.Side.PAYER))
        assertTrue(Assignment.mayMove(S.IN_PROGRESS, S.SUBMITTED, Assignment.Side.PAYEE))
    }

    @Test
    fun `only the payer approves and rejects`() {
        assertEquals(Assignment.Side.PAYER, Assignment.actor(S.APPROVED))
        assertEquals(Assignment.Side.PAYER, Assignment.actor(S.REJECTED))
        // A crew that could approve its own work needs no inspection at all.
        assertFalse(Assignment.mayMove(S.SUBMITTED, S.APPROVED, Assignment.Side.PAYEE))
        assertTrue(Assignment.mayMove(S.SUBMITTED, S.APPROVED, Assignment.Side.PAYER))
    }

    @Test
    fun `a rejection without a reason is not a rejection`() {
        assertFalse(Assignment.canReject(null))
        assertFalse(Assignment.canReject(""))
        assertFalse(Assignment.canReject("   "))
        assertTrue(Assignment.canReject("Pipe alignment in room 102 needs correcting."))
    }

    @Test
    fun `work is not submitted without a photograph`() {
        assertFalse(Assignment.canSubmit(S.IN_PROGRESS, photoCount = 0))
        assertTrue(Assignment.canSubmit(S.IN_PROGRESS, photoCount = 1))
        // And not from a state where there is nothing to submit.
        assertFalse(Assignment.canSubmit(S.ACCEPTED, photoCount = 5))
        assertFalse(Assignment.canSubmit(S.APPROVED, photoCount = 5))
    }

    @Test
    fun `approval is not payment`() {
        // The one confusion this file exists to prevent. Approving work
        // permits an application; it does not create one, and it certainly
        // does not send money.
        assertTrue(Assignment.canInvoice(S.APPROVED, alreadyInvoiced = false))
        assertFalse(Assignment.canInvoice(S.APPROVED, alreadyInvoiced = true))
        assertFalse(Assignment.canInvoice(S.SUBMITTED, alreadyInvoiced = false))
    }

    @Test
    fun `every state is reachable from draft`() {
        val seen = mutableSetOf(S.DRAFT)
        val queue = ArrayDeque(listOf(S.DRAFT))
        while (queue.isNotEmpty()) {
            Assignment.next(queue.removeFirst()).forEach { if (seen.add(it)) queue += it }
        }
        val declared = setOf(
            S.DRAFT, S.OFFERED, S.ACCEPTED, S.DECLINED, S.IN_PROGRESS,
            S.SUBMITTED, S.REJECTED, S.APPROVED, S.CANCELLED,
        )
        assertEquals("an unreachable state is a state nothing can ever be in", declared, seen)
    }
}
