package il.co.tradesmanager.core.safety

import il.co.tradesmanager.core.safety.Incidents.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidentsTest {

    @Test
    fun `severity runs least to most serious`() {
        assertEquals(
            listOf(Severity.NEAR_MISS, Severity.MINOR, Severity.SERIOUS, Severity.FATAL),
            Severity.entries.toList(),
        )
    }

    @Test
    fun `serious and fatal are flagged for escalation`() {
        assertFalse(Incidents.needsEscalating(Severity.NEAR_MISS))
        assertFalse(Incidents.needsEscalating(Severity.MINOR))
        assertTrue(Incidents.needsEscalating(Severity.SERIOUS))
        assertTrue(Incidents.needsEscalating(Severity.FATAL))
    }

    @Test
    fun `unrecognised text does not become a serious incident`() {
        // The column was free text before this existed, so a register may hold
        // anything. Reading a stray value as FATAL would raise a false alarm;
        // reading it as the least assuming value keeps the row visible without
        // asserting something about it.
        assertEquals(Severity.NEAR_MISS, Incidents.parse("not bad"))
        assertEquals(Severity.NEAR_MISS, Incidents.parse(null))
        assertEquals(Severity.NEAR_MISS, Incidents.parse(""))
        assertEquals(Severity.FATAL, Incidents.parse("FATAL"))
    }

    @Test
    fun `parsing is exact, not case-insensitive`() {
        // "fatal" is not FATAL. A register that quietly accepted both would
        // hold two values meaning the same thing, which is the problem the
        // controlled list exists to stop.
        assertEquals(Severity.NEAR_MISS, Incidents.parse("fatal"))
    }

    @Test
    fun `the register reads newest first, worst first within a moment`() {
        data class Row(val at: Long, val severity: Severity, val label: String)
        val rows = listOf(
            Row(100, Severity.MINOR, "older"),
            Row(200, Severity.NEAR_MISS, "newer near miss"),
            Row(200, Severity.FATAL, "newer fatal"),
        )
        assertEquals(
            listOf("newer fatal", "newer near miss", "older"),
            Incidents.order(rows, { it.at }, { it.severity }).map { it.label },
        )
    }
}
