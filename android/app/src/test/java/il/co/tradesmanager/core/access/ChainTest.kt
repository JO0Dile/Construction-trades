package il.co.tradesmanager.core.access

import il.co.tradesmanager.core.access.Chain.Blocker
import il.co.tradesmanager.core.access.Chain.Company
import il.co.tradesmanager.core.access.Chain.Link
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The promises this app makes about a person's wages.
 *
 * Not tests of a graph walk. Each one is a sentence somebody would say out
 * loud on a site — "my mate cannot see what I earn", "I cannot see what my
 * foreman earns" — and the only place those sentences are written down in a
 * form that fails when somebody changes them by accident.
 */
class ChainTest {

    // owner -> manager -> foreman -> {ali, dana}, and a second foreman beside.
    private val firm = Company(
        links = listOf(
            Link("owner", null),
            Link("manager", "owner"),
            Link("foreman", "manager"),
            Link("otherForeman", "manager"),
            Link("ali", "foreman"),
            Link("dana", "foreman"),
            Link("sam", "otherForeman"),
        ),
        owners = setOf("owner"),
    )

    @Test
    fun `your own pay is yours`() {
        // A labourer has no Money lens at all and must still be able to see
        // what they earned. Anything else is an app that will not tell
        // somebody what they were paid.
        assertTrue(Chain.maySeePay(firm, "ali", Role.WORKER, "ali"))
    }

    @Test
    fun `your boss sees your pay`() {
        assertTrue(Chain.maySeePay(firm, "foreman", Role.MANAGER, "ali"))
    }

    @Test
    fun `so does your boss's boss, all the way to the owner`() {
        assertTrue(Chain.maySeePay(firm, "manager", Role.MANAGER, "ali"))
        assertTrue(Chain.maySeePay(firm, "owner", Role.OWNER, "ali"))
    }

    @Test
    fun `you do not see your own boss's pay`() {
        // The complaint this came from, in one line.
        assertFalse(Chain.maySeePay(firm, "ali", Role.MANAGER, "foreman"))
        assertFalse(Chain.maySeePay(firm, "foreman", Role.MANAGER, "manager"))
        assertFalse(Chain.maySeePay(firm, "manager", Role.MANAGER, "owner"))
    }

    @Test
    fun `you do not see the pay of somebody beside you`() {
        assertFalse(Chain.maySeePay(firm, "ali", Role.MANAGER, "dana"))
        assertFalse(Chain.maySeePay(firm, "foreman", Role.MANAGER, "otherForeman"))
    }

    @Test
    fun `a foreman on another crew sees nothing of this one`() {
        assertFalse(Chain.maySeePay(firm, "otherForeman", Role.MANAGER, "ali"))
        assertFalse(Chain.maySeePay(firm, "foreman", Role.MANAGER, "sam"))
    }

    @Test
    fun `the money lens is needed for anybody else's record`() {
        // Standing above somebody is not on its own an entitlement to their
        // wages: the lens says whether this role touches money at all, and the
        // chain says whose. Both, or neither.
        assertFalse(Chain.maySeePay(firm, "foreman", Role.SAFETY_OFFICER, "ali"))
        assertFalse(Chain.maySeePay(firm, "foreman", Role.WORKER, "ali"))
        assertTrue(Chain.maySeePay(firm, "foreman", Role.MANAGER, "ali"))
    }

    @Test
    fun `nobody with no membership sees anything`() {
        // An unauthenticated request arrives with no membership, and a rule
        // that treated blank as a wildcard would answer it in full.
        assertFalse(Chain.maySeePay(firm, "", Role.OWNER, "ali"))
        assertFalse(Chain.maySeePay(firm, "owner", Role.OWNER, ""))
        assertFalse(Chain.maySeePay(firm, "", Role.OWNER, ""))
    }

    @Test
    fun `a firm that has drawn no chart still lets its owner see the payroll`() {
        // Everybody starts with no manager: the gate admits people and does
        // not rank them. Without the owner being the top of every branch, a
        // company on its first morning would hide its own wages from the
        // person who pays them.
        val fresh = Company(
            links = listOf(Link("owner", null), Link("ali", null), Link("dana", null)),
            owners = setOf("owner"),
        )
        assertTrue(Chain.maySeePay(fresh, "owner", Role.OWNER, "ali"))
        assertFalse(Chain.maySeePay(fresh, "ali", Role.MANAGER, "dana"))
        assertFalse(Chain.maySeePay(fresh, "ali", Role.MANAGER, "owner"))
    }

