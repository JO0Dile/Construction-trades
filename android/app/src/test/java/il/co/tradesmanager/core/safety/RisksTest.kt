package il.co.tradesmanager.core.safety

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RisksTest {

    private val today = LocalDate.of(2026, 10, 1)

    private fun refusal(
        activity: String = "Formwork at the slab edge",
        hazard: String = "Fall from height",
        lb: Int = 4,
        sb: Int = 5,
        controls: String = "Edge protection, harness to anchor",
        la: Int = 2,
        sa: Int = 5,
        reviewOn: LocalDate? = null,
    ) = Risks.refusal(activity, hazard, lb, sb, controls, la, sa, reviewOn, today)

    @Test
    fun `an assessment names the activity and the hazard, on a one to five scale`() {
        assertNull(refusal())
        assertEquals(Risks.Refusal.BLANK_ACTIVITY, refusal(activity = " "))
        assertEquals(Risks.Refusal.BLANK_HAZARD, refusal(hazard = ""))
        assertEquals(Risks.Refusal.OUT_OF_RANGE, refusal(lb = 0))
        assertEquals(Risks.Refusal.OUT_OF_RANGE, refusal(sa = 6))
    }

    @Test
    fun `a risk cannot come down with nothing done, nor go up because of what was done`() {
        assertEquals(Risks.Refusal.NO_CONTROLS, refusal(controls = " "))
        assertNull("no controls, and no change claimed, is honest", refusal(controls = "", la = 4, sa = 5))
        assertEquals(Risks.Refusal.RESIDUAL_ABOVE_INITIAL, refusal(lb = 2, sb = 2, la = 3, sa = 3))
    }

    @Test
    fun `a review date is not in the past`() {
        assertEquals(Risks.Refusal.REVIEW_IN_PAST, refusal(reviewOn = today.minusDays(1)))
        assertNull(refusal(reviewOn = today))
    }

    @Test
    fun `scores fall into bands at five, ten and fifteen`() {
        assertEquals(Risks.Band.LOW, Risks.band(Risks.score(2, 2)))
        assertEquals(Risks.Band.MEDIUM, Risks.band(5))
        assertEquals(Risks.Band.MEDIUM, Risks.band(9))
        assertEquals(Risks.Band.HIGH, Risks.band(10))
        assertEquals(Risks.Band.HIGH, Risks.band(12))
        assertEquals(Risks.Band.EXTREME, Risks.band(15))
        assertEquals(Risks.Band.EXTREME, Risks.band(Risks.score(5, 5)))
    }

    @Test
    fun `extreme outranks a late review, and a closed risk is only closed`() {
        assertEquals(Risks.State.EXTREME, Risks.state(false, 20, today.minusDays(9), today))
        assertEquals(Risks.State.REVIEW_OVERDUE, Risks.state(false, 6, today.minusDays(1), today))
        assertEquals("due today is not overdue", Risks.State.OPEN, Risks.state(false, 6, today, today))
        assertEquals(Risks.State.CLOSED, Risks.state(true, 25, today.minusDays(9), today))
    }

    @Test
    fun `extreme first, then overdue, then the highest residual score, closed last`() {
        val rows = listOf(
            Risks.State.OPEN to 4,
            Risks.State.CLOSED to 25,
            Risks.State.OPEN to 12,
            Risks.State.EXTREME to 16,
            Risks.State.REVIEW_OVERDUE to 3,
        )
        assertEquals(listOf(3, 4, 2, 0, 1), Risks.order(rows))
    }

    @Test
    fun `numbered per job`() {
        assertEquals("RA-001", Risks.reference(0))
        assertEquals("RA-042", Risks.reference(41))
    }
}
