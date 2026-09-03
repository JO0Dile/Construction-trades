package il.co.tradesmanager.core.time

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeOfDayTest {

    @Test
    fun `parses a normal time`() {
        assertEquals(7 * 60, TimeOfDay.parse("07:00", fallback = 0))
        assertEquals(17 * 60 + 45, TimeOfDay.parse("17:45", fallback = 0))
    }

    @Test
    fun `accepts a dot separator and surrounding spaces`() {
        assertEquals(8 * 60 + 30, TimeOfDay.parse(" 8.30 ", fallback = 0))
    }

    @Test
    fun `a bare hour means the top of that hour`() {
        assertEquals(9 * 60, TimeOfDay.parse("9", fallback = 0))
    }

    @Test
    fun `out of range and unreadable input falls back instead of throwing`() {
        val fallback = 6 * 60
        assertEquals(fallback, TimeOfDay.parse("25:00", fallback))
        assertEquals(fallback, TimeOfDay.parse("10:75", fallback))
        assertEquals(fallback, TimeOfDay.parse("morning", fallback))
        assertEquals(fallback, TimeOfDay.parse("", fallback))
        assertEquals(fallback, TimeOfDay.parse("08:xx", fallback))
    }

    @Test
    fun `formats back to a zero padded 24 hour string`() {
        assertEquals("07:00", TimeOfDay.format(7 * 60))
        assertEquals("00:05", TimeOfDay.format(5))
        assertEquals("23:59", TimeOfDay.format(TimeOfDay.MINUTES_PER_DAY - 1))
    }

    @Test
    fun `an end before its start is pushed out to a readable minimum`() {
        val (start, end) = TimeOfDay.sanitiseRange(start = 9 * 60, end = 8 * 60)
        assertEquals(9 * 60, start)
        assertEquals(9 * 60 + 15, end)
    }

    @Test
    fun `a valid range is left alone`() {
        assertEquals(7 * 60 to 12 * 60, TimeOfDay.sanitiseRange(7 * 60, 12 * 60))
    }

    @Test
    fun `a block late in the day cannot run past midnight`() {
        val (_, end) = TimeOfDay.sanitiseRange(start = 23 * 60 + 55, end = 23 * 60 + 56)
        assertEquals(TimeOfDay.MINUTES_PER_DAY, end)
    }
}
