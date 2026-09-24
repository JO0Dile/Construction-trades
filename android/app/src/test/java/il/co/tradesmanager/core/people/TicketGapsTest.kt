package il.co.tradesmanager.core.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketGapsTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_750_000_000_000L
    private val titles = mapOf(
        "electrician" to listOf("Licensed electrician", "חשמלאי מוסמך", "كهربائي مرخّص"),
        "height" to listOf("Work at height", "עבודה בגובה", "العمل على ارتفاع"),
    )

    private fun gaps(vararg held: TicketGaps.Held, usual: List<String> = listOf("electrician", "height")) =
        TicketGaps.gapsFor(usual, titles, held.toList(), now)

    @Test
    fun `a ticket in any of the three languages counts`() {
        assertTrue(gaps(TicketGaps.Held("חשמלאי מוסמך", null), TicketGaps.Held("work at height", now + day)).isEmpty())
    }

    @Test
    fun `nothing on record, and only expired on record, are both gaps`() {
        val result = gaps(TicketGaps.Held("Licensed electrician", now - day))
        assertEquals(
            listOf(
                TicketGaps.Gap("electrician", TicketGaps.State.EXPIRED),
                TicketGaps.Gap("height", TicketGaps.State.MISSING),
            ),
            result,
        )
    }

    @Test
    fun `one ticket in date is enough, however many have lapsed`() {
        assertTrue(
            gaps(
                TicketGaps.Held("Licensed electrician", now - 400 * day),
                TicketGaps.Held("Licensed electrician", now + 400 * day),
                usual = listOf("electrician"),
            ).isEmpty(),
        )
    }

    @Test
    fun `a ticket typed under other wording is not taken as one`() {
        assertEquals(
            listOf(TicketGaps.Gap("electrician", TicketGaps.State.MISSING)),
            gaps(TicketGaps.Held("Sparky", null), usual = listOf("electrician")),
        )
    }

    @Test
    fun `a trade the catalogue says nothing about has no gaps, and nor does a kind with no names`() {
        assertTrue(gaps(usual = emptyList()).isEmpty())
        assertTrue(gaps(usual = listOf("unknown-kind")).isEmpty())
    }
}
