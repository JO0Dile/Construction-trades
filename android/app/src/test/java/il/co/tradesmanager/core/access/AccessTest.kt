package il.co.tradesmanager.core.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The permission grid, asserted rather than described.
 *
 * These are not tests of an enum. They are the promises the product makes to
 * whoever hands out the roles — "the finance clerk cannot see the programme",
 * "the labourer cannot delete the job" — and the only place those promises are
 * written down in a form that fails when someone changes them by accident.
 */
class AccessTest {

    @Test
    fun `finance sees money everywhere and plan nowhere`() {
        assertTrue(Role.FINANCE.canWrite(Lens.MONEY))
        assertFalse(Role.FINANCE.canRead(Lens.PLAN))
    }

    @Test
    fun `money has exactly one writer and two readers`() {
        val writers = Role.entries.filter { it.canWrite(Lens.MONEY) } - Role.OWNER
        val readers = Role.entries.filter { it.canRead(Lens.MONEY) } - Role.OWNER

        assertEquals(listOf(Role.FINANCE), writers)
        assertEquals(listOf(Role.MANAGER, Role.FINANCE), readers)
    }

    @Test
    fun `an owner is not restricted anywhere`() {
        Lens.entries.forEach { lens ->
            assertTrue("owner should write $lens", Role.OWNER.canWrite(lens))
        }
        assertTrue(Role.OWNER.canManageMembers)
        assertTrue(Role.OWNER.canManageJobs)
    }

    @Test
    fun `nobody on the tools can delete the job they are standing on`() {
        assertFalse(Role.WORKER.canManageJobs)
        // They still record what they did — that is the whole point of them.
        assertTrue(Role.WORKER.canWrite(Lens.EVIDENCE))
        assertTrue(Role.WORKER.canWrite(Lens.STUFF))
    }

    @Test
    fun `only owners and HR can change who is here`() {
        val managers = Role.entries.filter { it.canManageMembers }
        assertEquals(listOf(Role.OWNER, Role.HR), managers)
    }

    @Test
    fun `HR cannot read the money or the programme`() {
        assertFalse(Role.HR.canRead(Lens.MONEY))
        assertFalse(Role.HR.canRead(Lens.PLAN))
        assertTrue(Role.HR.canWrite(Lens.PEOPLE))
    }

    @Test
    fun `readable lenses is what a navigation bar is built from`() {
        assertEquals(
            setOf(Lens.STUFF, Lens.EVIDENCE, Lens.MONEY),
            Role.FINANCE.readableLenses(),
        )
        assertEquals(Lens.entries.toSet(), Role.OWNER.readableLenses())
    }

    @Test
    fun `an unknown role falls back to the least privilege there is`() {
        // A corrupted or newer row must never come back as an administrator.
        assertEquals(Role.WORKER, Role.parse("chief-of-everything"))
        assertEquals(Role.WORKER, Role.parse(null))
        assertEquals(Role.WORKER, Role.parse(""))
        assertFalse(Role.parse("nonsense").canManageMembers)
    }

    @Test
    fun `role names round-trip through storage`() {
        Role.entries.forEach { role ->
            assertEquals(role, Role.parse(role.name))
            assertEquals(role, Role.parse(role.name.lowercase()))
        }
    }

    @Test
    fun `owner is never offered as a role to hand out`() {
        // Two owners by accident is how a company ends up with an
        // administrator nobody remembers appointing.
        assertFalse(Role.OWNER in Role.assignable)
        assertEquals(4, Role.assignable.size)
    }

    @Test
    fun `access is ordered so a comparison is the whole check`() {
        assertTrue(Access.WRITE > Access.READ)
        assertTrue(Access.READ > Access.NONE)
        assertTrue(Access.WRITE.canRead)
        assertFalse(Access.READ.canWrite)
        assertFalse(Access.NONE.canRead)
    }
}
