package il.co.tradesmanager.core.safety

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExaminationsTest {

    private val today = LocalDate.of(2026, 10, 1)

    @Test
    fun `a certificate names its examiner, is not from the future, and a failure says why`() {
        val passed = Examinations.Result.PASSED
        assertEquals(Examinations.Refusal.NO_EXAMINER, Examinations.refusal(" ", today, null, passed, "", today))
        assertEquals(Examinations.Refusal.EXAMINED_IN_FUTURE, Examinations.refusal("Avi Mizrahi", today.plusDays(1), null, passed, "", today))
        assertEquals(
            Examinations.Refusal.DUE_BEFORE_EXAMINED,
            Examinations.refusal("Avi Mizrahi", today, today.minusDays(1), passed, "", today),
        )
        assertEquals(
            Examinations.Refusal.FAILED_WITHOUT_REASON,
            Examinations.refusal("Avi Mizrahi", today, null, Examinations.Result.FAILED, " ", today),
        )
        assertNull(Examinations.refusal("Avi Mizrahi", today.minusDays(3), today.plusMonths(12), passed, "", today))
        assertNull(Examinations.refusal("Avi Mizrahi", today, null, Examinations.Result.FAILED, "Worn hoist rope", today))
    }

    @Test
    fun `a certificate is current through its due date, warned on thirty days before, and overdue the day after`() {
        val passed = Examinations.Result.PASSED
        assertEquals(Examinations.State.CURRENT, Examinations.state(passed, today.plusDays(31), today))
        assertEquals(Examinations.State.DUE_SOON, Examinations.state(passed, today.plusDays(30), today))
        assertEquals("due today is due, not overdue", Examinations.State.DUE_SOON, Examinations.state(passed, today, today))
        assertEquals(Examinations.State.OVERDUE, Examinations.state(passed, today.minusDays(1), today))
        assertEquals(Examinations.State.NO_NEXT_DATE, Examinations.state(passed, null, today))
    }

    @Test
    fun `a failure outranks any date, and a machine never examined is none rather than overdue`() {
        assertEquals(Examinations.State.FAILED, Examinations.state(Examinations.Result.FAILED, today.plusYears(1), today))
        assertEquals(Examinations.State.NONE, Examinations.state(null, null, today))
        assertTrue(Examinations.needsAttention(Examinations.State.FAILED))
        assertTrue(Examinations.needsAttention(Examinations.State.OVERDUE))
        assertFalse(Examinations.needsAttention(Examinations.State.NONE))
        assertFalse(Examinations.needsAttention(Examinations.State.DUE_SOON))
    }

    @Test
    fun `a stored result this version does not know is none`() {
        assertEquals(Examinations.Result.FAILED, Examinations.resultOf("FAILED"))
        assertNull(Examinations.resultOf("CONDITIONAL"))
        assertNull(Examinations.resultOf(null))
    }
}
