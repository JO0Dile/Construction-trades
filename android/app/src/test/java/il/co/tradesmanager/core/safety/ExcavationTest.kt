package il.co.tradesmanager.core.safety

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two things kill people in trenches: the sides, and what was already in the
 * ground. Both gates are pinned here.
 */
class ExcavationTest {

    private val israel: ZoneId = ZoneId.of("Asia/Jerusalem")

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(israel).toInstant().toEpochMilli()

    private fun blockers(
        servicesLocatedAt: Long? = at("2026-06-01T08:00"),
        depthMetres: Double? = 2.0,
        support: Excavation.Support = Excavation.Support.SHORED,
        lastInspectedAt: Long? = at("2026-06-10T06:30"),
        lastInspectionPassed: Boolean = true,
        disturbedAt: Long? = null,
        backfilledAt: Long? = null,
        now: Long = at("2026-06-10T09:00"),
    ) = Excavation.blockers(
        servicesLocatedAt = servicesLocatedAt,
        depthMetres = depthMetres,
        support = support,
        lastInspectedAt = lastInspectedAt,
        lastInspectionPassed = lastInspectionPassed,
        disturbedAt = disturbedAt,
        backfilledAt = backfilledAt,
        now = now,
        zone = israel,
    )

    @Test
    fun `services located, sides held, inspected this morning`() {
        assertEquals(emptySet<Excavation.Blocker>(), blockers())
        assertTrue(Excavation.mayEnter(blockers()))
    }

    @Test
    fun `nobody goes in until somebody has found out what is buried there`() {
        // The decision that cannot be undone once the spade is in.
        assertTrue(
            blockers(servicesLocatedAt = null)
                .contains(Excavation.Blocker.SERVICES_NOT_LOCATED),
        )
    }

    @Test
    fun `at one metre twenty the sides have to be held back somehow`() {
        assertFalse(Excavation.needsSupport(1.19))
        assertTrue(Excavation.needsSupport(1.2))
        assertTrue(Excavation.needsSupport(3.0))
        // Not measured is not a blocker of its own: a trench nobody has
        // measured is usually one nobody has dug.
        assertFalse(Excavation.needsSupport(null))
    }

    @Test
    fun `a deep trench with nothing holding the sides is refused`() {
        val found = blockers(depthMetres = 2.5, support = Excavation.Support.NONE)
        assertTrue(found.contains(Excavation.Blocker.NO_SUPPORT))
        assertFalse(Excavation.mayEnter(found))
    }

    @Test
    fun `a shallow one needs no support recorded`() {
        val found = blockers(depthMetres = 0.8, support = Excavation.Support.NONE)
        assertFalse(found.contains(Excavation.Blocker.NO_SUPPORT))
        assertTrue(Excavation.mayEnter(found))
    }

    @Test
    fun `battering, shoring and piling all hold the sides back, nothing does not`() {
        assertFalse(Excavation.Support.NONE.holdsTheSidesBack)
        assertTrue(Excavation.Support.BATTERED.holdsTheSidesBack)
        assertTrue(Excavation.Support.SHORED.holdsTheSidesBack)
        assertTrue(Excavation.Support.PILED.holdsTheSidesBack)
    }

    @Test
    fun `yesterday's inspection does not cover this morning's shift`() {
        // Inspected at seven last night; the shift starts at six today.
        val found = blockers(
            lastInspectedAt = at("2026-06-09T19:00"),
            now = at("2026-06-10T06:00"),
        )
        assertTrue(found.contains(Excavation.Blocker.NOT_INSPECTED_TODAY))
        assertFalse(Excavation.mayEnter(found))
    }

    @Test
    fun `an inspection later the same day still covers the evening`() {
        assertTrue(
            Excavation.inspectionCoversNow(
                at("2026-06-10T06:30"),
                at("2026-06-10T22:00"),
                israel,
            ),
        )
    }

    @Test
    fun `never inspected is its own answer, not merely out of date`() {
        val found = blockers(lastInspectedAt = null)
        assertTrue(found.contains(Excavation.Blocker.NEVER_INSPECTED))
        assertFalse(found.contains(Excavation.Blocker.NOT_INSPECTED_TODAY))
    }

    @Test
    fun `a failed inspection is not redeemed by being today's`() {
        val found = blockers(lastInspectionPassed = false)
        assertTrue(found.contains(Excavation.Blocker.LAST_INSPECTION_FAILED))
        assertFalse(Excavation.mayEnter(found))
    }

    @Test
    fun `rain since the inspection voids it`() {
        val found = blockers(
            lastInspectedAt = at("2026-06-10T06:30"),
            disturbedAt = at("2026-06-10T07:15"),
        )
        assertTrue(found.contains(Excavation.Blocker.DISTURBED))
    }

    @Test
    fun `a disturbance before the inspection is what the inspection was for`() {
        val found = blockers(
            disturbedAt = at("2026-06-10T05:00"),
            lastInspectedAt = at("2026-06-10T06:30"),
        )
        assertFalse(found.contains(Excavation.Blocker.DISTURBED))
        assertTrue(Excavation.mayEnter(found))
    }

    @Test
    fun `a backfilled trench is nobody's problem and nobody's entry`() {
        val found = blockers(backfilledAt = at("2026-06-11T10:00"))
        assertTrue(found.contains(Excavation.Blocker.BACKFILLED))
        assertFalse(Excavation.mayEnter(found))
    }

    @Test
    fun `everything wrong is reported at once`() {
        val found = blockers(
            servicesLocatedAt = null,
            depthMetres = 3.0,
            support = Excavation.Support.NONE,
            lastInspectedAt = null,
        )
        assertTrue(
            found.containsAll(
                setOf(
                    Excavation.Blocker.SERVICES_NOT_LOCATED,
                    Excavation.Blocker.NO_SUPPORT,
                    Excavation.Blocker.NEVER_INSPECTED,
                ),
            ),
        )
    }

    @Test
    fun `the worst trench sorts above the merely stale one, and filled ones sink`() {
        val worst = Excavation.urgency(
            blockers(servicesLocatedAt = null, support = Excavation.Support.NONE),
        )
        val stale = Excavation.urgency(blockers(lastInspectedAt = at("2026-06-09T07:00")))
        val fine = Excavation.urgency(blockers())
        val filled = Excavation.urgency(blockers(backfilledAt = at("2026-06-11T10:00")))
        assertTrue(worst < stale)
        assertTrue(stale < fine)
        assertTrue(fine < filled)
    }
}
