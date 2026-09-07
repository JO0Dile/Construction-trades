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

    // Raising the application. Where a discrete package meets a cumulative
    // claim, which is the join the whole payments feature exists to get right.

    private fun claimable(id: String, amount: Double, status: String, invoiced: Boolean) =
        Assignment.Claimable(id, amount, status, invoiced)

    @Test
    fun `a claim is the total of approved work, not the packages being added`() {
        val packages = listOf(
            claimable("a", 7_000.0, S.APPROVED, invoiced = true),
            claimable("b", 4_000.0, S.APPROVED, invoiced = false),
        )
        // 11,000, not 4,000. An application says what the work is worth in
        // total; claiming only the new package would ask for it and hand back
        // everything claimed last month.
        assertEquals(11_000.0, Assignment.claimToDate(packages), 0.005)
        assertEquals(listOf("b"), Assignment.readyToClaim(packages).map { it.id })
    }

    @Test
    fun `work that is not signed off is not work to date`() {
        val packages = listOf(
            claimable("a", 7_000.0, S.APPROVED, invoiced = false),
            claimable("b", 4_000.0, S.SUBMITTED, invoiced = false),
            claimable("c", 2_000.0, S.IN_PROGRESS, invoiced = false),
            claimable("d", 9_000.0, S.REJECTED, invoiced = false),
        )
        assertEquals(7_000.0, Assignment.claimToDate(packages), 0.005)
        assertEquals(listOf("a"), Assignment.readyToClaim(packages).map { it.id })
    }

    @Test
    fun `nothing new approved means no application to raise`() {
        val allClaimed = listOf(claimable("a", 7_000.0, S.APPROVED, invoiced = true))
        assertFalse(Assignment.canRaiseApplication(allClaimed))
        // But what was claimed before still counts toward the running total.
        assertEquals(7_000.0, Assignment.claimToDate(allClaimed), 0.005)

        assertTrue(
            Assignment.canRaiseApplication(
                allClaimed + claimable("b", 1.0, S.APPROVED, invoiced = false),
            ),
        )
    }

    @Test
    fun `a job with no packages claims nothing and raises nothing`() {
        assertEquals(0.0, Assignment.claimToDate(emptyList()), 0.005)
        assertFalse(Assignment.canRaiseApplication(emptyList()))
    }

    @Test
    fun `the breakdown lists exactly what the total is made of`() {
        val packages = listOf(
            claimable("a", 7_000.0, S.APPROVED, invoiced = true),
            claimable("b", 4_000.0, S.APPROVED, invoiced = false),
            claimable("c", 9_000.0, S.SUBMITTED, invoiced = false),
        )
        // Both applications from earlier and the one being added, because the
        // figure beside the breakdown is cumulative. Leaving "a" out would
        // print 11,000 above a list adding to 4,000.
        assertEquals(listOf("a", "b"), Assignment.claimedBy(packages).map { it.id })
        assertEquals(
            "the breakdown must add up to the claim",
            Assignment.claimToDate(packages),
            Assignment.claimedBy(packages).sumOf { it.amount },
            0.005,
        )
    }

    @Test
    fun `the breakdown adds up to the claim whatever the packages are`() {
        val states = listOf(
            S.DRAFT, S.OFFERED, S.ACCEPTED, S.DECLINED, S.IN_PROGRESS,
            S.SUBMITTED, S.REJECTED, S.APPROVED, S.CANCELLED,
        )
        // Every state, both invoiced flags, a spread of awkward amounts. The
        // property is the one that matters: a total and a breakdown that came
        // from two different filters would agree on the easy cases and part
        // company on exactly the job somebody disputes.
        val packages = states.flatMapIndexed { index, status ->
            listOf(true, false).map { invoiced ->
                claimable("$status-$invoiced", 1_000.0 + index * 333.33, status, invoiced)
            }
        }
        assertEquals(
            Assignment.claimToDate(packages),
            Assignment.claimedBy(packages).sumOf { it.amount },
            0.005,
        )
        assertTrue(
            "everything in the breakdown is approved",
            Assignment.claimedBy(packages).all { it.status == S.APPROVED },
        )
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
