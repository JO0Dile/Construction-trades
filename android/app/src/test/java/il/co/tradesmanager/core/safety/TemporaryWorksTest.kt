package il.co.tradesmanager.core.safety

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Temporary works fail at the moment somebody loads them and the moment
 * somebody takes them away. Both gates are pinned here.
 */
class TemporaryWorksTest {

    private val israel: ZoneId = ZoneId.of("Asia/Jerusalem")

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(israel).toInstant().toEpochMilli()

    private fun loadBlockers(
        designReference: String? = "TW-DRG-14",
        category: TemporaryWorks.CheckCategory = TemporaryWorks.CheckCategory.TWO,
        checkedAt: Long? = at("2026-06-01T09:00"),
        checkerName: String? = "R. Levi",
        designerName: String? = "A. Cohen",
        erectedAt: Long? = at("2026-06-02T07:00"),
        inspectedAt: Long? = at("2026-06-02T16:00"),
        struckAt: Long? = null,
    ) = TemporaryWorks.loadBlockers(
        designReference = designReference,
        category = category,
        checkedAt = checkedAt,
        checkerName = checkerName,
        designerName = designerName,
        erectedAt = erectedAt,
        inspectedAt = inspectedAt,
        struckAt = struckAt,
    )

    private fun strikeBlockers(
        erectedAt: Long? = at("2026-06-02T07:00"),
        struckAt: Long? = null,
        supportsPourAt: Long? = at("2026-06-03T10:00"),
        minimumStrikingDays: Long = TemporaryWorks.DEFAULT_STRIKING_DAYS,
        releasedAt: Long? = at("2026-06-18T08:00"),
        now: Long = at("2026-06-18T09:00"),
    ) = TemporaryWorks.strikeBlockers(
        erectedAt = erectedAt,
        struckAt = struckAt,
        supportsPourAt = supportsPourAt,
        minimumStrikingDays = minimumStrikingDays,
        releasedAt = releasedAt,
        now = now,
        zone = israel,
    )

    @Test
    fun `designed, checked, erected and inspected may take load`() {
        assertEquals(emptySet<TemporaryWorks.LoadBlocker>(), loadBlockers())
        assertTrue(TemporaryWorks.mayLoad(loadBlockers()))
    }

    @Test
    fun `a prop somebody guessed at is not a design`() {
        assertTrue(loadBlockers(designReference = null).contains(TemporaryWorks.LoadBlocker.NO_DESIGN))
        assertTrue(loadBlockers(designReference = "  ").contains(TemporaryWorks.LoadBlocker.NO_DESIGN))
    }

    @Test
    fun `everything missing is reported at once, not one at a time`() {
        val found = loadBlockers(
            designReference = null,
            checkedAt = null,
            erectedAt = null,
            inspectedAt = null,
        )
        assertTrue(
            found.containsAll(
                setOf(
                    TemporaryWorks.LoadBlocker.NO_DESIGN,
                    TemporaryWorks.LoadBlocker.NOT_CHECKED,
                    TemporaryWorks.LoadBlocker.NOT_ERECTED,
                    TemporaryWorks.LoadBlocker.NOT_INSPECTED,
                ),
            ),
        )
    }

    @Test
    fun `built is not the same as checked against the design`() {
        // The commonest way temporary works go wrong is not a bad design. It
        // is a good design built differently, and nobody comparing the two.
        assertTrue(
            loadBlockers(inspectedAt = null)
                .contains(TemporaryWorks.LoadBlocker.NOT_INSPECTED),
        )
    }

    @Test
    fun `on a category that needs an independent check the designer cannot check himself`() {
        val same = loadBlockers(
            category = TemporaryWorks.CheckCategory.TWO,
            checkerName = "A. Cohen",
            designerName = "a. cohen ",
        )
        assertTrue(same.contains(TemporaryWorks.LoadBlocker.CHECKER_NOT_INDEPENDENT))
    }

    @Test
    fun `on a standard solution the same person may sign both`() {
        val same = loadBlockers(
            category = TemporaryWorks.CheckCategory.ZERO,
            checkerName = "A. Cohen",
            designerName = "A. Cohen",
        )
        assertFalse(same.contains(TemporaryWorks.LoadBlocker.CHECKER_NOT_INDEPENDENT))
    }

