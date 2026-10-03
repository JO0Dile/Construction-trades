package il.co.tradesmanager.core.safety

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvestigationsTest {

    private val today = LocalDate.of(2026, 10, 3)

    @Test
    fun `a serious injury or a death needs one, and a near miss may have one`() {
        assertEquals(Investigations.State.NEEDED, Investigations.state(Incidents.Severity.SERIOUS, started = false, closed = false))
        assertEquals(Investigations.State.NEEDED, Investigations.state(Incidents.Severity.FATAL, started = false, closed = false))
        assertEquals(Investigations.State.NOT_STARTED, Investigations.state(Incidents.Severity.NEAR_MISS, started = false, closed = false))
        assertEquals(Investigations.State.NOT_STARTED, Investigations.state(Incidents.Severity.MINOR, started = false, closed = false))
        assertEquals(Investigations.State.OPEN, Investigations.state(Incidents.Severity.MINOR, started = true, closed = false))
        assertEquals(Investigations.State.CLOSED, Investigations.state(Incidents.Severity.FATAL, started = true, closed = true))
        assertTrue(Investigations.outstanding(Investigations.State.NEEDED))
        assertTrue(Investigations.outstanding(Investigations.State.OPEN))
        assertFalse(Investigations.outstanding(Investigations.State.CLOSED))
        assertFalse(Investigations.outstanding(Investigations.State.NOT_STARTED))
    }

    @Test
    fun `it closes only once it says why, what lay behind it, and every action is done`() {
        val causes = setOf(Investigations.Cause.SUPERVISION)
        assertEquals(Investigations.Refusal.BLANK_IMMEDIATE_CAUSE, Investigations.closeRefusal(" ", causes, 0, closed = false))
        assertEquals(Investigations.Refusal.NO_CAUSE, Investigations.closeRefusal("The board slid", emptySet(), 0, closed = false))
        assertEquals(Investigations.Refusal.ACTIONS_OPEN, Investigations.closeRefusal("The board slid", causes, 1, closed = false))
        assertEquals(Investigations.Refusal.ALREADY_CLOSED, Investigations.closeRefusal("The board slid", causes, 0, closed = true))
        assertNull(Investigations.closeRefusal("The board slid", causes, 0, closed = false))
    }

    @Test
    fun `an action says what to do, and cannot be due before the incident`() {
        assertEquals(Investigations.Refusal.BLANK_ACTION, Investigations.actionRefusal(" ", null, today, false))
        assertEquals(Investigations.Refusal.DUE_BEFORE_INCIDENT, Investigations.actionRefusal("Brief the crew", today.minusDays(1), today, false))
        assertEquals(Investigations.Refusal.ALREADY_CLOSED, Investigations.actionRefusal("Brief the crew", null, today, true))
        assertNull(Investigations.actionRefusal("Brief the crew", today, today, false))
        assertEquals(Investigations.Refusal.BLANK_CLOSING_NOTE, Investigations.actionCloseRefusal(" ", false))
        assertEquals(Investigations.Refusal.ACTION_ALREADY_CLOSED, Investigations.actionCloseRefusal("Done on the 4th", true))
        assertNull(Investigations.actionCloseRefusal("Done on the 4th", false))
    }

    @Test
    fun `overdue from the day after its date, overdue first, no date last`() {
        assertEquals(Investigations.ActionState.OPEN, Investigations.actionState(today, closed = false, today = today))
        assertEquals(Investigations.ActionState.OVERDUE, Investigations.actionState(today.minusDays(1), closed = false, today = today))
        assertEquals(Investigations.ActionState.DONE, Investigations.actionState(today.minusDays(9), closed = true, today = today))
        val rows = listOf(
            Investigations.ActionSort(Investigations.ActionState.DONE, today.toEpochDay(), 1),
            Investigations.ActionSort(Investigations.ActionState.OPEN, null, 2),
            Investigations.ActionSort(Investigations.ActionState.OPEN, today.plusDays(2).toEpochDay(), 3),
            Investigations.ActionSort(Investigations.ActionState.OVERDUE, today.minusDays(1).toEpochDay(), 4),
        )
        assertEquals(listOf(3, 2, 1, 0), Investigations.actionOrder(rows))
    }

    @Test
    fun `causes are stored in a fixed order, and an unknown name is dropped`() {
        val causes = setOf(Investigations.Cause.TRAINING, Investigations.Cause.WAY_OF_WORKING)
        assertEquals("WAY_OF_WORKING,TRAINING", Investigations.encode(causes))
        assertEquals(causes, Investigations.decode("TRAINING, WAY_OF_WORKING,LUCK"))
        assertEquals(emptySet<Investigations.Cause>(), Investigations.decode(null))
        assertEquals(emptySet<Investigations.Cause>(), Investigations.decode(""))
    }
}
