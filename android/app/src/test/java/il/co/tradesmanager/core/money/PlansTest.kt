package il.co.tradesmanager.core.money

import il.co.tradesmanager.core.money.Plans.Capability
import il.co.tradesmanager.core.money.Plans.Entitlement
import il.co.tradesmanager.core.money.Plans.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the money may and may not buy.
 *
 * The first test here is the one that matters, and it is exhaustive on
 * purpose: every capability in [Plans.NEVER_CHARGED], against every plan, in
 * both the paid and the lapsed state. The rule it defends — nothing that keeps
 * somebody safe is ever behind a payment — is the sort of thing that survives
 * in a document right up until the quarter somebody is asked to find growth in
 * it. Written this way, putting a permit behind a paywall means deleting a
 * passing test with "safety" in its name, which is a thing a reviewer sees.
 *
 * The rest are the ordinary promises: what a plan buys, what a lapse takes
 * back, and how many people fit on the books.
 */
class PlansTest {

    private val everyPlan = Plan.entries
    private val everyState = everyPlan.flatMap { plan ->
        listOf(Entitlement(plan, lapsed = false), Entitlement(plan, lapsed = true))
    }

    @Test
    fun `safety is never behind a payment, on any plan, lapsed or not`() {
        for (capability in Plans.NEVER_CHARGED) {
            for (entitlement in everyState) {
                assertTrue(
                    "$capability must be allowed on $entitlement",
                    Plans.allows(entitlement, capability),
                )
            }
        }
    }

    @Test
    fun `the free plan is a real plan and not a locked door`() {
        val free = Entitlement()
        assertEquals(Plan.FREE, free.plan)
        assertFalse(free.lapsed)

        // The whole of the never-charged set, on the plan nobody pays for.
        assertTrue(Plans.allows(free, Capability.ADMISSION_AT_THE_GATE))
        assertTrue(Plans.allows(free, Capability.SAFETY_INDUCTION))
        assertTrue(Plans.allows(free, Capability.READ_PERMITS))
        assertTrue(Plans.allows(free, Capability.REPORT_INCIDENT))
        assertTrue(Plans.allows(free, Capability.SEE_OWN_VIOLATION))
        assertTrue(Plans.allows(free, Capability.CHECK_IN_AND_OUT))
        assertTrue(Plans.allows(free, Capability.READ_EVERYTHING))
        assertTrue(Plans.allows(free, Capability.EXPORT_EVERYTHING))
    }

    @Test
    fun `every capability is classified`() {
        // A capability nobody put in a set is silently unavailable on every
        // plan, including the one that paid for it. This is the test that
        // makes adding the thirteenth capability a two-line change rather
        // than a bug report six weeks later.
        for (capability in Capability.entries) {
            val free = Plans.allows(Entitlement(Plan.FREE), capability)
            val site = Plans.allows(Entitlement(Plan.SITE), capability)
            assertTrue(
                "$capability is available on no plan at all — it was never classified",
                free || site,
            )
        }
    }

    @Test
    fun `the plans nest, and each one buys something`() {
        for (capability in Capability.entries) {
            val free = Plans.allows(Entitlement(Plan.FREE), capability)
            val pro = Plans.allows(Entitlement(Plan.PRO), capability)
            val site = Plans.allows(Entitlement(Plan.SITE), capability)

            assertTrue("$capability is on free but not pro", !free || pro)
            assertTrue("$capability is on pro but not site", !pro || site)
        }

        assertTrue(
            "pro buys nothing free does not already have",
            Capability.entries.any {
                Plans.allows(Entitlement(Plan.PRO), it) && !Plans.allows(Entitlement(Plan.FREE), it)
            },
        )
        assertTrue(
            "site buys nothing pro does not already have",
            Capability.entries.any {
                Plans.allows(Entitlement(Plan.SITE), it) && !Plans.allows(Entitlement(Plan.PRO), it)
            },
        )
    }

    @Test
    fun `what pro buys`() {
        val pro = Entitlement(Plan.PRO)
        assertTrue(Plans.allows(pro, Capability.SECOND_COMPANY))
        assertTrue(Plans.allows(pro, Capability.SIGNED_AUDIT_EXPORT))

        val free = Entitlement(Plan.FREE)
        assertFalse(Plans.allows(free, Capability.SECOND_COMPANY))
        assertFalse(Plans.allows(free, Capability.SIGNED_AUDIT_EXPORT))
    }

