package il.co.tradesmanager.core.work

import il.co.tradesmanager.core.work.Meetings.ActionSort
import il.co.tradesmanager.core.work.Meetings.ActionState
import il.co.tradesmanager.core.work.Meetings.Refusal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeetingsTest {

    private val today = LocalDate.of(2026, 10, 8)

    @Test
    fun `minutes come after the meeting, and a point is due after it too`() {
        assertEquals(Refusal.HELD_IN_FUTURE, Meetings.meetingRefusal(today.plusDays(1), today))
        assertNull(Meetings.meetingRefusal(today, today))
        assertEquals(Refusal.BLANK_ACTION, Meetings.actionRefusal(" ", null, today))
        assertEquals(Refusal.DUE_BEFORE_MEETING, Meetings.actionRefusal("Send the drawing", today.minusDays(1), today))
        assertNull("due the same day is allowed", Meetings.actionRefusal("Send the drawing", today, today))
        assertNull("no date is allowed", Meetings.actionRefusal("Send the drawing", null, today))
    }

    @Test
    fun `a point is closed once, with what was done`() {
        assertEquals(Refusal.BLANK_CLOSING_NOTE, Meetings.closeRefusal(" ", closed = false))
        assertEquals(Refusal.ALREADY_CLOSED, Meetings.closeRefusal("Sent", closed = true))
        assertNull(Meetings.closeRefusal("Sent on the 9th", closed = false))
    }

    @Test
    fun `overdue from the day after its date, and done is done whatever the date`() {
        assertEquals(ActionState.OPEN, Meetings.actionState(today, closed = false, today = today))
        assertEquals(ActionState.OVERDUE, Meetings.actionState(today.minusDays(1), closed = false, today = today))
        assertEquals(ActionState.OPEN, Meetings.actionState(null, closed = false, today = today))
        assertEquals(ActionState.DONE, Meetings.actionState(today.minusDays(30), closed = true, today = today))
    }

    @Test
    fun `overdue first, then open by date with no date last, then the most recently closed`() {
        val rows = listOf(
            ActionSort(ActionState.DONE, today.toEpochDay(), closedAt = 100L, reference = "MT-001/1"),
            ActionSort(ActionState.OPEN, null, null, "MT-001/2"),
            ActionSort(ActionState.OPEN, today.plusDays(2).toEpochDay(), null, "MT-001/3"),
            ActionSort(ActionState.OVERDUE, today.minusDays(1).toEpochDay(), null, "MT-001/4"),
            ActionSort(ActionState.DONE, today.plusDays(9).toEpochDay(), closedAt = 200L, reference = "MT-001/5"),
            ActionSort(ActionState.OPEN, today.plusDays(1).toEpochDay(), null, "MT-001/6"),
        )
        assertEquals(listOf(3, 5, 2, 1, 4, 0), Meetings.actionOrder(rows))
    }

    @Test
    fun `meetings are numbered per job and points under their meeting`() {
        assertEquals("MT-001", Meetings.reference(0))
        assertEquals("MT-004/2", Meetings.actionReference("MT-004", 2))
        assertEquals(Meetings.Kind.SAFETY, Meetings.kindOf("SAFETY"))
        assertEquals(Meetings.Kind.OTHER, Meetings.kindOf("BARBECUE"))
    }
}
