package il.co.tradesmanager.core.safety

import il.co.tradesmanager.core.safety.FirePoints.Refusal
import il.co.tradesmanager.core.safety.FirePoints.State
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirePointsTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private val today = LocalDate.of(2026, 10, 8)
    private val now = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
    private fun daysAgo(days: Long): Long = today.minusDays(days).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    private fun state(serviceDueOn: LocalDate? = today.plusMonths(6), lastCheckAt: Long? = daysAgo(3), ok: Boolean? = true, removed: Boolean = false) =
        FirePoints.state(serviceDueOn, lastCheckAt, ok, removed, now, zone)

    @Test
    fun `a point needs a place, a label date ahead, and a fault needs saying what`() {
        assertEquals(Refusal.BLANK_LOCATION, FirePoints.addRefusal(" ", null, today))
        assertEquals(Refusal.SERVICE_DUE_IN_PAST, FirePoints.addRefusal("Stair 2", today.minusDays(1), today))
        assertNull("due today is not yet past", FirePoints.addRefusal("Stair 2", today, today))
        assertNull(FirePoints.addRefusal("Stair 2", null, today))
        assertEquals(Refusal.FAULT_NEEDS_NOTE, FirePoints.checkRefusal(ok = false, note = " ", removed = false))
        assertNull(FirePoints.checkRefusal(ok = true, note = "", removed = false))
        assertEquals(Refusal.ALREADY_REMOVED, FirePoints.checkRefusal(ok = true, note = "", removed = true))
        assertEquals(Refusal.SERVICE_DUE_IN_PAST, FirePoints.serviceRefusal(today, removed = false, today = today))
        assertNull(FirePoints.serviceRefusal(today.plusYears(1), removed = false, today = today))
        assertEquals(Refusal.ALREADY_REMOVED, FirePoints.serviceRefusal(today.plusYears(1), removed = true, today = today))
    }

    @Test
    fun `a fault outranks everything, then a service passed, then a look overdue`() {
        assertEquals(State.FAULT, state(serviceDueOn = today.minusDays(5), ok = false))
        assertEquals(State.SERVICE_OVERDUE, state(serviceDueOn = today.minusDays(1)))
        assertEquals(State.CHECK_OVERDUE, state(lastCheckAt = null, ok = null))
        assertEquals("a month to the day is not late", State.READY, state(lastCheckAt = daysAgo(31)))
        assertEquals(State.CHECK_OVERDUE, state(lastCheckAt = daysAgo(32)))
        assertEquals(State.SERVICE_DUE_SOON, state(serviceDueOn = today.plusDays(30)))
        assertEquals(State.READY, state(serviceDueOn = today.plusDays(31)))
        assertEquals("no label date is not a reason to shout", State.READY, state(serviceDueOn = null))
        assertEquals(State.REMOVED, state(ok = false, removed = true))
    }

    @Test
    fun `only the states somebody has to act on count for attention`() {
        assertTrue(FirePoints.needsAttention(State.FAULT))
        assertTrue(FirePoints.needsAttention(State.SERVICE_OVERDUE))
        assertTrue(FirePoints.needsAttention(State.CHECK_OVERDUE))
        assertFalse(FirePoints.needsAttention(State.SERVICE_DUE_SOON))
        assertFalse(FirePoints.needsAttention(State.READY))
        assertFalse(FirePoints.needsAttention(State.REMOVED))
    }

    @Test
    fun `ordered by state and then by number, and numbered per job`() {
        val rows = listOf(State.READY to "FP-001", State.FAULT to "FP-004", State.READY to "FP-002", State.CHECK_OVERDUE to "FP-003")
        assertEquals(listOf(1, 3, 0, 2), FirePoints.order(rows))
        assertEquals("FP-001", FirePoints.reference(0))
        assertEquals(FirePoints.Kind.CO2, FirePoints.kindOf("CO2"))
        assertEquals(FirePoints.Kind.POWDER, FirePoints.kindOf("SAND"))
    }
}
