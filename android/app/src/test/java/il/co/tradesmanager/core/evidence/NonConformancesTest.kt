package il.co.tradesmanager.core.evidence

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NonConformancesTest {

    private val today = LocalDate.of(2026, 10, 3)

    @Test
    fun `a report says where, against what, and what was found`() {
        assertEquals(NonConformances.Refusal.BLANK_ELEMENT, NonConformances.raiseRefusal(" ", "S-102", "Cover 25 mm"))
        assertEquals(NonConformances.Refusal.BLANK_REQUIREMENT, NonConformances.raiseRefusal("L3 slab", " ", "Cover 25 mm"))
        assertEquals(NonConformances.Refusal.BLANK_FINDING, NonConformances.raiseRefusal("L3 slab", "S-102", ""))
        assertNull(NonConformances.raiseRefusal("L3 slab", "S-102", "Cover 25 mm"))
    }

    @Test
    fun `kept as it is names who agreed, anything else says what will be done`() {
        fun decide(d: NonConformances.Disposition, by: String = "", fix: String = "", due: LocalDate? = null, decided: Boolean = false, closed: Boolean = false) =
            NonConformances.decideRefusal(d, by, fix, due, today, decided, closed)
        assertEquals(NonConformances.Refusal.NO_ACCEPTOR, decide(NonConformances.Disposition.ACCEPT_AS_IS))
        assertNull(decide(NonConformances.Disposition.ACCEPT_AS_IS, by = "Dana Levi, supervisor"))
        assertEquals(NonConformances.Refusal.BLANK_CORRECTION, decide(NonConformances.Disposition.REPAIR, by = "Dana Levi"))
        assertNull(decide(NonConformances.Disposition.REWORK, fix = "Break out and recast"))
        assertEquals(NonConformances.Refusal.DUE_BEFORE_RAISED, decide(NonConformances.Disposition.REMOVE, fix = "Take it out", due = today.minusDays(1)))
        assertEquals(NonConformances.Refusal.ALREADY_DECIDED, decide(NonConformances.Disposition.REWORK, fix = "x", decided = true))
        assertEquals(NonConformances.Refusal.ALREADY_CLOSED, decide(NonConformances.Disposition.REWORK, fix = "x", decided = true, closed = true))
    }

    @Test
    fun `it closes only once decided, with how it was checked`() {
        assertEquals(NonConformances.Refusal.NOT_DECIDED, NonConformances.closeRefusal(decided = false, verification = "Checked", closed = false))
        assertEquals(NonConformances.Refusal.BLANK_VERIFICATION, NonConformances.closeRefusal(decided = true, verification = " ", closed = false))
        assertEquals(NonConformances.Refusal.ALREADY_CLOSED, NonConformances.closeRefusal(decided = true, verification = "Checked", closed = true))
        assertNull(NonConformances.closeRefusal(decided = true, verification = "Checked by the supervisor", closed = false))
    }

    @Test
    fun `overdue from the day after its date, and waiting for a decision before that`() {
        assertEquals(NonConformances.State.AWAITING_DECISION, NonConformances.state(false, null, false, today))
        assertEquals(NonConformances.State.IN_HAND, NonConformances.state(true, today, false, today))
        assertEquals(NonConformances.State.IN_HAND, NonConformances.state(true, null, false, today))
        assertEquals(NonConformances.State.OVERDUE, NonConformances.state(true, today.minusDays(1), false, today))
        assertEquals(NonConformances.State.CLOSED, NonConformances.state(true, today.minusDays(9), true, today))
    }

    @Test
    fun `overdue first, then undecided, then in hand oldest first, then closed newest first`() {
        val rows = listOf(
            NonConformances.State.CLOSED to 100L,
            NonConformances.State.IN_HAND to 50L,
            NonConformances.State.CLOSED to 300L,
            NonConformances.State.AWAITING_DECISION to 70L,
            NonConformances.State.IN_HAND to 20L,
            NonConformances.State.OVERDUE to 90L,
        )
        assertEquals(listOf(5, 3, 4, 1, 2, 0), NonConformances.order(rows))
    }

    @Test
    fun `numbered per job, and an unknown stored value reads safely`() {
        assertEquals("NCR-001", NonConformances.reference(0))
        assertEquals("NCR-012", NonConformances.reference(11))
        assertEquals(NonConformances.FoundBy.OWN_QUALITY_CONTROL, NonConformances.foundByOf("SOMEONE"))
        assertNull(NonConformances.dispositionOf(null))
        assertEquals(NonConformances.Disposition.REPAIR, NonConformances.dispositionOf("REPAIR"))
    }
}
