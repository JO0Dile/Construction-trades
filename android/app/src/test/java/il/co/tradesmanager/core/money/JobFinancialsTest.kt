package il.co.tradesmanager.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numbers somebody decides on.
 *
 * A slip in here is not a display bug — it is an invoice that does not match a
 * supplier's statement, or a job that looks profitable until the last delivery
 * note arrives.
 */
class JobFinancialsTest {

    private val cents = 0.005

    @Test
    fun `an approved variation changes what the client owes and a proposed one does not`() {
        val job = JobFinancials(
            contractValue = 100_000.0,
            approvedVariations = 12_500.0,
            proposedVariations = 8_000.0,
        )

        assertEquals(112_500.0, job.revisedContract, cents)
        assertEquals(120_500.0, job.potentialContract, cents)
    }

    @Test
    fun `an omission is a variation too`() {
        // The client removed the second bathroom. That is a negative variation,
        // not a missing one.
        val job = JobFinancials(contractValue = 90_000.0, approvedVariations = -15_000.0)

        assertEquals(75_000.0, job.revisedContract, cents)
    }

    @Test
    fun `margin counts spent money, forecast counts committed money too`() {
        val job = JobFinancials(
            contractValue = 50_000.0,
            costToDate = 30_000.0,
            committedCosts = 12_000.0,
        )

        assertEquals(20_000.0, job.margin, cents)
        assertEquals(42_000.0, job.forecastCost, cents)
        assertEquals(8_000.0, job.forecastMargin, cents)
    }

    @Test
    fun `a job heading over budget is flagged before it gets there`() {
        val heading = JobFinancials(
            contractValue = 40_000.0,
            costToDate = 35_000.0,
            committedCosts = 9_000.0,
        )

        assertFalse("not over yet", heading.isOverBudget)
        assertTrue("but forecast to be", heading.isForecastOverBudget)

        val over = heading.copy(costToDate = 44_000.0)
        assertTrue(over.isOverBudget)
        // Already over is not also "forecast over" — one state, one warning.
        assertFalse(over.isForecastOverBudget)
    }

    @Test
    fun `a new job does not report an infinite margin`() {
        val fresh = JobFinancials()

        assertEquals(0.0, fresh.marginFraction, cents)
        assertEquals(0.0, fresh.revisedContract, cents)
    }

    @Test
    fun `margin fraction is of the revised contract, not the original`() {
        val job = JobFinancials(
            contractValue = 80_000.0,
            approvedVariations = 20_000.0,
            costToDate = 75_000.0,
        )

        // 25,000 of 100,000, not of 80,000.
        assertEquals(0.25, job.marginFraction, 0.0001)
    }

    @Test
    fun `uninvoiced goes negative when the client has been over-billed`() {
        val job = JobFinancials(contractValue = 60_000.0, invoiced = 65_000.0)

        assertEquals(-5_000.0, job.uninvoiced, cents)
    }

    @Test
    fun `outstanding is what a chase call is about`() {
        val job = JobFinancials(contractValue = 60_000.0, invoiced = 45_000.0, paid = 20_000.0)

        assertEquals(25_000.0, job.outstanding, cents)
        assertEquals(15_000.0, job.uninvoiced, cents)
    }

    @Test
    fun `VAT is added once, from the job's own rate`() {
        val job = JobFinancials(contractValue = 10_000.0, vatRate = JobFinancials.ISRAELI_VAT)

        assertEquals(11_800.0, job.withVat(10_000.0), cents)
        assertEquals(1_800.0, job.vatOn(10_000.0), cents)
    }

    @Test
    fun `a job priced under the old rate keeps the old rate`() {
        // VAT went 17% to 18% on 1 January 2025. A job quoted before that keeps
        // printing 17%, because its paperwork has to keep saying what it said.
        val old = JobFinancials(contractValue = 10_000.0, vatRate = 0.17)

        assertEquals(11_700.0, old.withVat(10_000.0), cents)
        assertEquals(0.18, JobFinancials.ISRAELI_VAT, 0.0001)
    }

    @Test
    fun `the worked example a foreman would recognise`() {
        // 200k flat refurbishment, one approved extra, one still being argued
        // about, most of the materials bought, half of it billed.
        val job = JobFinancials(
            contractValue = 200_000.0,
            approvedVariations = 18_000.0,
            proposedVariations = 6_500.0,
            costToDate = 120_000.0,
            committedCosts = 40_000.0,
            invoiced = 110_000.0,
            paid = 70_000.0,
        )

        assertEquals(218_000.0, job.revisedContract, cents)
        assertEquals(224_500.0, job.potentialContract, cents)
        assertEquals(98_000.0, job.margin, cents)
        assertEquals(58_000.0, job.forecastMargin, cents)
        assertEquals(108_000.0, job.uninvoiced, cents)
        assertEquals(40_000.0, job.outstanding, cents)
        assertFalse(job.isOverBudget)
        assertFalse(job.isForecastOverBudget)
        assertEquals(257_240.0, job.withVat(job.revisedContract), cents)
    }
}
