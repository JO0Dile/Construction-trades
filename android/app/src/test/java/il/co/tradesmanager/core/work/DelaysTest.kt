package il.co.tradesmanager.core.work

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DelaysTest {

    private val sunday = LocalDate.of(2026, 10, 4)

    @Test
    fun `a delay says what happened, and has already begun`() {
        assertEquals(Delays.Refusal.BLANK_DESCRIPTION, Delays.recordRefusal(" ", sunday, sunday))
        assertEquals(Delays.Refusal.STARTS_IN_FUTURE, Delays.recordRefusal("Rain", sunday.plusDays(1), sunday))
        assertNull(Delays.recordRefusal("Rain", sunday, sunday))
        assertNull(Delays.recordRefusal("Rain", sunday.minusDays(10), sunday))
    }

    @Test
    fun `an end is written once, and not before the start`() {
        assertNull(Delays.endRefusal(sunday, null, sunday))
        assertEquals(Delays.Refusal.ENDS_BEFORE_START, Delays.endRefusal(sunday, null, sunday.minusDays(1)))
        assertEquals(Delays.Refusal.ALREADY_ENDED, Delays.endRefusal(sunday, sunday.plusDays(2), sunday.plusDays(3)))
    }

    @Test
    fun `notice goes to somebody, once, and not before the delay began`() {
        assertNull(Delays.noticeRefusal(sunday, null, "The client", sunday))
        assertEquals(Delays.Refusal.NOBODY_NOTIFIED, Delays.noticeRefusal(sunday, null, " ", sunday))
        assertEquals(Delays.Refusal.NOTICE_BEFORE_START, Delays.noticeRefusal(sunday, null, "The client", sunday.minusDays(1)))
        assertEquals(Delays.Refusal.NOTICE_ALREADY_GIVEN, Delays.noticeRefusal(sunday, sunday, "The client", sunday.plusDays(1)))
    }

    @Test
    fun `days count the first and the last, and one still going counts to today`() {
        assertEquals("rain on Sunday and Monday is two days", 2L, Delays.days(sunday, sunday.plusDays(1), sunday.plusDays(9)))
        assertEquals("one day", 1L, Delays.days(sunday, sunday, sunday.plusDays(9)))
        assertEquals(4L, Delays.days(sunday, null, sunday.plusDays(3)))
        assertEquals("never negative", 0L, Delays.days(sunday, sunday.minusDays(2), sunday))
    }

    @Test
    fun `days add up per cause, overlaps each counted in full`() {
        val events = listOf(
            Triple(Delays.Cause.WEATHER, sunday, sunday.plusDays(1)),
            Triple(Delays.Cause.WEATHER, sunday.plusDays(1), sunday.plusDays(2)),
            Triple(Delays.Cause.LATE_INFORMATION, sunday, null),
        )
        val byCause = Delays.daysByCause(events, sunday.plusDays(4))
        assertEquals(4L, byCause[Delays.Cause.WEATHER])
        assertEquals(5L, byCause[Delays.Cause.LATE_INFORMATION])
        assertNull(byCause[Delays.Cause.SUPPLY])
    }

    @Test
    fun `still going first, then the most recent start`() {
        val rows = listOf(
            false to sunday.minusDays(10),
            true to sunday.minusDays(20),
            false to sunday.minusDays(1),
            true to sunday.minusDays(2),
        )
        assertEquals(listOf(3, 1, 2, 0), Delays.order(rows))
    }

    @Test
    fun `numbered per job, and a cause this version does not know reads as other`() {
        assertEquals("DE-001", Delays.reference(0))
        assertEquals("DE-020", Delays.reference(19))
        assertEquals(Delays.Cause.UTILITIES, Delays.causeOf("UTILITIES"))
        assertEquals(Delays.Cause.OTHER, Delays.causeOf("LOCUSTS"))
    }
}
