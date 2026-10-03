package il.co.tradesmanager.core.evidence

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectionsTest {

    private val zone = ZoneId.of("Asia/Jerusalem")
    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val thursday = LocalDate.of(2026, 10, 1)

    @Test
    fun `a request says what, where, and of whom`() {
        val now = at(thursday, 9)
        assertEquals(Inspections.Refusal.BLANK_ELEMENT, Inspections.requestRefusal(" ", "Engineer", now, null, zone))
        assertEquals(Inspections.Refusal.NOBODY_ASKED, Inspections.requestRefusal("L3 slab", " ", now, null, zone))
        assertNull(Inspections.requestRefusal("L3 slab", "Engineer", now, null, zone))
    }

    @Test
    fun `wanted today is fine, wanted yesterday is not`() {
        val now = at(thursday, 15)
        assertNull(Inspections.requestRefusal("L3 slab", "Engineer", now, at(thursday, 0), zone))
        assertEquals(
            Inspections.Refusal.WANTED_TOO_EARLY,
            Inspections.requestRefusal("L3 slab", "Engineer", now, at(thursday.minusDays(1), 0), zone),
        )
    }

    @Test
    fun `a result needs a name, and a failure or a comment needs its words`() {
        val passed = Inspections.Result.PASSED
        assertEquals(Inspections.Refusal.NO_INSPECTOR, Inspections.resultRefusal(passed, " ", "", null))
        assertNull(Inspections.resultRefusal(passed, "Dana Levi", "", null))
        assertEquals(
            Inspections.Refusal.FAILED_WITHOUT_REASON,
            Inspections.resultRefusal(Inspections.Result.FAILED, "Dana Levi", " ", null),
        )
        assertNull(Inspections.resultRefusal(Inspections.Result.FAILED, "Dana Levi", "Laps short at grid C", null))
        assertEquals(
            Inspections.Refusal.COMMENTS_MISSING,
            Inspections.resultRefusal(Inspections.Result.PASSED_WITH_COMMENTS, "Dana Levi", "", null),
        )
        assertEquals(
            "written once",
            Inspections.Refusal.ALREADY_DECIDED,
            Inspections.resultRefusal(passed, "Dana Levi", "", decidedAt = 1L),
        )
    }

    @Test
    fun `only a failed inspection is asked again, and only once`() {
        assertNull(Inspections.reinspectRefusal(Inspections.Result.FAILED, alreadyReinspected = false))
        assertEquals(Inspections.Refusal.ALREADY_REINSPECTED, Inspections.reinspectRefusal(Inspections.Result.FAILED, true))
        assertEquals(Inspections.Refusal.NOT_FAILED, Inspections.reinspectRefusal(Inspections.Result.PASSED, false))
        assertEquals("still waiting", Inspections.Refusal.NOT_FAILED, Inspections.reinspectRefusal(null, false))
    }

    @Test
    fun `a pour is cleared by a passed inspection of the steel or the forms, once, on its own job`() {
        fun refusal(
            kind: Inspections.Kind = Inspections.Kind.REINFORCEMENT,
            result: Inspections.Result? = Inspections.Result.PASSED,
            cleared: String? = null,
            pourJob: String = "job.1",
        ) = Inspections.clearPourRefusal(kind, result, cleared, "job.1", pourJob)

        assertNull(refusal())
        assertNull(refusal(kind = Inspections.Kind.FORMWORK, result = Inspections.Result.PASSED_WITH_COMMENTS))
        assertEquals(Inspections.Refusal.NOT_PASSED, refusal(result = Inspections.Result.FAILED))
        assertEquals(Inspections.Refusal.NOT_PASSED, refusal(result = null))
        assertEquals(Inspections.Refusal.NOT_FOR_A_POUR, refusal(kind = Inspections.Kind.WATERPROOFING))
        assertEquals(Inspections.Refusal.ALREADY_CLEARED_A_POUR, refusal(cleared = "pour.7"))
        assertEquals(Inspections.Refusal.OTHER_JOB, refusal(pourJob = "job.2"))
    }

    @Test
    fun `overdue from the day after the one it was wanted, on the site's own clock`() {
        val wanted = at(thursday, 0)
        assertEquals(
            "Thursday evening is still Thursday",
            Inspections.State.WAITING,
            Inspections.state(null, wanted, false, at(thursday, 23), zone),
        )
        assertEquals(Inspections.State.OVERDUE, Inspections.state(null, wanted, false, at(thursday.plusDays(1), 0) + 1, zone))
        assertEquals(
            "no date is never overdue",
            Inspections.State.WAITING,
            Inspections.state(null, null, false, at(thursday.plusDays(99), 9), zone),
        )
    }

    @Test
    fun `a failure holds the work until it is asked again`() {
        val now = at(thursday, 9)
        val failed = Inspections.state(Inspections.Result.FAILED, null, reinspected = false, now = now, zone = zone)
        val followed = Inspections.state(Inspections.Result.FAILED, null, reinspected = true, now = now, zone = zone)
        assertEquals(Inspections.State.FAILED, failed)
        assertEquals(Inspections.State.REINSPECTED, followed)
        assertTrue(Inspections.outstanding(failed))
        assertFalse("the re-inspection is the one to follow now", Inspections.outstanding(followed))
        assertFalse(Inspections.outstanding(Inspections.state(Inspections.Result.PASSED_WITH_COMMENTS, null, false, now, zone)))
        assertTrue(Inspections.outstanding(Inspections.State.WAITING))
        assertTrue(Inspections.outstanding(Inspections.State.OVERDUE))
    }

    @Test
    fun `overdue first, then failed, then waiting, then done, oldest first within each`() {
        val rows = listOf(
            Inspections.State.PASSED to 1L,
            Inspections.State.WAITING to 5L,
            Inspections.State.OVERDUE to 9L,
            Inspections.State.FAILED to 2L,
            Inspections.State.WAITING to 3L,
            Inspections.State.REINSPECTED to 0L,
        )
        assertEquals(listOf(2, 3, 4, 1, 0, 5), Inspections.order(rows))
    }

    @Test
    fun `numbered per job, and stored words this version does not know are read safely`() {
        assertEquals("IR-001", Inspections.reference(0))
        assertEquals("IR-010", Inspections.reference(9))
        assertEquals("IR-1000", Inspections.reference(999))
        assertEquals(Inspections.Kind.FORMWORK, Inspections.kindOf("FORMWORK"))
        assertEquals(Inspections.Kind.OTHER, Inspections.kindOf("DRONE_SURVEY"))
        assertEquals(Inspections.Result.FAILED, Inspections.resultOf("FAILED"))
        assertNull(Inspections.resultOf(null))
    }
}
