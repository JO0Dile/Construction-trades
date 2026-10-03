package il.co.tradesmanager.core.safety

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklySafetyTest {

    @Test
    fun `the week is Sunday to Saturday, whichever day it is asked on`() {
        val sunday = LocalDate.of(2026, 10, 4)
        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
        (0L..6L).forEach { offset -> assertEquals(sunday, WeeklySafety.weekStarting(sunday.plusDays(offset))) }
        assertEquals(sunday.plusWeeks(1), WeeklySafety.weekStarting(sunday.plusDays(7)))
    }

    @Test
    fun `the window runs from Sunday's first moment to the next Sunday's, on the site's clock`() {
        val zone = ZoneId.of("Asia/Jerusalem")
        val sunday = LocalDate.of(2026, 10, 4)
        val window = WeeklySafety.window(sunday, zone)
        assertEquals(sunday.atStartOfDay(zone).toInstant().toEpochMilli(), window.first)
        assertEquals(sunday.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli() - 1, window.last)
    }

    @Test
    fun `a near miss written down does not make it a bad week, an incident does`() {
        assertTrue(WeeklySafety.Report(peopleOnSite = 30, nearMisses = 2, talksHeld = 3).nothingWentWrong)
        assertFalse(WeeklySafety.Report(incidents = 1).nothingWentWrong)
        assertFalse(WeeklySafety.Report(violations = 1).nothingWentWrong)
        assertFalse(WeeklySafety.Report(inspectionsFailed = 1).nothingWentWrong)
        assertFalse(WeeklySafety.Report(fireFaults = 1).nothingWentWrong)
    }
}
