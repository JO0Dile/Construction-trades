package il.co.tradesmanager.core.evidence

import il.co.tradesmanager.core.evidence.Waste.Destination
import il.co.tradesmanager.core.evidence.Waste.Load
import il.co.tradesmanager.core.evidence.Waste.Measurement
import il.co.tradesmanager.core.evidence.Waste.Refusal
import il.co.tradesmanager.core.evidence.Waste.Stream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WasteTest {

    private fun load(
        quantity: Double,
        unit: Measurement,
        destination: Destination,
        ticket: String? = null,
        photos: Int = 0,
        stream: Stream = Stream.CONCRETE_AND_MASONRY,
    ) = Load(stream, quantity, unit, destination, "Facility", ticket, photos)

    @Test
    fun `a load needs a quantity and somewhere it went`() {
        assertEquals(Refusal.NO_QUANTITY, Waste.refusal(Stream.WOOD, 0.0, "Hiriya", null))
        assertEquals(Refusal.NO_QUANTITY, Waste.refusal(Stream.WOOD, -1.0, "Hiriya", null))
        assertEquals(Refusal.NO_QUANTITY, Waste.refusal(Stream.WOOD, Double.NaN, "Hiriya", null))
        assertEquals(Refusal.NO_DESTINATION, Waste.refusal(Stream.WOOD, 2.0, "  ", null))
        assertNull(Waste.refusal(Stream.MIXED, 6.0, "Transfer station", null))
    }

    @Test
    fun `hazardous waste is never recorded without its ticket`() {
        assertEquals(
            Refusal.HAZARDOUS_WITHOUT_TICKET,
            Waste.refusal(Stream.HAZARDOUS, 0.5, "Ramat Hovav", " "),
        )
        assertNull(Waste.refusal(Stream.HAZARDOUS, 0.5, "Ramat Hovav", "RH-1234"))
    }

    @Test
    fun `a ticket number or a photo of the ticket proves a load`() {
        assertTrue(load(1.0, Measurement.TONNES, Destination.LANDFILL, ticket = "A1").proven)
        assertTrue(load(1.0, Measurement.TONNES, Destination.LANDFILL, photos = 1).proven)
        assertFalse(load(1.0, Measurement.TONNES, Destination.LANDFILL, ticket = " ").proven)
        assertFalse(load(1.0, Measurement.TONNES, Destination.LANDFILL).proven)
    }

    @Test
    fun `totals are kept per unit and never added across units`() {
        val loads = listOf(
            load(12.0, Measurement.TONNES, Destination.RECYCLING, ticket = "A1"),
            load(3.0, Measurement.TONNES, Destination.LANDFILL, photos = 1),
            load(5.0, Measurement.TONNES, Destination.TRANSFER_STATION),
            load(8.0, Measurement.CUBIC_METRES, Destination.REUSED, ticket = " "),
        )

        val totals = Waste.totals(loads)

        assertEquals(listOf(Measurement.TONNES, Measurement.CUBIC_METRES), totals.map { it.unit })
        val tonnes = totals[0]
        assertEquals(20.0, tonnes.total, 0.0)
        assertEquals("a transfer station is not counted as kept out", 12.0, tonnes.keptOutOfLandfill, 0.0)
        assertEquals(0.6, tonnes.diversionRate!!, 1e-12)
        assertEquals(1, tonnes.unproven)
        assertEquals(1.0, totals[1].diversionRate!!, 1e-12)
        assertEquals(2, Waste.unproven(loads))
    }

    @Test
    fun `no loads is no totals, not a diversion rate of nothing`() {
        assertEquals(emptyList<Waste.Totals>(), Waste.totals(emptyList()))
        assertNull(Waste.Totals(Measurement.TONNES, 0.0, 0.0, 0).diversionRate)
    }
}
