package il.co.tradesmanager.core.safety

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A scaffold whose inspection has lapsed looks exactly like one whose has not.
 * The register is the only thing that can tell anybody, so it is pinned.
 */
class ScaffoldingTest {

    private val israel: ZoneId = ZoneId.of("Asia/Jerusalem")

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(israel).toInstant().toEpochMilli()

    private fun state(
        inspected: Long?,
        passed: Boolean = true,
        altered: Long? = null,
        now: Long,
    ) = Scaffolding.state(inspected, passed, altered, now, israel)

    @Test
    fun `a fresh passing inspection is safe for most of the week`() {
        val inspected = at("2026-06-01T08:00")
        assertEquals(Scaffolding.State.SAFE, state(inspected, now = at("2026-06-01T09:00")))
        assertEquals(Scaffolding.State.SAFE, state(inspected, now = at("2026-06-05T17:00")))
    }

    @Test
    fun `it starts asking a day out, so somebody can arrange the inspection`() {
        val inspected = at("2026-06-01T08:00")
        // Due on the 8th, so the 7th warns.
        assertEquals(Scaffolding.State.DUE_SOON, state(inspected, now = at("2026-06-07T06:00")))
        assertEquals(Scaffolding.State.DUE_SOON, state(inspected, now = at("2026-06-08T07:59")))
    }

    @Test
    fun `the moment the seven days run out it may not be climbed`() {
        val inspected = at("2026-06-01T08:00")
        val expired = state(inspected, now = at("2026-06-08T08:00"))
        assertEquals(Scaffolding.State.OVERDUE, expired)
        assertFalse(expired.mayBeClimbed)
    }

    @Test
    fun `a warning is still climbable and an overdue one is not`() {
        assertTrue(Scaffolding.State.SAFE.mayBeClimbed)
        assertTrue(Scaffolding.State.DUE_SOON.mayBeClimbed)
        assertFalse(Scaffolding.State.OVERDUE.mayBeClimbed)
        assertFalse(Scaffolding.State.FAILED.mayBeClimbed)
        assertFalse(Scaffolding.State.ALTERED.mayBeClimbed)
        assertFalse(Scaffolding.State.NEVER_INSPECTED.mayBeClimbed)
    }

    @Test
    fun `an alteration voids an inspection that still had days left on it`() {
        val inspected = at("2026-06-01T08:00")
        val altered = at("2026-06-02T10:00")
        assertEquals(
            Scaffolding.State.ALTERED,
            state(inspected, altered = altered, now = at("2026-06-03T08:00")),
        )
    }

    @Test
    fun `an alteration before the inspection is what the inspection was for`() {
        val altered = at("2026-06-01T06:00")
        val inspected = at("2026-06-01T08:00")
        assertEquals(
            Scaffolding.State.SAFE,
            state(inspected, altered = altered, now = at("2026-06-03T08:00")),
        )
    }

    @Test
    fun `a failed inspection is not redeemed by being recent`() {
        val inspected = at("2026-06-01T08:00")
        assertEquals(
            Scaffolding.State.FAILED,
            state(inspected, passed = false, now = at("2026-06-01T08:01")),
        )
    }

    @Test
    fun `erected and never inspected is not a blank row, it is a scaffold nobody climbs`() {
        assertEquals(Scaffolding.State.NEVER_INSPECTED, state(null, now = at("2026-06-01T08:00")))
    }

    @Test
    fun `the week Israel puts its clocks forward is still seven days, not 168 hours`() {
        // Israel moves to summer time on 27 March 2026. The seven days from
        // the 24th contain that change, so they are 167 hours — and the
        // deadline still has to land on the 31st at eight in the morning,
        // which is when a person would turn up expecting to inspect it.
        val inspected = at("2026-03-24T08:00")
        val due = Scaffolding.nextDueAt(inspected, israel)
        assertEquals(at("2026-03-31T08:00"), due)
        assertEquals(167L, (due - inspected) / (60L * 60L * 1000L))
    }

    @Test
    fun `days until due counts dates on the wall, not blocks of twenty-four hours`() {
        val inspected = at("2026-06-01T08:00")
        // Late on the 7th: due at 08:00 tomorrow is well under a day away in
        // hours, and still "tomorrow" to the person reading it.
        assertEquals(1L, Scaffolding.daysUntilDue(inspected, at("2026-06-07T23:00"), israel))
        assertEquals(0L, Scaffolding.daysUntilDue(inspected, at("2026-06-08T09:00"), israel))
        assertEquals(-2L, Scaffolding.daysUntilDue(inspected, at("2026-06-10T09:00"), israel))
    }

    @Test
    fun `the sort puts unusable scaffolds first, longest wrong at the top`() {
        val now = at("2026-06-20T08:00")
        val longOverdue = Scaffolding.urgency(
            Scaffolding.State.OVERDUE, at("2026-06-01T08:00"), now, israel,
        )
        val justOverdue = Scaffolding.urgency(
            Scaffolding.State.OVERDUE, at("2026-06-12T08:00"), now, israel,
        )
        val dueSoon = Scaffolding.urgency(
            Scaffolding.State.DUE_SOON, at("2026-06-14T08:00"), now, israel,
        )
        val fine = Scaffolding.urgency(
            Scaffolding.State.SAFE, at("2026-06-19T08:00"), now, israel,
        )
        val never = Scaffolding.urgency(
            Scaffolding.State.NEVER_INSPECTED, null, now, israel,
        )
        assertTrue(never < longOverdue)
        assertTrue(longOverdue < justOverdue)
        assertTrue(justOverdue < dueSoon)
        assertTrue(dueSoon < fine)
    }
}
