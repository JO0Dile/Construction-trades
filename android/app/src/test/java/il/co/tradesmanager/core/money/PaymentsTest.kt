package il.co.tradesmanager.core.money

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two mistakes cost real money here: paying the number printed largest on a
 * cumulative application, and reading "שוטף + 30" as thirty days from the
 * invoice. Both are pinned.
 */
class PaymentsTest {

    private val penny = 0.005

    @Test
    fun `an application claims everything to date, and only the difference is paid`() {
        // Three applications on a two million contract. Each says what the work
        // is worth in total, not what was done that month.
        val first = Payments.assess(
            grossToDate = 400_000.0,
            previouslyPaidNet = 0.0,
            contractSum = 2_000_000.0,
        )
        assertEquals(20_000.0, first.retentionHeld, penny)
        assertEquals(380_000.0, first.netToDate, penny)
        assertEquals(380_000.0, first.dueNow, penny)

        val second = Payments.assess(
            grossToDate = 900_000.0,
            previouslyPaidNet = 380_000.0,
            contractSum = 2_000_000.0,
        )
        // Not 855,000. The month's work is the difference, and paying the face
        // of the application would pay for the first 400,000 all over again.
        assertEquals(855_000.0, second.netToDate, penny)
        assertEquals(475_000.0, second.dueNow, penny)
    }

    @Test
    fun `a re-measure downwards shows as money owed back, not as zero`() {
        // Something was over-certified last month and has been taken out.
        val corrected = Payments.assess(
            grossToDate = 700_000.0,
            previouslyPaidNet = 855_000.0,
            contractSum = 2_000_000.0,
        )
        assertTrue(corrected.dueNow < 0.0)
        assertEquals(-190_000.0, corrected.dueNow, penny)
    }

    @Test
    fun `retention stops at the limit rather than growing with the job`() {
        val contract = 1_000_000.0
        // Five per cent of work done, while that is under five per cent of the
        // contract.
        assertEquals(25_000.0, Payments.retentionOn(500_000.0, contract), penny)
        assertEquals(50_000.0, Payments.retentionOn(1_000_000.0, contract), penny)
        // The job has run over. Retention does not follow it up.
        assertEquals(50_000.0, Payments.retentionOn(1_400_000.0, contract), penny)
    }

    @Test
    fun `a job with no contract value entered is not capped at nothing`() {
        assertEquals(5_000.0, Payments.retentionOn(100_000.0, contractSum = 0.0), penny)
    }

    @Test
    fun `nothing certified holds no retention`() {
        assertEquals(0.0, Payments.retentionOn(0.0, 1_000_000.0), penny)
        assertEquals(0.0, Payments.retentionOn(-5.0, 1_000_000.0), penny)
    }

    @Test
    fun `retention comes back in two halves`() {
        assertEquals(25_000.0, Payments.releasedAtCompletion(50_000.0), penny)
        assertEquals(25_000.0, Payments.releasedAfterDefects(50_000.0), penny)
        // An odd amount does not lose a shekel between the two.
        val odd = 12_345.67
        assertEquals(
            odd,
            Payments.releasedAtCompletion(odd) + Payments.releasedAfterDefects(odd),
            penny,
        )
    }

    @Test
    fun `shotef plus thirty runs from the end of the month, not the invoice`() {
        // The whole point. An invoice on the 3rd of March is due on the 30th of
        // April, not the 2nd — a month later than anybody reading "net 30"
        // would budget for.
        assertEquals(
            LocalDate.of(2026, 4, 30),
            Payments.dueDate(LocalDate.of(2026, 3, 3), Payments.Terms.SHOTEF_30),
        )
        // And an invoice on the last day of the same month is due on the same
        // day, which is why nobody wants to invoice on the 1st.
        assertEquals(
            LocalDate.of(2026, 4, 30),
            Payments.dueDate(LocalDate.of(2026, 3, 31), Payments.Terms.SHOTEF_30),
        )
    }

    @Test
    fun `the longer terms count from the same place`() {
        val invoice = LocalDate.of(2026, 3, 3)
        assertEquals(
            LocalDate.of(2026, 5, 30),
            Payments.dueDate(invoice, Payments.Terms.SHOTEF_60),
        )
        assertEquals(
            LocalDate.of(2026, 6, 29),
            Payments.dueDate(invoice, Payments.Terms.SHOTEF_90),
        )
        assertEquals(
            LocalDate.of(2026, 7, 29),
            Payments.dueDate(invoice, Payments.Terms.SHOTEF_120),
        )
    }

    @Test
    fun `a short month is still the end of that month`() {
        // February, and a leap year at that, so the arithmetic cannot be
        // hiding a thirty-day assumption.
        assertEquals(
            LocalDate.of(2028, 3, 30),
            Payments.dueDate(LocalDate.of(2028, 2, 1), Payments.Terms.SHOTEF_30),
        )
        assertEquals(
            LocalDate.of(2026, 3, 30),
            Payments.dueDate(LocalDate.of(2026, 2, 14), Payments.Terms.SHOTEF_30),
        )
    }

    @Test
    fun `payment on presentation does not wait for the end of the month`() {
        val invoice = LocalDate.of(2026, 3, 3)
        assertEquals(invoice, Payments.dueDate(invoice, Payments.Terms.IMMEDIATE))
    }

    @Test
    fun `overdue counts forward, and a payment not yet due is not overdue`() {
        val due = LocalDate.of(2026, 4, 30)
        assertEquals(0L, Payments.daysOverdue(due, LocalDate.of(2026, 4, 30)))
        assertEquals(14L, Payments.daysOverdue(due, LocalDate.of(2026, 5, 14)))
        assertTrue(Payments.daysOverdue(due, LocalDate.of(2026, 4, 1)) < 0L)
    }

