package il.co.tradesmanager.core.evidence

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The day arithmetic is the part worth pinning. Israel changes its clocks
 * twice a year, and a log built on "plus 24 hours" is wrong on both of those
 * days in a way nobody notices until they go looking for what happened.
 */
class DailyLogTest {

    private val israel: ZoneId = ZoneId.of("Asia/Jerusalem")
    private val hour = 60L * 60L * 1000L
    private val signature = "0.2,0.5 0.4,0.3 0.6,0.5"

    private fun day(text: String) = LocalDate.parse(text).toEpochDay()

    private fun at(date: String, hourOfDay: Int, minute: Int = 0): Long =
        LocalDate.parse(date).atTime(hourOfDay, minute).atZone(israel).toInstant().toEpochMilli()

    @Test
    fun `a log covers local midnight to local midnight`() {
        val window = DailyLog.windowFor(day("2026-09-05"), israel)
        assertEquals(at("2026-09-05", 0), window.first)
        // Exclusive: midnight belongs to the day that is starting.
        assertEquals(at("2026-09-06", 0) - 1, window.last)
    }

    @Test
    fun `the last minute of the day is in, the first minute of the next is not`() {
        val fifth = day("2026-09-05")
        assertTrue(DailyLog.covers(fifth, at("2026-09-05", 23, 59), israel))
        assertFalse(DailyLog.covers(fifth, at("2026-09-06", 0, 0), israel))
        assertFalse(DailyLog.covers(fifth, at("2026-09-04", 23, 59), israel))
    }

    @Test
    fun `the day the clocks go forward is twenty-three hours, not twenty-four`() {
        // Israel moves to summer time on 27 March 2026. A fixed 24-hour window
        // would reach an hour into the 28th and claim work that was not done.
        val window = DailyLog.windowFor(day("2026-03-27"), israel)
        val length = window.last - window.first + 1
        assertEquals(23 * hour, length)
        assertFalse(DailyLog.covers(day("2026-03-27"), at("2026-03-28", 0, 0), israel))
    }

    @Test
    fun `the day the clocks go back is twenty-five hours, not twenty-four`() {
        // Israel returns to winter time on 25 October 2026. A fixed 24-hour
        // window would stop an hour early and lose the end of the shift.
        val window = DailyLog.windowFor(day("2026-10-25"), israel)
        val length = window.last - window.first + 1
        assertEquals(25 * hour, length)
        assertTrue(DailyLog.covers(day("2026-10-25"), at("2026-10-25", 23, 30), israel))
    }

    @Test
    fun `a moment maps back to the day it belongs to`() {
        val fifth = day("2026-09-05")
        assertEquals(fifth, DailyLog.dayOf(at("2026-09-05", 6, 30), israel))
        assertEquals(fifth, DailyLog.dayOf(at("2026-09-05", 23, 59), israel))
        assertEquals(day("2026-09-06"), DailyLog.dayOf(at("2026-09-06", 0, 1), israel))
    }

    @Test
    fun `the stored date does not move when the record is opened elsewhere`() {
        // A log for the fifth of September is the fifth of September to whoever
        // opens it, wherever they are. Only the window is local.
        val fifth = day("2026-09-05")
        assertEquals(LocalDate.of(2026, 9, 5), LocalDate.ofEpochDay(fifth))
        val abroad = ZoneId.of("America/New_York")
        assertEquals(LocalDate.of(2026, 9, 5), LocalDate.ofEpochDay(fifth))
        // The window differs abroad, which is expected and why the label is
        // stored as a date rather than derived from one.
        assertTrue(DailyLog.windowFor(fifth, abroad).first > DailyLog.windowFor(fifth, israel).first)
    }

    @Test
    fun `a signed log cannot be edited`() {
        assertTrue(DailyLog.canEdit(DailyLog.Status.DRAFT))
        assertFalse(DailyLog.canEdit(DailyLog.Status.SIGNED))
    }

    @Test
    fun `an unknown status is a draft, never a closed record`() {
        assertEquals(DailyLog.State.DRAFT, DailyLog.state("SOMETHING_LATER"))
        assertTrue(DailyLog.canEdit("SOMETHING_LATER"))
    }

    @Test
    fun `signing needs a real signature and an unsigned log`() {
        assertTrue(DailyLog.canSign(DailyLog.Status.DRAFT, signature))
        assertFalse(DailyLog.canSign(DailyLog.Status.DRAFT, null))
        assertFalse(DailyLog.canSign(DailyLog.Status.DRAFT, "0.5,0.5"))
        assertFalse(DailyLog.canSign(DailyLog.Status.SIGNED, signature))
    }

    @Test
    fun `a day where the app watched nothing happen says so`() {
        assertTrue(DailyLog.Summary().isQuiet)
        assertFalse(DailyLog.Summary(talksHeld = 1).isQuiet)
        assertFalse(DailyLog.Summary(incidents = 1).isQuiet)
    }
}
