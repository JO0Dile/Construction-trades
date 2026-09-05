package il.co.tradesmanager.core.safety

import il.co.tradesmanager.core.people.Expiry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Everything that stops a lift is known before the load leaves the ground and
 * forgotten while it is in the air, so it is pinned here.
 */
class LiftingTest {

    private val now = 1_760_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    private val fullCrew = Lifting.Crew(
        operator = Expiry.State.VALID,
        slinger = Expiry.State.VALID,
        banksman = Expiry.State.NO_EXPIRY,
    )

    private fun blockers(
        loadWeightKg: Double? = 4_000.0,
        riggingWeightKg: Double? = 200.0,
        capacityAtRadiusKg: Double? = 6_000.0,
        crew: Lifting.Crew = fullCrew,
        applianceCertificateExpiresOn: Long? = now + 90 * day,
        applianceCertificateRequired: Boolean = true,
        windSpeedKmh: Double? = 20.0,
        windLimitKmh: Double = Lifting.DEFAULT_WIND_LIMIT_KMH,
        approvedAt: Long? = now - day,
    ) = Lifting.blockers(
        loadWeightKg = loadWeightKg,
        riggingWeightKg = riggingWeightKg,
        capacityAtRadiusKg = capacityAtRadiusKg,
        crew = crew,
        applianceCertificateExpiresOn = applianceCertificateExpiresOn,
        applianceCertificateRequired = applianceCertificateRequired,
        windSpeedKmh = windSpeedKmh,
        windLimitKmh = windLimitKmh,
        approvedAt = approvedAt,
        now = now,
    )

    @Test
    fun `a complete plan inside the chart may lift`() {
        val found = blockers()
        assertEquals(emptySet<Lifting.Blocker>(), found)
        assertTrue(Lifting.mayLift(found))
    }

    @Test
    fun `the rigging is on the hook too`() {
        assertEquals(4_200.0, Lifting.grossWeightKg(4_000.0, 200.0)!!, 0.0001)
        // No rigging recorded is treated as none, not as unknown: the load is
        // still the load, and refusing to compute anything would hide a lift
        // that is over the chart on its own.
        assertEquals(4_000.0, Lifting.grossWeightKg(4_000.0, null)!!, 0.0001)
        assertNull(Lifting.grossWeightKg(null, 200.0))
    }

    @Test
    fun `rigging weight is what tips a marginal lift over the chart`() {
        // 5,900 of load on a 6,000 chart looks like ninety-eight per cent and
        // passes. The slings and the beam are the other two hundred kilos.
        assertTrue(Lifting.mayLift(blockers(loadWeightKg = 5_900.0, riggingWeightKg = null)))
        assertTrue(
            blockers(loadWeightKg = 5_900.0, riggingWeightKg = 200.0)
                .contains(Lifting.Blocker.OVERLOADED),
        )
    }

    @Test
    fun `an unknown weight has no utilisation rather than a utilisation of zero`() {
        assertNull(Lifting.utilisation(null, 6_000.0))
        assertNull(Lifting.utilisation(4_000.0, null))
        assertNull(Lifting.utilisation(4_000.0, 0.0))
        assertFalse(Lifting.isHeavy(null))
    }

    @Test
    fun `three quarters of the chart is where a lift stops being routine`() {
        assertFalse(Lifting.isHeavy(Lifting.utilisation(4_400.0, 6_000.0)))
        assertTrue(Lifting.isHeavy(Lifting.utilisation(4_500.0, 6_000.0)))
        assertTrue(Lifting.isHeavy(Lifting.utilisation(6_000.0, 6_000.0)))
        // Over the chart is not "heavy". It is refused, and painting it the
        // same amber as a heavy lift would be the wrong answer.
        assertFalse(Lifting.isHeavy(Lifting.utilisation(6_001.0, 6_000.0)))
    }

    @Test
    fun `exactly on the chart is allowed and a kilo over is not`() {
        assertTrue(Lifting.mayLift(blockers(loadWeightKg = 5_800.0)))
        assertTrue(blockers(loadWeightKg = 5_801.0).contains(Lifting.Blocker.OVERLOADED))
    }

    @Test
    fun `every missing role is reported at once, not one at a time`() {
        val found = blockers(
            loadWeightKg = null,
            capacityAtRadiusKg = null,
            crew = Lifting.Crew(operator = null, slinger = null, banksman = null),
            approvedAt = null,
        )
        assertTrue(found.containsAll(
            setOf(
                Lifting.Blocker.NO_WEIGHT,
                Lifting.Blocker.NO_CAPACITY,
                Lifting.Blocker.NO_OPERATOR,
                Lifting.Blocker.NO_SLINGER,
                Lifting.Blocker.NO_BANKSMAN,
                Lifting.Blocker.NOT_APPROVED,
            ),
        ))
    }

    @Test
    fun `a lapsed ticket on any of the three stops the lift`() {
        val expiredSlinger = fullCrew.copy(slinger = Expiry.State.EXPIRED)
        assertTrue(blockers(crew = expiredSlinger).contains(Lifting.Blocker.TICKET_EXPIRED))
    }

    @Test
    fun `a ticket expiring next month does not stop today's lift`() {
        val soon = fullCrew.copy(banksman = Expiry.State.EXPIRING_SOON)
        assertTrue(Lifting.mayLift(blockers(crew = soon)))
    }

    @Test
    fun `a licence that never expires is a licence`() {
        val noExpiry = Lifting.Crew(
            operator = Expiry.State.NO_EXPIRY,
            slinger = Expiry.State.NO_EXPIRY,
            banksman = Expiry.State.NO_EXPIRY,
        )
        assertTrue(Lifting.mayLift(blockers(crew = noExpiry)))
    }

    @Test
    fun `a crane with no recorded certificate is not assumed to have one`() {
        // Unlike a person's trade licence, periodic testing of the appliance is
        // not optional, so a blank date is a missing certificate rather than
        // one that never runs out.
        assertTrue(
            blockers(applianceCertificateExpiresOn = null)
                .contains(Lifting.Blocker.APPLIANCE_UNCERTIFIED),
        )
        assertTrue(
            blockers(applianceCertificateExpiresOn = now - day)
                .contains(Lifting.Blocker.APPLIANCE_UNCERTIFIED),
        )
    }

    @Test
    fun `an appliance that needs no certificate is not blocked for lacking one`() {
        assertTrue(
            Lifting.mayLift(
                blockers(
                    applianceCertificateExpiresOn = null,
                    applianceCertificateRequired = false,
                ),
            ),
        )
    }

    @Test
    fun `wind over the limit stops it and wind nobody measured does not`() {
        assertTrue(blockers(windSpeedKmh = 80.0).contains(Lifting.Blocker.TOO_WINDY))
        assertFalse(blockers(windSpeedKmh = 72.0).contains(Lifting.Blocker.TOO_WINDY))
        // The app has no anemometer. Treating "not measured" as a blocker would
        // train everybody to type a number in to get past it.
        assertTrue(Lifting.mayLift(blockers(windSpeedKmh = null)))
    }

    @Test
    fun `an appliance with a lower limit than a tower crane uses its own`() {
        assertTrue(
            blockers(windSpeedKmh = 40.0, windLimitKmh = 35.0)
                .contains(Lifting.Blocker.TOO_WINDY),
        )
    }

    @Test
    fun `a plan nobody signed is not a plan`() {
        assertTrue(blockers(approvedAt = null).contains(Lifting.Blocker.NOT_APPROVED))
    }
}