    @Test
    fun `only a submitted application with a figure on it can be certified`() {
        assertTrue(Payments.canCertify(Payments.Status.SUBMITTED, 100.0))
        assertFalse(Payments.canCertify(Payments.Status.SUBMITTED, null))
        assertFalse(Payments.canCertify(Payments.Status.DRAFT, 100.0))
        assertFalse(Payments.canCertify(Payments.Status.CERTIFIED, 100.0))
        assertFalse(Payments.canCertify(Payments.Status.PAID, 100.0))
    }

    @Test
    fun `only a certified application can be marked paid`() {
        assertTrue(Payments.canMarkPaid(Payments.Status.CERTIFIED))
        assertFalse(Payments.canMarkPaid(Payments.Status.SUBMITTED))
        assertFalse(Payments.canMarkPaid(Payments.Status.PAID))
    }

    // The running total. Which cheque an application is measured against is
    // not something it can remember, because the money it is measured against
    // usually arrives after it was raised.

    private fun settled(
        number: Int,
        certified: Double,
        direction: String = "RECEIVABLE",
        rate: Double = 0.05,
        limit: Double = 0.05,
    ) = Payments.Settled(number, direction, certified, rate, limit)

    @Test
    fun `an application raised before the last one was paid still counts it`() {
        // The ordinary month under shotef plus thirty. Application two is
        // raised at the end of February, while application one — certified in
        // January — is still waiting. Nothing has been paid yet.
        val contract = 2_000_000.0
        assertEquals(
            0.0,
            Payments.previouslyPaidNet(2, "RECEIVABLE", emptyList(), contract),
            penny,
        )

        // The cheque for application one lands on the 30th of March. Only then
        // is application two certified.
        val paid = listOf(settled(number = 1, certified = 400_000.0))
        val previously = Payments.previouslyPaidNet(2, "RECEIVABLE", paid, contract)
        assertEquals(380_000.0, previously, penny)

        val second = Payments.assess(900_000.0, previously, contract)
        // 475,000, which is February's work. An application that had kept the
        // "nothing paid yet" it was raised with would ask for 855,000 — the
        // whole job to date, with January in it twice.
        assertEquals(475_000.0, second.dueNow, penny)
    }

    @Test
    fun `only the last paid application counts, because they are cumulative`() {
        val contract = 2_000_000.0
        val paid = listOf(
            settled(number = 1, certified = 400_000.0),
            settled(number = 2, certified = 900_000.0),
        )
        // 855,000, not 380,000 + 855,000. Application two already contains
        // application one.
        assertEquals(
            855_000.0,
            Payments.previouslyPaidNet(3, "RECEIVABLE", paid, contract),
            penny,
        )
    }

    @Test
    fun `an application is not measured against the other direction`() {
        // We are paying a subcontractor on the same job that we are claiming
        // from the client. Two sequences, both numbered from one.
        val contract = 2_000_000.0
        val paid = listOf(
            settled(number = 1, certified = 400_000.0, direction = "RECEIVABLE"),
            settled(number = 1, certified = 250_000.0, direction = "PAYABLE"),
            settled(number = 2, certified = 900_000.0, direction = "PAYABLE"),
        )
        assertEquals(
            380_000.0,
            Payments.previouslyPaidNet(2, "RECEIVABLE", paid, contract),
            penny,
        )
        assertEquals(
            855_000.0,
            Payments.previouslyPaidNet(3, "PAYABLE", paid, contract),
            penny,
        )
    }

    @Test
    fun `a later application is never mistaken for an earlier one`() {
        // Application four is being looked at while nine has already been
        // paid — which happens when an old one is settled out of order, or
        // when somebody opens last spring's paperwork.
        val contract = 2_000_000.0
        val paid = listOf(
            settled(number = 3, certified = 400_000.0),
            settled(number = 9, certified = 1_800_000.0),
        )
        assertEquals(
            380_000.0,
            Payments.previouslyPaidNet(4, "RECEIVABLE", paid, contract),
            penny,
        )
    }

    @Test
    fun `nothing paid before the first application`() {
        val paid = listOf(settled(number = 1, certified = 400_000.0))
        assertEquals(
            0.0,
            Payments.previouslyPaidNet(1, "RECEIVABLE", paid, 2_000_000.0),
            penny,
        )
    }

    @Test
    fun `an earlier application keeps the retention it was paid under`() {
        // Retention was renegotiated down from ten per cent to five partway
        // through. The cheque already banked was 360,000, not 380,000, and
        // restating it would hand over 20,000 that was never paid.
        val contract = 2_000_000.0
        val paid = listOf(settled(number = 1, certified = 400_000.0, rate = 0.10, limit = 0.10))
        val previously = Payments.previouslyPaidNet(2, "RECEIVABLE", paid, contract)
        assertEquals(360_000.0, previously, penny)
        assertEquals(495_000.0, Payments.assess(900_000.0, previously, contract).dueNow, penny)
    }

    @Test
    fun `certifying less than was claimed is what the difference is for`() {
        // The subcontractor claims 900,000; the surveyor measures 820,000.
        val claimed = Payments.assess(900_000.0, 380_000.0, 2_000_000.0)
        val certified = Payments.assess(820_000.0, 380_000.0, 2_000_000.0)
        assertEquals(475_000.0, claimed.dueNow, penny)
        assertEquals(399_000.0, certified.dueNow, penny)
    }
}
