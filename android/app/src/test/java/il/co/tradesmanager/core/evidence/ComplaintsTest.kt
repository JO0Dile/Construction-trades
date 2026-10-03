package il.co.tradesmanager.core.evidence

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComplaintsTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val sunday = LocalDate.of(2026, 10, 4)

    @Test
    fun `a complaint says who made it and what it was, and was not made in the future`() {
        val now = at(sunday, 12)
        assertEquals(Complaints.Refusal.BLANK_FROM, Complaints.receiveRefusal(" ", "Noise at six", now, now))
        assertEquals(Complaints.Refusal.BLANK_DESCRIPTION, Complaints.receiveRefusal("Neighbour", "", now, now))
        assertEquals(Complaints.Refusal.RECEIVED_IN_FUTURE, Complaints.receiveRefusal("Neighbour", "Noise", now + 1, now))
        assertNull(Complaints.receiveRefusal("Neighbour", "Noise", now - 3_600_000L, now))
    }

    @Test
    fun `an answer is written once and says something`() {
        assertEquals(Complaints.Refusal.BLANK_RESPONSE, Complaints.answerRefusal(" ", null))
        assertEquals(Complaints.Refusal.ALREADY_ANSWERED, Complaints.answerRefusal("Pump moved", 1L))
        assertNull(Complaints.answerRefusal("Pump moved to the far side", null))
    }

    @Test
    fun `unanswered for a week is waiting long from the eighth day, on the site's calendar`() {
        val received = at(sunday, 22)
        assertEquals(Complaints.State.OPEN, Complaints.state(received, null, at(sunday.plusDays(7), 23), zone))
        assertEquals(Complaints.State.WAITING_LONG, Complaints.state(received, null, at(sunday.plusDays(8), 1), zone))
        assertEquals(Complaints.State.ANSWERED, Complaints.state(received, 5L, at(sunday.plusDays(30), 9), zone))
    }

    @Test
    fun `waiting longest first, then open, then answered, oldest first within each`() {
        val rows = listOf(
            Complaints.State.ANSWERED to 1L,
            Complaints.State.OPEN to 9L,
            Complaints.State.WAITING_LONG to 5L,
            Complaints.State.OPEN to 3L,
        )
        assertEquals(listOf(2, 3, 1, 0), Complaints.order(rows))
    }

    @Test
    fun `numbered per job, and stored words this version does not know are read safely`() {
        assertEquals("CP-001", Complaints.reference(0))
        assertEquals(Complaints.Subject.DUST, Complaints.subjectOf("DUST"))
        assertEquals(Complaints.Subject.OTHER, Complaints.subjectOf("VIBRATION"))
        assertEquals(Complaints.Channel.IN_PERSON, Complaints.channelOf("CARRIER_PIGEON"))
    }
}