    @Test
    fun `categories two and three want somebody from outside, nought and one do not`() {
        assertFalse(TemporaryWorks.CheckCategory.ZERO.needsIndependentChecker)
        assertFalse(TemporaryWorks.CheckCategory.ONE.needsIndependentChecker)
        assertTrue(TemporaryWorks.CheckCategory.TWO.needsIndependentChecker)
        assertTrue(TemporaryWorks.CheckCategory.THREE.needsIndependentChecker)
    }

    @Test
    fun `something already struck cannot be loaded again`() {
        assertTrue(
            loadBlockers(struckAt = at("2026-06-20T08:00"))
                .contains(TemporaryWorks.LoadBlocker.STRUCK),
        )
    }

    @Test
    fun `props under fresh concrete stay in until it has had its days`() {
        // Poured on the 3rd, fourteen days owed, released by the engineer.
        val early = strikeBlockers(now = at("2026-06-16T09:00"))
        assertTrue(early.contains(TemporaryWorks.StrikeBlocker.CONCRETE_TOO_YOUNG))
        assertFalse(TemporaryWorks.mayStrike(early))

        val onTime = strikeBlockers(now = at("2026-06-17T09:00"))
        assertFalse(onTime.contains(TemporaryWorks.StrikeBlocker.CONCRETE_TOO_YOUNG))
    }

    @Test
    fun `the engineer's number wins over the app's default`() {
        // Seven days rather than fourteen, because that is what was specified.
        val found = strikeBlockers(minimumStrikingDays = 7L, now = at("2026-06-10T09:00"))
        assertFalse(found.contains(TemporaryWorks.StrikeBlocker.CONCRETE_TOO_YOUNG))
        assertTrue(TemporaryWorks.mayStrike(found))
    }

    @Test
    fun `nothing comes down without somebody releasing it, however old the concrete`() {
        val found = strikeBlockers(releasedAt = null, now = at("2026-07-30T09:00"))
        assertTrue(found.contains(TemporaryWorks.StrikeBlocker.NOT_RELEASED))
        assertFalse(TemporaryWorks.mayStrike(found))
    }

    @Test
    fun `temporary works holding up no concrete are released on somebody's word alone`() {
        // Edge protection, a hoarding, a trench box: nothing is curing, so the
        // only gate is that somebody with the authority said so.
        val found = strikeBlockers(supportsPourAt = null, now = at("2026-06-04T09:00"))
        assertTrue(TemporaryWorks.mayStrike(found))
    }

    @Test
    fun `days still owed counts down and stops at zero`() {
        val pour = at("2026-06-03T10:00")
        assertEquals(14L, TemporaryWorks.daysStillOwed(pour, 14L, at("2026-06-03T23:00"), israel))
        assertEquals(4L, TemporaryWorks.daysStillOwed(pour, 14L, at("2026-06-13T06:00"), israel))
        assertEquals(0L, TemporaryWorks.daysStillOwed(pour, 14L, at("2026-06-17T06:00"), israel))
        assertEquals(0L, TemporaryWorks.daysStillOwed(pour, 14L, at("2026-08-01T06:00"), israel))
        assertEquals(0L, TemporaryWorks.daysStillOwed(null, 14L, at("2026-06-03T23:00"), israel))
    }

    @Test
    fun `the fourteen days that span a clock change are still fourteen dates`() {
        // Poured 20 March 2026; Israel goes to summer time on the 27th. The
        // props are owed until the 3rd of April whichever way the clocks moved.
        val pour = at("2026-03-20T10:00")
        assertEquals(1L, TemporaryWorks.daysStillOwed(pour, 14L, at("2026-04-02T09:00"), israel))
        assertEquals(0L, TemporaryWorks.daysStillOwed(pour, 14L, at("2026-04-03T09:00"), israel))
    }

    @Test
    fun `it cannot be struck twice`() {
        assertTrue(
            strikeBlockers(struckAt = at("2026-06-18T10:00"))
                .contains(TemporaryWorks.StrikeBlocker.ALREADY_STRUCK),
        )
    }
}
