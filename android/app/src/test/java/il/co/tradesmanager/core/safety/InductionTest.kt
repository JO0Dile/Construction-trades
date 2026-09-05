package il.co.tradesmanager.core.safety

import il.co.tradesmanager.core.access.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InductionTest {

    @Test
    fun `the people who run jobs get the long induction`() {
        assertEquals(Induction.Level.SUPERVISOR, Induction.levelFor(Role.OWNER))
        assertEquals(Induction.Level.SUPERVISOR, Induction.levelFor(Role.MANAGER))
    }

    @Test
    fun `everybody else gets the one-page one`() {
        // Six screens of permit procedure for a bricklayer is how inductions
        // get scrolled past instead of read.
        assertEquals(Induction.Level.WORKER, Induction.levelFor(Role.WORKER))
        assertEquals(Induction.Level.WORKER, Induction.levelFor(Role.FINANCE))
        assertEquals(Induction.Level.WORKER, Induction.levelFor(Role.HR))
    }

    @Test
    fun `the level follows the role grid, not a second list beside it`() {
        Role.entries.forEach { role ->
            val expected =
                if (role.canManageJobs) Induction.Level.SUPERVISOR else Induction.Level.WORKER
            assertEquals(role.name, expected, Induction.levelFor(role))
        }
    }

    @Test
    fun `somebody who has never signed one is stopped at the door`() {
        assertTrue(Induction.isRequired(null))
        assertFalse(Induction.isRequired(1_760_000_000_000L))
    }

    @Test
    fun `a tap is not a signature and does not induct anybody`() {
        assertFalse(Induction.isSignatureAcceptable(null))
        assertFalse(Induction.isSignatureAcceptable(""))
        assertFalse(Induction.isSignatureAcceptable("0.5,0.5"))
        assertTrue(Induction.isSignatureAcceptable("0.2,0.5 0.4,0.3 0.6,0.5"))
    }
}
