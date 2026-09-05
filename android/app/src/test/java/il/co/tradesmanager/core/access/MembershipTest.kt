package il.co.tradesmanager.core.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The same person is a site manager for one firm and on the tools for another,
 * and neither answer is wrong. These pin which one is in force.
 */
class MembershipTest {

    private val day = 24L * 60L * 60L * 1000L
    private val now = 1_760_000_000_000L

    private fun m(
        id: String,
        company: String?,
        role: Role,
        joined: Long = now,
        left: Long? = null,
    ) = Membership(id, company, role, joined, left)

    @Test
    fun `the company somebody last chose is the one in force`() {
        val list = listOf(
            m("1", "acme", Role.MANAGER, joined = now - 10 * day),
            m("2", "brick", Role.WORKER, joined = now - day),
        )
        assertEquals("acme", Memberships.active(list, "acme")?.companyId)
        assertEquals(Role.MANAGER, Memberships.roleFor(list, "acme"))
        assertEquals(Role.WORKER, Memberships.roleFor(list, "brick"))
    }

    @Test
    fun `being taken off the books stops that firm's work appearing`() {
        // A stale preference in a settings file is not a reason to keep
        // showing a company somebody no longer works for.
        val list = listOf(
            m("1", "acme", Role.MANAGER, joined = now - 10 * day, left = now - day),
            m("2", "brick", Role.WORKER, joined = now - 5 * day),
        )
        assertEquals("brick", Memberships.active(list, "acme")?.companyId)
        assertEquals(Role.WORKER, Memberships.roleFor(list, "acme"))
    }

    @Test
    fun `with no preference, the most recently joined wins`() {
        val list = listOf(
            m("1", "acme", Role.WORKER, joined = now - 100 * day),
            m("2", "brick", Role.MANAGER, joined = now - day),
        )
        assertEquals("brick", Memberships.active(list, null)?.companyId)
    }

    @Test
    fun `somebody off every firm's books keeps their account and sees nothing`() {
        val list = listOf(m("1", "acme", Role.OWNER, left = now))
        assertNull(Memberships.active(list, "acme"))
        // Least privilege, same as an unrecognised role string.
        assertEquals(Role.WORKER, Memberships.roleFor(list, "acme"))
        assertTrue(Memberships.switchable(list).isEmpty())
    }

    @Test
    fun `no memberships at all is not an owner`() {
        assertNull(Memberships.active(emptyList(), null))
        assertEquals(Role.WORKER, Memberships.roleFor(emptyList(), null))
    }

    @Test
    fun `a sole trader has one membership and no company`() {
        val list = listOf(m("1", null, Role.OWNER))
        assertEquals(Role.OWNER, Memberships.roleFor(list, null))
        assertNull(Memberships.active(list, null)?.companyId)
    }

    @Test
    fun `switchable lists only current memberships, newest first`() {
        val list = listOf(
            m("1", "acme", Role.WORKER, joined = now - 10 * day),
            m("2", "brick", Role.MANAGER, joined = now - day),
            m("3", "gone", Role.WORKER, joined = now - 2 * day, left = now),
        )
        assertEquals(listOf("brick", "acme"), Memberships.switchable(list).map { it.companyId })
    }

    @Test
    fun `the last owner of a company cannot be demoted or removed`() {
        val list = listOf(
            m("1", "acme", Role.OWNER),
            m("2", "acme", Role.WORKER),
        )
        assertTrue(Memberships.wouldStrandCompany(list, list[0], Role.MANAGER))
        assertTrue(Memberships.wouldStrandCompany(list, list[0], null))
        assertFalse(Memberships.wouldStrandCompany(list, list[0], Role.OWNER))
        assertFalse(Memberships.wouldStrandCompany(list, list[1], null))
    }

    @Test
    fun `being the last owner of one firm says nothing about another`() {
        // The old account-wide check could not tell these apart, and would
        // have refused a change that strands nobody.
        val list = listOf(
            m("1", "acme", Role.OWNER),
            m("2", "acme", Role.OWNER),
            m("3", "brick", Role.OWNER),
        )
        assertFalse(Memberships.wouldStrandCompany(list, list[0], Role.WORKER))
        assertTrue(Memberships.wouldStrandCompany(list, list[2], Role.WORKER))
    }

    @Test
    fun `an owner who already left is not holding the company up`() {
        val list = listOf(
            m("1", "acme", Role.OWNER, left = now),
            m("2", "acme", Role.OWNER),
        )
        assertFalse(Memberships.wouldStrandCompany(list, list[0], Role.WORKER))
        assertTrue(Memberships.wouldStrandCompany(list, list[1], Role.WORKER))
    }

    @Test
    fun `a personal membership can never strand anybody`() {
        val list = listOf(m("1", null, Role.OWNER))
        assertFalse(Memberships.wouldStrandCompany(list, list[0], Role.WORKER))
    }
}
