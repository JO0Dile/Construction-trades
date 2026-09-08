package il.co.tradesmanager.core.safety

import il.co.tradesmanager.core.safety.Incidents.Blocker
import il.co.tradesmanager.core.safety.Incidents.Report
import il.co.tradesmanager.core.safety.Incidents.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /* ------------------------------------------------- what a report needs */

    private fun report(
        description: String = "scaffold board gave way",
        evidenceCount: Int = 1,
        cost: Double? = null,
    ) = Report(description, evidenceCount, cost)

    @Test
    fun `a described report with a picture can be filed`() {
        assertNull(Incidents.blocksReporting(report()))
        assertTrue(Incidents.canReport(report()))
    }

    @Test
    fun `nothing written, nothing filed`() {
        // A severity chip on its own says nothing anybody can act on.
        assertEquals(Blocker.NOT_DESCRIBED, Incidents.blocksReporting(report(description = " ")))
    }

    @Test
    fun `a report with no photograph or video is refused`() {
        // By the time this is argued about, the ladder has been moved and the
        // spill mopped. A description on its own is one person's word.
        assertEquals(Blocker.NO_EVIDENCE, Incidents.blocksReporting(report(evidenceCount = 0)))
    }

    @Test
    fun `no cost is a real answer and zero is a different one`() {
        // Most reports are filed by somebody who has no idea yet what it will
        // come to. Forcing a figure would get zero, which is a claim.
        assertTrue(Incidents.acceptableCost(null))
        assertTrue(Incidents.acceptableCost(0.0))
        assertNull(Incidents.blocksReporting(report(cost = null)))
        assertNull(Incidents.blocksReporting(report(cost = 0.0)))
    }

    @Test
    fun `a negative cost is refused`() {
        assertFalse(Incidents.acceptableCost(-1.0))
        assertEquals(Blocker.BAD_COST, Incidents.blocksReporting(report(cost = -0.01)))
    }

    @Test
    fun `what is written comes before what is attached`() {
        // One message at a time, and the first thing missing is the one to ask
        // for -- sending somebody to the camera before they have said what
        // they are photographing is the wrong order.
        assertEquals(
            Blocker.NOT_DESCRIBED,
            Incidents.blocksReporting(report(description = "", evidenceCount = 0)),
        )
    }
}
