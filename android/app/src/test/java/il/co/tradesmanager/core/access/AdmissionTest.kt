package il.co.tradesmanager.core.access

import il.co.tradesmanager.core.access.Admission.AtTheGate
import il.co.tradesmanager.core.access.Admission.Blocker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdmissionTest {

    private fun gate(
        found: String = "acc-1",
        signed: Boolean = true,
        alreadyIn: Boolean = false,
    ) = AtTheGate(found, signed, alreadyIn)

    @Test
    fun `somebody found, not yet in, who has signed, gets in`() {
        assertNull(Admission.blocksAdmission(gate()))
        assertTrue(Admission.canAdmit(gate()))
    }

    @Test
    fun `an ID nobody has is refused rather than creating an account`() {
        // The alternative is a stranger's ID number bringing an account into
        // existence that the person it belongs to never agreed to.
        assertEquals(Blocker.NOT_FOUND, Admission.blocksAdmission(gate(found = "")))
    }

    @Test
    fun `admitting somebody twice is refused`() {
        assertEquals(Blocker.ALREADY_IN, Admission.blocksAdmission(gate(alreadyIn = true)))
    }

    @Test
    fun `no signature, no entry`() {
        assertEquals(Blocker.NOT_SIGNED, Admission.blocksAdmission(gate(signed = false)))
    }

    @Test
    fun `not being found is reported before the missing signature`() {
        // Asking somebody to sign before you know who they are is the wrong
        // order, and the screen shows one message.
        assertEquals(
            Blocker.NOT_FOUND,
            Admission.blocksAdmission(gate(found = "", signed = false)),
        )
    }

    // The part that matters most.

    @Test
    fun `the gate admits as a worker and nothing else`() {
        assertEquals(Role.WORKER, Admission.grantedRole())
        assertEquals(Role.onAdmission, Admission.grantedRole())
    }

    @Test
    fun `the gate may not hand out any other role`() {
        // Rank comes from whoever engaged you, separately. Whoever happens to
        // be on the gate today must not be able to make somebody a manager.
        Role.entries.forEach { role ->
            val allowed = Admission.gateMayGrant(role)
            if (role == Role.WORKER) {
                assertTrue("the gate must be able to admit a worker", allowed)
            } else {
                assertFalse("the gate must not be able to grant $role", allowed)
            }
        }
    }

    @Test
    fun `the granted role carries no authority over other people`() {
        val granted = Admission.grantedRole()
        assertFalse("admission must not confer member management", granted.canManageMembers)
        assertFalse("admission must not confer job management", granted.canManageJobs)
        assertFalse("admission must not confer money", granted.canRead(Lens.MONEY))
    }
}
