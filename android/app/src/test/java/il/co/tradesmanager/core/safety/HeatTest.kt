package il.co.tradesmanager.core.safety

import il.co.tradesmanager.core.safety.Heat.Band
import il.co.tradesmanager.core.safety.Heat.Measure
import il.co.tradesmanager.core.safety.Heat.Outcome
import il.co.tradesmanager.core.safety.Heat.Refusal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatTest {

    private fun accepted(outcome: Outcome): Heat.Reading {
        assertTrue("expected a reading, got $outcome", outcome is Outcome.Accepted)
        return (outcome as Outcome.Accepted).reading
    }

    private fun refused(outcome: Outcome): Refusal {
        assertTrue("expected a refusal, got $outcome", outcome is Outcome.Refused)
        return (outcome as Outcome.Refused).reason
    }

    @Test
    fun `the index matches the published NWS chart`() {
        // Points read off the National Weather Service heat index chart, in
        // Fahrenheit there and converted here. The chart is itself rounded to
        // whole degrees Fahrenheit, which is where the tolerance comes from.
        val chart = listOf(
            Triple(90.0, 50.0, 95.0),
            Triple(100.0, 40.0, 109.0),
            Triple(86.0, 90.0, 105.0),
            Triple(96.0, 60.0, 116.0),
            Triple(110.0, 40.0, 136.0),
        )
        chart.forEach { (tempF, humidity, indexF) ->
            assertEquals(
                "$tempF F at $humidity%",
                Heat.fahrenheitToCelsius(indexF),
                Heat.heatIndexCelsius(Heat.fahrenheitToCelsius(tempF), humidity),
                0.3,
            )
        }
    }

    @Test
    fun `a humid coast is more dangerous than a dry desert that is hotter`() {
        val coast = accepted(Heat.record(temperatureC = 32.0, humidityPercent = 70.0, inSun = false))
        val desert = accepted(Heat.record(temperatureC = 40.0, humidityPercent = 20.0, inSun = false))

        assertEquals(Band.DANGER, coast.band)
        assertEquals(Band.EXTREME_CAUTION, desert.band)
        assertTrue(coast.heatIndexC > desert.heatIndexC)
    }

    @Test
    fun `a mild day is nothing to act on`() {
        assertEquals(Band.NONE, accepted(Heat.record(25.0, 50.0, inSun = false)).band)
    }

    @Test
    fun `full sun is judged on the worst case, and the shade figure is kept`() {
        val shade = accepted(Heat.record(33.0, 40.0, inSun = false))
        val sun = accepted(Heat.record(33.0, 40.0, inSun = true))

        assertEquals(Band.EXTREME_CAUTION, shade.band)
        assertEquals(Band.DANGER, sun.band)
        assertEquals("the index itself does not change", shade.heatIndexC, sun.heatIndexC, 1e-9)
        assertEquals(shade.heatIndexC + Heat.SUN_ALLOWANCE_C, sun.judgedOnC, 1e-9)
        assertEquals(15.0 * 5.0 / 9.0, Heat.SUN_ALLOWANCE_C, 1e-12)
    }

    @Test
    fun `each band starts exactly at the NWS threshold`() {
        val edges = mapOf(
            Band.CAUTION to 80.0,
            Band.EXTREME_CAUTION to 90.0,
            Band.DANGER to 103.0,
            Band.EXTREME_DANGER to 125.0,
        )
        edges.forEach { (band, fahrenheit) ->
            val edge = Heat.fahrenheitToCelsius(fahrenheit)
            assertEquals(edge, band.fromCelsius, 1e-9)
            assertEquals(band, Heat.bandOf(edge))
            assertNotEquals("just below $band is not $band", band, Heat.bandOf(edge - 1e-6))
        }
    }

    @Test
    fun `the regression is extrapolated rather than capped at the top of the chart`() {
        assertEquals(Band.EXTREME_DANGER, accepted(Heat.record(45.0, 50.0, inSun = false)).band)
    }

    @Test
    fun `numbers that cannot be weather are refused`() {
        assertEquals(Refusal.IMPLAUSIBLE_TEMPERATURE, refused(Heat.record(61.0, 50.0, false)))
        assertEquals(Refusal.IMPLAUSIBLE_TEMPERATURE, refused(Heat.record(-31.0, 50.0, false)))
        assertEquals(Refusal.IMPLAUSIBLE_TEMPERATURE, refused(Heat.record(Double.NaN, 50.0, false)))
        assertEquals(Refusal.IMPLAUSIBLE_HUMIDITY, refused(Heat.record(30.0, 101.0, false)))
        assertEquals(Refusal.IMPLAUSIBLE_HUMIDITY, refused(Heat.record(30.0, -1.0, false)))
        assertEquals(Refusal.IMPLAUSIBLE_HUMIDITY, refused(Heat.record(30.0, Double.NaN, false)))

        // The edges themselves are weather.
        accepted(Heat.record(60.0, 0.0, false))
        accepted(Heat.record(-30.0, 100.0, false))
    }

    @Test
    fun `each band keeps the measures below it and adds its own`() {
        assertEquals(emptyList<Measure>(), Heat.recommended(Band.NONE))
        assertEquals(listOf(Measure.WATER, Measure.SHADE), Heat.recommended(Band.CAUTION))
        assertTrue(
            Heat.recommended(Band.EXTREME_DANGER).containsAll(Heat.recommended(Band.DANGER)),
        )
        assertTrue(Measure.HEAVY_WORK_STOPPED in Heat.recommended(Band.EXTREME_DANGER))
        assertTrue(Measure.HEAVY_WORK_STOPPED !in Heat.recommended(Band.DANGER))
    }

    @Test
    fun `a check with nothing done is recorded, and says what is missing`() {
        val danger = accepted(Heat.record(32.0, 70.0, inSun = false))

        assertEquals(
            listOf(
                Measure.SCHEDULED_BREAKS,
                Measure.BUDDY_SYSTEM,
                Measure.NEW_WORKERS_EASED_IN,
                Measure.HEAVY_WORK_MOVED,
            ),
            danger.missing(listOf(Measure.WATER, Measure.SHADE)),
        )
        assertEquals(Heat.recommended(Band.DANGER), danger.missing(emptyList()))
    }

    @Test
    fun `bands are ordered from least to most dangerous`() {
        assertTrue(Band.DANGER.atLeast(Band.EXTREME_CAUTION))
        assertTrue(Band.DANGER.atLeast(Band.DANGER))
        assertTrue(!Band.CAUTION.atLeast(Band.DANGER))
    }
}
