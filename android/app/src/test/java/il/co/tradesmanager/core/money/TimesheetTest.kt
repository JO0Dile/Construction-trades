package il.co.tradesmanager.core.money

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hours have to be gathered into person-days before any overtime band is
 * applied, and a night shift belongs to the day it started.
 */
class TimesheetTest {

    private val israel: ZoneId = ZoneId.of("Asia/Jerusalem")
    private val cent = 0.005

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(israel).toInstant().toEpochMilli()

    /**
     * A finished shift. A null [to] is somebody still clocked in, which the
     * view model filters out before it gets here — represented as no shift at
     * all, because an unfinished shift has no hours to count.
     */
    private fun entry(
        worker: String,
        from: String,
        to: String?,
        rate: Double? = 50.0,
    ): Timesheet.Shift? = to?.let {
        Timesheet.Shift(
            workerName = worker,
            startedAt = at(from),
            minutes = (at(it) - at(from)) / 60_000L,
            hourlyRate = rate,
        )
    }

    @Test
    fun `two shifts in one day are one day of overtime, not two short days`() {
        // Morning and afternoon, five hours each. Ten hours in the day, so two
        // of them are overtime — not zero, which is what treating each shift
        // separately would give.
        val days = Timesheet.personDays(
            listOfNotNull(
                entry("Yossi", "2026-06-01T06:00", "2026-06-01T11:00"),
                entry("Yossi", "2026-06-01T12:00", "2026-06-01T17:00"),
            ),
            israel,
        )
        assertEquals(1, days.size)
        assertEquals(8.0, days[0].hours.ordinary, cent)
        assertEquals(2.0, days[0].hours.atFirstOvertimeRate, cent)
        assertEquals(10.5 * 50.0, days[0].cost!!, cent)
    }

    @Test
    fun `a night shift belongs to the day it started`() {
        // Ten at night until six. One long night with overtime, not two short
        // days with none.
        val days = Timesheet.personDays(
            listOfNotNull(entry("Nadia", "2026-06-01T22:00", "2026-06-02T06:00")),
            israel,
        )
        assertEquals(1, days.size)
        assertEquals(LocalDate.of(2026, 6, 1), days[0].date)
        assertEquals(8.0, days[0].hours.total, cent)
    }

    @Test
    fun `two people on the same day are two rows`() {
        val days = Timesheet.personDays(
            listOfNotNull(
                entry("Yossi", "2026-06-01T06:00", "2026-06-01T14:00"),
                entry("Amir", "2026-06-01T06:00", "2026-06-01T14:00"),
            ),
            israel,
        )
        assertEquals(2, days.size)
        assertEquals(setOf("Yossi", "Amir"), days.map { it.workerName }.toSet())
    }

    @Test
    fun `a shift nobody has clocked out of never becomes a timesheet row`() {
        // Its hours would change every time the screen was looked at, so the
        // query never returns it and there is no shift to count.
        assertNull(entry("Yossi", "2026-06-01T06:00", null))
        assertTrue(Timesheet.personDays(emptyList(), israel).isEmpty())
    }

    @Test
    fun `a rest day is decided per person, not by the calendar`() {
        // Saturday for one, Friday for another. Hard-coding Saturday would
        // underpay the second.
        val entries = listOfNotNull(
            entry("Yossi", "2026-06-06T08:00", "2026-06-06T14:00"),
            entry("Amir", "2026-06-05T08:00", "2026-06-05T14:00"),
        )
        val days = Timesheet.personDays(entries, israel) { worker, date ->
            when (worker) {
                "Yossi" -> date.dayOfWeek == java.time.DayOfWeek.SATURDAY
                else -> date.dayOfWeek == java.time.DayOfWeek.FRIDAY
            }
        }
        assertTrue(days.all { it.isRestDay })
        // Six hours each, all at the rest-day rate: nine payable hours.
        assertTrue(days.all { it.hours.ordinary == 0.0 })
        assertTrue(days.all { it.cost!! == 9.0 * 50.0 })
    }

    @Test
    fun `a day with no rate costs an unknown amount and is counted as unpriced`() {
        val days = Timesheet.personDays(
            listOfNotNull(entry("Yossi", "2026-06-01T06:00", "2026-06-01T14:00", rate = null)),
            israel,
        )
        assertNull(days[0].cost)
        assertTrue(Timesheet.hasUnpricedDays(days))
        // And it contributes nothing to the total rather than a wrong figure.
        assertEquals(0.0, Timesheet.totalCost(days), cent)
    }

    @Test
    fun `totals add the bands per person rather than re-splitting the week`() {
        val days = Timesheet.personDays(
            listOfNotNull(
                entry("Yossi", "2026-06-01T06:00", "2026-06-01T15:00"),
                entry("Yossi", "2026-06-02T06:00", "2026-06-02T15:00"),
            ),
            israel,
        )
        val totals = Timesheet.totals(days)
        assertEquals(1, totals.size)
        // Two nine-hour days: sixteen ordinary and two at the first band.
        assertEquals(16.0, totals[0].hours.ordinary, cent)
        assertEquals(2.0, totals[0].hours.atFirstOvertimeRate, cent)
        assertEquals(0.0, totals[0].hours.atFurtherOvertimeRate, cent)
        assertEquals(18.5 * 50.0, totals[0].cost!!, cent)
        assertFalse(totals[0].rateIsIncomplete)
    }

    @Test
    fun `a person priced on some days and not others still shows what is known`() {
        val days = Timesheet.personDays(
            listOfNotNull(
                entry("Yossi", "2026-06-01T06:00", "2026-06-01T14:00", rate = 50.0),
                entry("Yossi", "2026-06-02T06:00", "2026-06-02T14:00", rate = null),
            ),
            israel,
        )
        val total = Timesheet.totals(days).single()
        assertEquals(1, total.daysWithoutRate)
        assertTrue(total.rateIsIncomplete)
        // The priced day is still worth showing, as long as the screen says so.
        assertEquals(8.0 * 50.0, total.cost!!, cent)
        assertEquals(16.0, total.hours.total, cent)
    }

    @Test
    fun `newest day first, and people in order within a day`() {
        val days = Timesheet.personDays(
            listOfNotNull(
                entry("Yossi", "2026-06-01T06:00", "2026-06-01T14:00"),
                entry("Amir", "2026-06-03T06:00", "2026-06-03T14:00"),
                entry("Boaz", "2026-06-03T06:00", "2026-06-03T14:00"),
            ),
            israel,
        )
        assertEquals(LocalDate.of(2026, 6, 3), days[0].date)
        assertEquals("Amir", days[0].workerName)
        assertEquals("Boaz", days[1].workerName)
        assertEquals("Yossi", days[2].workerName)
    }

    @Test
    fun `nothing recorded is an empty timesheet, not a zero-cost one`() {
        val days = Timesheet.personDays(emptyList(), israel)
        assertTrue(days.isEmpty())
        assertTrue(Timesheet.totals(days).isEmpty())
        assertFalse(Timesheet.hasUnpricedDays(days))
    }
}
