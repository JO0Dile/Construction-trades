package il.co.tradesmanager.core.people

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A lapsed ticket is a legal exposure, so the boundaries matter more than they
 * look: one day either side of the warning window is the difference between
 * someone booking a renewal course and someone being sent home from a site.
 */
class ExpiryTest {

    private val now = 1_760_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    @Test
    fun `a ticket with no expiry date is never chased`() {
        assertEquals(Expiry.State.NO_EXPIRY, Expiry.state(null, now))
    }

    @Test
    fun `the warning window opens exactly thirty days out`() {
        assertEquals(Expiry.State.VALID, Expiry.state(now + 31 * day, now))
        assertEquals(Expiry.State.EXPIRING_SOON, Expiry.state(now + 30 * day, now))
        assertEquals(Expiry.State.EXPIRING_SOON, Expiry.state(now + day, now))
    }

    @Test
    fun `the last day is still valid and the next is not`() {
        assertEquals(Expiry.State.EXPIRING_SOON, Expiry.state(now, now))
        assertEquals(Expiry.State.EXPIRED, Expiry.state(now - 1, now))
        assertEquals(Expiry.State.EXPIRED, Expiry.state(now - 40 * day, now))
    }

    @Test
    fun `days are floored, so almost-a-day is not a day`() {
        // Rounding up here would let someone plan a day they do not have.
        assertEquals(0L, Expiry.daysUntil(now + 23 * 60 * 60 * 1000L, now))
        assertEquals(1L, Expiry.daysUntil(now + day, now))
        assertEquals(-1L, Expiry.daysUntil(now - 1, now))
    }

    @Test
    fun `urgency sorts expired first and never-expiring last`() {
        val order = listOf(
            Expiry.urgency(now - 5 * day, now),
            Expiry.urgency(now + 3 * day, now),
            Expiry.urgency(now + 300 * day, now),
            Expiry.urgency(null, now),
        )
        assertEquals(order, order.sorted())
    }
}

/** The date field on the ticket dialog. A wrong date here is a wrong warning. */
class TypedDateTest {

    @Test
    fun `a normal Israeli date reads back`() {
        val date = il.co.tradesmanager.core.i18n.Formats.parseDate("07/03/2027")
        assertEquals(java.time.LocalDate.of(2027, 3, 7), date)
    }

    @Test
    fun `an impossible date is refused rather than shifted`() {
        // Lenient parsing turns 31/02 into 3 March and stores a date nobody
        // typed. Better to say it is not a date.
        assertEquals(null, il.co.tradesmanager.core.i18n.Formats.parseDate("31/02/2027"))
        assertEquals(null, il.co.tradesmanager.core.i18n.Formats.parseDate("07-03-2027"))
        assertEquals(null, il.co.tradesmanager.core.i18n.Formats.parseDate(""))
        assertEquals(null, il.co.tradesmanager.core.i18n.Formats.parseDate("soon"))
    }

    @Test
    fun `a leap day is a date`() {
        assertEquals(
            java.time.LocalDate.of(2028, 2, 29),
            il.co.tradesmanager.core.i18n.Formats.parseDate("29/02/2028"),
        )
        assertEquals(null, il.co.tradesmanager.core.i18n.Formats.parseDate("29/02/2027"))
    }
}