    @Test
    fun `what site buys on top`() {
        val site = Entitlement(Plan.SITE)
        assertTrue(Plans.allows(site, Capability.SUBCONTRACT_CHAIN))
        assertTrue(Plans.allows(site, Capability.CONTRACTS))
        assertTrue(Plans.allows(site, Capability.PAYMENT_APPLICATIONS))

        val pro = Entitlement(Plan.PRO)
        assertFalse(Plans.allows(pro, Capability.SUBCONTRACT_CHAIN))
        assertFalse(Plans.allows(pro, Capability.CONTRACTS))
        assertFalse(Plans.allows(pro, Capability.PAYMENT_APPLICATIONS))
    }

    @Test
    fun `a lapse takes back the writing and nothing else`() {
        val lapsed = Entitlement(Plan.SITE, lapsed = true)

        // Everything bought is gone, whichever plan was bought.
        for (capability in Capability.entries - Plans.NEVER_CHARGED) {
            for (plan in everyPlan) {
                assertFalse(
                    "$capability survived a lapse on $plan",
                    Plans.allows(Entitlement(plan, lapsed = true), capability),
                )
            }
        }

        // The records do not. This is the promise in docs/PRICING.md: a firm
        // that cannot get its own site diary out of an app is a firm that
        // will never put one in, and the day they most need the export is
        // the day they have stopped paying.
        assertTrue(Plans.allows(lapsed, Capability.READ_EVERYTHING))
        assertTrue(Plans.allows(lapsed, Capability.EXPORT_EVERYTHING))
    }

    @Test
    fun `a lapse is not a downgrade`() {
        // What they bought is remembered, so paying again restores it rather
        // than making them choose a plan a second time.
        val lapsed = Entitlement(Plan.SITE, lapsed = true)
        assertEquals(Plan.SITE, lapsed.plan)
        assertEquals(Plan.FREE, lapsed.effective)
        assertEquals(Plan.SITE, lapsed.copy(lapsed = false).effective)
    }

    @Test
    fun `how many fit on the books`() {
        assertEquals(3, Plans.seats(Plan.FREE))
        assertEquals(15, Plans.seats(Plan.PRO))
        assertNull(Plans.seats(Plan.SITE))
    }

    @Test
    fun `the office may add up to the limit and not past it`() {
        val free = Entitlement(Plan.FREE)
        assertTrue(Plans.mayAddPerson(free, onTheBooks = 2))
        assertFalse(Plans.mayAddPerson(free, onTheBooks = 3))

        val pro = Entitlement(Plan.PRO)
        assertTrue(Plans.mayAddPerson(pro, onTheBooks = 14))
        assertFalse(Plans.mayAddPerson(pro, onTheBooks = 15))

        assertTrue(Plans.mayAddPerson(Entitlement(Plan.SITE), onTheBooks = 10_000))
    }

    @Test
    fun `a firm with nobody on the books can always add the first person`() {
        // Including a lapsed one. Somebody reinstalling on a new phone after
        // their subscription ran out must be able to put themselves back.
        for (entitlement in everyState) {
            assertTrue("$entitlement cannot add its first person", Plans.mayAddPerson(entitlement, 0))
        }
    }

    @Test
    fun `a lapsed firm falls back to the free limit without losing anyone`() {
        val lapsed = Entitlement(Plan.PRO, lapsed = true)

        // Eleven people are already on the books. Nobody is removed — the
        // records are all still there and readable — but the office cannot
        // add a twelfth until they pay again.
        assertFalse(Plans.mayAddPerson(lapsed, onTheBooks = 11))
        assertEquals(0, Plans.seatsLeft(lapsed, onTheBooks = 11))
        assertTrue(Plans.mayAddPerson(lapsed.copy(lapsed = false), onTheBooks = 11))
    }

    @Test
    fun `seats left never goes negative and is unknown when unlimited`() {
        val free = Entitlement(Plan.FREE)
        assertEquals(3, Plans.seatsLeft(free, onTheBooks = 0))
        assertEquals(1, Plans.seatsLeft(free, onTheBooks = 2))
        assertEquals(0, Plans.seatsLeft(free, onTheBooks = 3))
        // Over the limit, because a firm can drop from pro to free with
        // twelve people already on the books.
        assertEquals(0, Plans.seatsLeft(free, onTheBooks = 12))

        assertNull(Plans.seatsLeft(Entitlement(Plan.SITE), onTheBooks = 99))
    }
}
