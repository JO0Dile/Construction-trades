package il.co.tradesmanager.core.work

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmittalsTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val thursday = LocalDate.of(2026, 10, 1)

    @Test
    fun `a submittal says what is proposed and to whom`() {
        val now = at(thursday, 9)
        assertEquals(Submittals.Refusal.BLANK_ITEM, Submittals.submitRefusal(" ", "Architect", now, null, zone))
        assertEquals(Submittals.Refusal.NOBODY_ASKED, Submittals.submitRefusal("Tile 60x60", "", now, null, zone))
        assertNull(Submittals.submitRefusal("Tile 60x60", "Architect", now, null, zone))
        assertEquals(
            Submittals.Refusal.NEEDED_BY_TOO_EARLY,
            Submittals.submitRefusal("Tile 60x60", "Architect", now, at(thursday.minusDays(1), 12), zone),
        )
        assertNull("needed today is fine", Submittals.submitRefusal("Tile 60x60", "Architect", at(thursday, 15), at(thursday, 0), zone))
    }

    @Test
    fun `an answer needs a name, a rejection its reason, and as-noted its notes`() {
        assertEquals(Submittals.Refusal.NO_REVIEWER, Submittals.decisionRefusal(Submittals.Decision.APPROVED, " ", "", null))
        assertNull(Submittals.decisionRefusal(Submittals.Decision.APPROVED, "Noa Cohen", "", null))
        assertEquals(
            Submittals.Refusal.REJECTED_WITHOUT_REASON,
            Submittals.decisionRefusal(Submittals.Decision.REJECTED, "Noa Cohen", "", null),
        )
        assertEquals(
            Submittals.Refusal.NOTES_MISSING,
            Submittals.decisionRefusal(Submittals.Decision.APPROVED_AS_NOTED, "Noa Cohen", " ", null),
        )
        assertEquals(
            "written once",
            Submittals.Refusal.ALREADY_DECIDED,
            Submittals.decisionRefusal(Submittals.Decision.APPROVED, "Noa Cohen", "", decidedAt = 1L),
        )
    }

    @Test
    fun `only a rejected submittal is sent again, and only once`() {
        assertNull(Submittals.resubmitRefusal(Submittals.Decision.REJECTED, alreadyResubmitted = false))
        assertEquals(Submittals.Refusal.ALREADY_RESUBMITTED, Submittals.resubmitRefusal(Submittals.Decision.REJECTED, true))
        assertEquals(Submittals.Refusal.NOT_REJECTED, Submittals.resubmitRefusal(Submittals.Decision.APPROVED_AS_NOTED, false))
        assertEquals("still waiting", Submittals.Refusal.NOT_REJECTED, Submittals.resubmitRefusal(null, false))
    }

    @Test
    fun `overdue from the day after, and a rejection holds the order until it is sent again`() {
        val needed = at(thursday, 0)
        assertEquals(Submittals.State.WAITING, Submittals.state(null, needed, false, at(thursday, 23), zone))
        assertEquals(Submittals.State.OVERDUE, Submittals.state(null, needed, false, at(thursday.plusDays(1), 8), zone))
        assertEquals(Submittals.State.REJECTED, Submittals.state(Submittals.Decision.REJECTED, needed, false, at(thursday, 9), zone))
        assertEquals(Submittals.State.SUPERSEDED, Submittals.state(Submittals.Decision.REJECTED, needed, true, at(thursday, 9), zone))
        assertEquals(
            "an answer is never overdue",
            Submittals.State.APPROVED,
            Submittals.state(Submittals.Decision.APPROVED_AS_NOTED, needed, false, at(thursday.plusDays(30), 9), zone),
        )
        assertTrue(Submittals.outstanding(Submittals.State.REJECTED))
        assertTrue(Submittals.outstanding(Submittals.State.OVERDUE))
        assertFalse(Submittals.outstanding(Submittals.State.SUPERSEDED))
        assertFalse(Submittals.outstanding(Submittals.State.APPROVED))
    }

    @Test
    fun `overdue first, then rejected, then waiting, then done`() {
        val rows = listOf(
            Submittals.State.APPROVED to 1L,
            Submittals.State.WAITING to 4L,
            Submittals.State.REJECTED to 7L,
            Submittals.State.OVERDUE to 9L,
            Submittals.State.WAITING to 2L,
        )
        assertEquals(listOf(3, 2, 4, 1, 0), Submittals.order(rows))
    }

    @Test
    fun `numbered per job, and a stored answer this version does not know is none`() {
        assertEquals("MS-001", Submittals.reference(0))
        assertEquals("MS-012", Submittals.reference(11))
        assertEquals(Submittals.Decision.REJECTED, Submittals.decisionOf("REJECTED"))
        assertNull(Submittals.decisionOf("PENDING_BOARD"))
        assertNull(Submittals.decisionOf(null))
    }
}
