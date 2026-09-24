package il.co.tradesmanager.core.work

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueriesTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val thursday = LocalDate.of(2026, 10, 1)

    @Test
    fun `a question needs words and somebody to ask`() {
        val now = at(thursday, 9)
        assertEquals(Queries.Refusal.BLANK_QUESTION, Queries.raiseRefusal(" ", "Architect", now, null, zone))
        assertEquals(Queries.Refusal.NOBODY_ASKED, Queries.raiseRefusal("Which tile?", "", now, null, zone))
        assertNull(Queries.raiseRefusal("Which tile?", "Architect", now, null, zone))
    }

    @Test
    fun `needed by today is fine, needed by yesterday is not`() {
        val now = at(thursday, 15)
        assertNull(Queries.raiseRefusal("Q", "Engineer", now, at(thursday, 0), zone))
        assertEquals(
            Queries.Refusal.NEEDED_BY_TOO_EARLY,
            Queries.raiseRefusal("Q", "Engineer", now, at(thursday.minusDays(1), 0), zone),
        )
    }

    @Test
    fun `overdue from the day after, on the site's own clock`() {
        val neededBy = at(thursday, 0)
        assertEquals("Thursday evening is still Thursday", Queries.State.OPEN, Queries.state(neededBy, null, at(thursday, 23), zone))
        assertEquals(Queries.State.OVERDUE, Queries.state(neededBy, null, at(thursday.plusDays(1), 0) + 1, zone))
        assertEquals(Queries.State.ANSWERED, Queries.state(neededBy, answeredAt = 1L, now = at(thursday.plusDays(9), 9), zone))
        assertEquals("no date is never overdue", Queries.State.OPEN, Queries.state(null, null, at(thursday.plusDays(99), 9), zone))
    }

    @Test
    fun `an answer goes in once`() {
        assertEquals(Queries.Refusal.BLANK_ANSWER, Queries.answerRefusal(" ", null))
        assertEquals(Queries.Refusal.ALREADY_ANSWERED, Queries.answerRefusal("Use the sample", 5L))
        assertNull(Queries.answerRefusal("Use the sample", null))
    }

    @Test
    fun `overdue first, then open, then answered, oldest first within each`() {
        val order = Queries.order(
            listOf(
                Queries.State.ANSWERED to 1L,
                Queries.State.OPEN to 5L,
                Queries.State.OVERDUE to 9L,
                Queries.State.OPEN to 2L,
            ),
        )
        assertEquals(listOf(2, 3, 1, 0), order)
    }

    @Test
    fun `numbered per job, padded`() {
        assertEquals("Q-001", Queries.reference(0))
        assertEquals("Q-042", Queries.reference(41))
        assertEquals("Q-1000", Queries.reference(999))
    }
}
