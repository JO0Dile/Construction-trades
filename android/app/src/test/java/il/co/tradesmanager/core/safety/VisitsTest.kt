package il.co.tradesmanager.core.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitsTest {

    @Test
    fun `only a missing name is refused`() {
        assertEquals(Visits.Refusal.BLANK_NAME, Visits.refusal(" "))
        assertNull(Visits.refusal("Dana"))
    }

    @Test
    fun `a visit is signed out once`() {
        assertNull(Visits.signOutRefusal(null))
        assertEquals(Visits.Refusal.ALREADY_LEFT, Visits.signOutRefusal(5L))
    }

    @Test
    fun `forgotten after the same shift-and-a-bit a clocking gets`() {
        assertFalse(Visits.forgotten(arrivedAt = 0L, now = Muster.STALE_AFTER_MS))
        assertTrue(Visits.forgotten(arrivedAt = 0L, now = Muster.STALE_AFTER_MS + 1))
    }

    @Test
    fun `a visitor goes on the roll call marked as one, with no account to merge on`() {
        val roll = Muster.start(
            openCheckIns = listOf(
                Visits.onRollCall("Dana", arrivedAt = 10L, projectId = "job.1"),
                Visits.onRollCall("Dana", arrivedAt = 20L, projectId = "job.1"),
            ),
            startedAt = 100L,
            id = { "row.$it" },
        )
        assertEquals("two visitors of one name are two rows, not one", 2, roll.people.size)
        assertTrue(roll.people.all { it.visitor && it.personId == null })
    }
}
