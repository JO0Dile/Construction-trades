package il.co.tradesmanager.core.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The worked example everybody uses to describe this problem:
 *
 *     general contractor pays the first tier   10,000
 *     the first tier pays the crew              7,000
 *     the first tier keeps                      3,000
 *
 * The crew may know 7,000 and must never learn the other two. Every test here
 * is that sentence from one seat or another.
 */
class CommercialTest {

    private val gc = "org.gc"
    private val first = "org.first"
    private val crew = "org.crew"
    private val stranger = "org.other"

    private val main = Commercial.Agreement("a.main", payerOrgId = gc, payeeOrgId = first)
    private val sub = Commercial.Agreement("a.sub", payerOrgId = first, payeeOrgId = crew)

    private val mainMoney = Commercial.Money(10_000.0, 10_000.0, 0.0)
    private val subMoney = Commercial.Money(7_000.0, 7_000.0, 0.0)

    @Test
    fun `the crew sees its own contract`() {
        assertTrue(Commercial.maySeeMoney(sub, crew))
        assertEquals(7_000.0, Commercial.disclose(sub, subMoney, crew).money!!.contractSum, 0.005)
    }

    @Test
    fun `the crew is not sent the main contract at all`() {
        assertFalse(Commercial.maySeeMoney(main, crew))
        // Absent, not blanked. A field that arrives as null was still sent.
        assertNull(Commercial.disclose(main, mainMoney, crew).money)
    }

    @Test
    fun `the general contractor does not learn what the crew was paid`() {
        // The direction people forget. Confidentiality is not only downward:
        // the first tier's buying price is its own business too.
        assertFalse(Commercial.maySeeMoney(sub, gc))
        assertNull(Commercial.disclose(sub, subMoney, gc).money)
    }

    @Test
    fun `the first tier sees both, because it signed both`() {
        assertTrue(Commercial.maySeeMoney(main, first))
        assertTrue(Commercial.maySeeMoney(sub, first))
    }

    @Test
    fun `a firm with no part in the job sees nothing`() {
        assertFalse(Commercial.maySeeMoney(main, stranger))
        assertFalse(Commercial.maySeeMoney(sub, stranger))
    }

    @Test
    fun `nobody is not a wildcard`() {
        // An unauthenticated request arrives with no organisation. A rule that
        // treated blank as "match anything" would answer it in full.
        assertFalse(Commercial.maySeeMoney(main, ""))
        assertFalse(Commercial.maySeeMoney(main, "   "))
        assertNull(Commercial.disclose(main, mainMoney, "").money)
    }

    @Test
    fun `open-book disclosure is to somebody specific`() {
        val open = sub.copy(disclosedToOrgIds = setOf(gc))
        assertTrue(Commercial.maySeeMoney(open, gc))
        // and still not to everybody else
        assertFalse(Commercial.maySeeMoney(open, stranger))
    }

    @Test
    fun `the crew can be told the package exists without being told its value`() {
        assertTrue(Commercial.maySeeExistence(main, crew, engagedOnJob = true))
        assertFalse(Commercial.maySeeMoney(main, crew))
        // Being on the job is what grants it; a stranger is still nobody.
        assertFalse(Commercial.maySeeExistence(main, stranger, engagedOnJob = false))
    }

    @Test
    fun `margin is answered for the firm in the middle and nobody else`() {
        assertEquals(
            3_000.0,
            Commercial.margin(listOf(main to mainMoney), listOf(sub to subMoney), first)!!,
            0.005,
        )
        // Null rather than zero. Zero is an answer, and this must not be
        // answerable from either side.
        assertNull(Commercial.margin(listOf(main to mainMoney), listOf(sub to subMoney), gc))
        assertNull(Commercial.margin(listOf(main to mainMoney), listOf(sub to subMoney), crew))
        assertNull(Commercial.margin(listOf(main to mainMoney), listOf(sub to subMoney), ""))
    }

    @Test
    fun `margin refuses to mix in a payable that is not the viewer's`() {
        // Somebody else's subcontract in the list would compute a number that
        // looks like a margin and is not one.
        val other = Commercial.Agreement("a.other", payerOrgId = gc, payeeOrgId = "org.x")
        assertNull(
            Commercial.margin(
                listOf(main to mainMoney),
                listOf(sub to subMoney, other to subMoney),
                first,
            ),
        )
    }

    @Test
    fun `margin refuses a receivable the viewer is not paid on`() {
        assertNull(Commercial.margin(listOf(sub to subMoney), emptyList(), first))
    }

    @Test
    fun `a firm with several crews nets them all off`() {
        val second = Commercial.Agreement("a.sub2", payerOrgId = first, payeeOrgId = "org.crew2")
        val secondMoney = Commercial.Money(2_000.0, 1_500.0, 0.0)
        assertEquals(
            10_000.0 - 7_000.0 - 1_500.0,
            Commercial.margin(
                listOf(main to mainMoney),
                listOf(sub to subMoney, second to secondMoney),
                first,
            )!!,
            0.005,
        )
    }

    @Test
    fun `a firm with two packages from the same client counts both`() {
        // The frame and the fit-out, let separately by the same general
        // contractor. Netting off against only the first understates the
        // margin by the whole of the second — a wrong number that looks
        // entirely plausible.
        val extra = Commercial.Agreement("a.main2", payerOrgId = gc, payeeOrgId = first)
        val extraMoney = Commercial.Money(4_000.0, 4_000.0, 0.0)
        assertEquals(
            10_000.0 + 4_000.0 - 7_000.0,
            Commercial.margin(
                listOf(main to mainMoney, extra to extraMoney),
                listOf(sub to subMoney),
                first,
            )!!,
            0.005,
        )
    }

    @Test
    fun `a firm that is paid on nothing has no margin`() {
        // Not zero. A crew with only outgoings on this job has not made a
        // margin of nothing, it has no margin to speak of.
        assertNull(Commercial.margin(emptyList(), listOf(sub to subMoney), first))
    }
}