    @Test
    fun `an owner is not above themselves`() {
        assertEquals(emptyList<String>(), Chain.ancestors(firm, "owner"))
        assertFalse(Chain.isAbove(firm, "owner", "owner"))
    }

    @Test
    fun `the chain reads nearest first`() {
        assertEquals(listOf("foreman", "manager", "owner"), Chain.ancestors(firm, "ali"))
    }

    @Test
    fun `what a foreman may be shown is themselves and their crew`() {
        assertEquals(
            setOf("foreman", "ali", "dana"),
            Chain.payVisibleTo(firm, "foreman", Role.MANAGER),
        )
    }

    @Test
    fun `a labourer may be shown only their own`() {
        assertEquals(setOf("ali"), Chain.payVisibleTo(firm, "ali", Role.WORKER))
    }

    /* ------------------------------------------------------- broken data */

    @Test
    fun `a circle in the chain does not hang the screen that reads wages`() {
        // Two devices restored from the same backup, a reorganisation applied
        // twice. A walk that trusted the data would spin on the one screen
        // that decides whether to show somebody's wages.
        val looped = Company(links = listOf(Link("a", "b"), Link("b", "a")))
        assertEquals(listOf("b"), Chain.ancestors(looped, "a"))
        assertFalse(Chain.isAbove(looped, "a", "a"))
    }

    @Test
    fun `a very deep chain terminates`() {
        val deep = Company(
            links = (0..200).map { Link("m$it", if (it == 0) null else "m${it - 1}") },
        )
        assertTrue(Chain.ancestors(deep, "m200").size <= Chain.MAX_DEPTH)
    }

    /* ---------------------------------------------------- moving the chain */

    @Test
    fun `a foreman may move somebody already on their crew`() {
        // The ordinary act this exists for. An earlier version of the rule
        // also asked Role.canManageMembers, which is the owner and HR only,
        // and so refused exactly this.
        assertNull(Chain.blocksReporting(firm, "foreman", "ali", "dana"))
    }

    @Test
    fun `a foreman may not rearrange another crew`() {
        assertEquals(Blocker.NOT_YOURS, Chain.blocksReporting(firm, "foreman", "sam", "ali"))
    }

    @Test
    fun `nobody moves themselves`() {
        // Otherwise the first thing anybody does is report to the labourer
        // they hired last week and read their manager's wages in the morning.
        assertEquals(Blocker.YOURSELF, Chain.blocksReporting(firm, "foreman", "foreman", "ali"))
    }

    @Test
    fun `arranging the chain is a matter of position, not of rank`() {
        // A crew leader stored as a worker still arranges their own crew, and
        // an office role with no branch under it arranges nobody. The rule
        // takes no role at all, and these are the two halves of why.
        assertTrue(Chain.mayArrange(firm, "foreman", "ali"))
        assertFalse(Chain.mayArrange(firm, "otherForeman", "ali"))
        assertFalse(Chain.mayArrange(firm, "ali", "dana"))
    }

    @Test
    fun `an owner may arrange anybody, because somebody has to draw the first line`() {
        assertTrue(Chain.mayArrange(firm, "owner", "ali"))
        assertFalse(Chain.mayArrange(firm, "owner", "owner"))
    }

    @Test
    fun `you cannot rearrange the branch you answer to`() {
        // This is the whole point of the direction: otherwise a labourer
        // makes their own manager report to them and reads their pay in the
        // morning.
        assertEquals(Blocker.NOT_YOURS, Chain.blocksReporting(firm, "ali", "foreman", "dana"))
    }

    @Test
    fun `a move that would make a circle is refused`() {
        assertEquals(Blocker.A_LOOP, Chain.blocksReporting(firm, "owner", "manager", "ali"))
        assertTrue(Chain.wouldLoop(firm, "manager", "ali"))
        assertFalse(Chain.wouldLoop(firm, "ali", "otherForeman"))
    }

    @Test
    fun `nobody reports to themselves`() {
        assertEquals(Blocker.THEMSELVES, Chain.blocksReporting(firm, "owner", "ali", "ali"))
    }

    @Test
    fun `a membership this company does not have is refused, not guessed at`() {
        assertEquals(Blocker.UNKNOWN, Chain.blocksReporting(firm, "owner", "stranger", "ali"))
        assertEquals(Blocker.UNKNOWN, Chain.blocksReporting(firm, "owner", "ali", "stranger"))
    }

    @Test
    fun `the owner may lift somebody back to the top`() {
        assertNull(Chain.blocksReporting(firm, "owner", "ali", null))
    }
}
