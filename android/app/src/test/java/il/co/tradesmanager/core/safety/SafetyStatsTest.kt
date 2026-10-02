package il.co.tradesmanager.core.safety

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyStatsTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val hour = 3_600_000L
    private val today = LocalDate.of(2026, 10, 2)

    @Test
    fun `a period still running ends today, and last month is the whole of it`() {
        assertEquals(SafetyStats.Span(LocalDate.of(2026, 10, 1), today), SafetyStats.span(SafetyStats.Period.THIS_MONTH, today))
        assertEquals(
            SafetyStats.Span(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
            SafetyStats.span(SafetyStats.Period.LAST_MONTH, today),
        )
        assertEquals(SafetyStats.Span(LocalDate.of(2026, 10, 1), today), SafetyStats.span(SafetyStats.Period.THIS_QUARTER, today))
        assertEquals(
            SafetyStats.Span(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)),
            SafetyStats.span(SafetyStats.Period.THIS_QUARTER, LocalDate.of(2026, 9, 30)),
        )
        assertEquals(SafetyStats.Span(LocalDate.of(2026, 1, 1), today), SafetyStats.span(SafetyStats.Period.THIS_YEAR, today))
        assertEquals(SafetyStats.Span(LocalDate.of(2025, 10, 3), today), SafetyStats.span(SafetyStats.Period.LAST_12_MONTHS, today))
        // Last month from the 31st of March is February, all of it.
        assertEquals(
            SafetyStats.Span(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29)),
            SafetyStats.span(SafetyStats.Period.LAST_MONTH, LocalDate.of(2028, 3, 31)),
        )
    }

    @Test
    fun `the window runs from the first day's first moment to the end of the last, on the site's clock`() {
        val span = SafetyStats.Span(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        val window = SafetyStats.window(span, zone)
        assertEquals(span.first.atStartOfDay(zone).toInstant().toEpochMilli(), window.first)
        assertEquals(LocalDate.of(2026, 10, 1).atStartOfDay(zone).toInstant().toEpochMilli() - 1, window.last)
    }

    @Test
    fun `a shift counts only its part inside the window`() {
        val window = 100 * hour until 200 * hour
        assertEquals(8 * hour, SafetyStats.countedMillis(110 * hour, 118 * hour, window))
        // Started the night before the period opened.
        assertEquals(3 * hour, SafetyStats.countedMillis(95 * hour, 103 * hour, window))
        // Ran past the end.
        assertEquals(2 * hour, SafetyStats.countedMillis(198 * hour, 205 * hour, window))
        assertEquals(0L, SafetyStats.countedMillis(80 * hour, 90 * hour, window))
        assertEquals(0L, SafetyStats.countedMillis(120 * hour, 120 * hour, window))
        assertEquals(0L, SafetyStats.countedMillis(120 * hour, 110 * hour, window))
    }

    @Test
    fun `a shift nobody checked out of counts the roll call's sixteen hours, not the weekend`() {
        val window = 0L until 1_000 * hour
        assertEquals(Muster.STALE_AFTER_MS, SafetyStats.countedMillis(10 * hour, 70 * hour, window))
        assertTrue(SafetyStats.looksForgotten(10 * hour, 70 * hour))
        assertEquals(16 * hour, SafetyStats.countedMillis(10 * hour, 26 * hour, window))
        assertEquals(false, SafetyStats.looksForgotten(10 * hour, 26 * hour))
    }

    @Test
    fun `rates are per million hours, and there is no rate without hours`() {
        val figures = SafetyStats.Figures(workedMillis = 500_000 * hour, minorInjuries = 2, seriousInjuries = 1, nearMisses = 9)
        assertEquals(3, figures.injuries)
        assertEquals(6.0, figures.injuryRate!!, 1e-9)
        assertEquals(2.0, figures.seriousRate!!, 1e-9)
        assertEquals(3.0, figures.nearMissesPerInjury!!, 1e-9)
        assertNull(SafetyStats.Figures(minorInjuries = 1).injuryRate)
        assertEquals(0.0, SafetyStats.Figures(workedMillis = hour).injuryRate!!, 0.0)
        assertNull(SafetyStats.Figures(workedMillis = hour, nearMisses = 4).nearMissesPerInjury)
    }

    @Test
    fun `parts count in their job, and other companies' jobs count nowhere`() {
        val jobs = listOf(
            SafetyStats.Job("tower", null),
            SafetyStats.Job("tower.floor3", "tower"),
            SafetyStats.Job("tower.floor3.flat12", "tower.floor3"),
            SafetyStats.Job("house", null),
        )
        val window = 0L until 1_000 * hour
        val sheet = SafetyStats.sheetOf(
            jobs = jobs,
            window = window,
            shifts = listOf(
                SafetyStats.Shift("tower", 10 * hour, 18 * hour),
                SafetyStats.Shift("tower.floor3.flat12", 20 * hour, 28 * hour),
                SafetyStats.Shift("house", 30 * hour, 34 * hour),
                SafetyStats.Shift("someone-elses", 30 * hour, 40 * hour),
                SafetyStats.Shift("house", 2_000 * hour, 2_008 * hour),
            ),
            incidents = listOf(
                SafetyStats.Tally("tower.floor3", "MINOR", 1),
                SafetyStats.Tally("tower", "NEAR_MISS", 3),
                SafetyStats.Tally("someone-elses", "FATAL", 1),
                SafetyStats.Tally(null, "SERIOUS", 1),
            ),
            violations = listOf(SafetyStats.Tally("house", null, 2)),
            talks = listOf(SafetyStats.Tally("tower.floor3", null, 4)),
        )
        assertEquals(listOf("tower", "house"), sheet.rows.map { it.projectId })
        val tower = sheet.rows.first().figures
        assertEquals(16 * hour, tower.workedMillis)
        assertEquals(2, tower.shifts)
        assertEquals(1, tower.minorInjuries)
        assertEquals(3, tower.nearMisses)
        assertEquals(4, tower.talksHeld)
        assertEquals(20 * hour, sheet.total.workedMillis)
        assertEquals(1, sheet.total.injuries)
        assertEquals(0, sheet.total.fatalities)
        assertEquals(2, sheet.total.violations)
    }

    @Test
    fun `the worst job comes first, and a job with nothing in the period is left out`() {
        val jobs = listOf(SafetyStats.Job("a", null), SafetyStats.Job("b", null), SafetyStats.Job("c", null), SafetyStats.Job("idle", null))
        val window = 0L until 1_000 * hour
        val sheet = SafetyStats.sheetOf(
            jobs = jobs,
            window = window,
            shifts = listOf(
                SafetyStats.Shift("a", 0, 100 * hour),
                SafetyStats.Shift("b", 0, 8 * hour),
                SafetyStats.Shift("c", 0, 12 * hour),
            ),
            incidents = listOf(SafetyStats.Tally("b", "SERIOUS", 1)),
            violations = emptyList(),
            talks = emptyList(),
        )
        // The hundred-hour shift on "a" counts sixteen, still more than "c"'s twelve.
        assertEquals(listOf("b", "a", "c"), sheet.rows.map { it.projectId })
        assertEquals(16 * hour, sheet.rows[1].figures.workedMillis)
        assertEquals(1, sheet.total.shiftsCapped)
    }

    @Test
    fun `an unknown severity is counted as the least assuming one`() {
        val sheet = SafetyStats.sheetOf(
            jobs = listOf(SafetyStats.Job("a", null)),
            window = 0L until hour,
            shifts = emptyList(),
            incidents = listOf(SafetyStats.Tally("a", "not bad", 2)),
            violations = emptyList(),
            talks = emptyList(),
        )
        assertEquals(2, sheet.total.nearMisses)
        assertEquals(0, sheet.total.injuries)
    }

    @Test
    fun `a parent missing or a loop in the parts ends the climb instead of hanging`() {
        assertEquals("floor", SafetyStats.rootOf("floor", mapOf("floor" to "gone")))
        val loop = mapOf("x" to "y", "y" to "x")
        assertTrue(SafetyStats.rootOf("x", loop) in loop.keys)
    }

    @Test
    fun `days since are counted on the site's calendar`() {
        val lastNight = LocalDate.of(2026, 10, 1).atTime(23, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(1L, SafetyStats.daysSince(lastNight, today, zone))
        assertEquals(0L, SafetyStats.daysSince(today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(), today, zone))
        assertEquals(0L, SafetyStats.daysSince(today.plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli(), today, zone))
    }
}
