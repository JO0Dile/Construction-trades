package il.co.tradesmanager.core.safety

import il.co.tradesmanager.core.safety.Muster.OpenCheckIn
import il.co.tradesmanager.core.safety.Muster.Outcome
import il.co.tradesmanager.core.safety.Muster.Refusal
import il.co.tradesmanager.core.safety.Muster.Roll
import il.co.tradesmanager.core.safety.Muster.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusterTest {

    private val hour = 60L * 60L * 1000L
    private val minute = 60L * 1000L
    private val now = 1_700_000_000_000L

    private fun rows(index: Int) = "r$index"

    private fun changed(outcome: Outcome): Roll {
        assertTrue("expected the change to go through, got $outcome", outcome is Outcome.Changed)
        return (outcome as Outcome.Changed).roll
    }

    private fun refusal(outcome: Outcome): Refusal {
        assertTrue("expected a refusal, got $outcome", outcome is Outcome.Refused)
        return (outcome as Outcome.Refused).reason
    }

    @Test
    fun `the list is the open check-ins, longest on site first`() {
        val roll = Muster.start(
            openCheckIns = listOf(
                OpenCheckIn(personId = null, name = "Delivery", checkInAt = now - 2 * hour),
                OpenCheckIn(personId = "w1", name = "Ahmad", checkInAt = now - 3 * hour),
                OpenCheckIn(personId = "w2", name = "Yossi", checkInAt = now - 40 * hour),
            ),
            startedAt = now,
            id = ::rows,
        )

        assertEquals(listOf("Yossi", "Ahmad", "Delivery"), roll.people.map { it.name })
        assertEquals(listOf(true, false, false), roll.people.map { it.staleCheckIn })
        assertEquals(3, roll.unaccountedCount)
        assertFalse(roll.everyoneAccountedFor)
        assertTrue(roll.isLive)
    }

    @Test
    fun `a check-in open longer than a shift is flagged, never dropped`() {
        val exactly = Muster.start(
            listOf(OpenCheckIn("w1", "A", now - Muster.STALE_AFTER_MS)), now, ::rows,
        )
        val over = Muster.start(
            listOf(OpenCheckIn("w1", "A", now - Muster.STALE_AFTER_MS - 1)), now, ::rows,
        )

        assertFalse(exactly.people.single().staleCheckIn)
        assertTrue(over.people.single().staleCheckIn)
        assertEquals(1, over.people.size)
    }

    @Test
    fun `one account twice is one person, but two unknowns stay two rows`() {
        val roll = Muster.start(
            listOf(
                OpenCheckIn("w1", "Ahmad", now - 3 * hour),
                OpenCheckIn("w1", "Ahmad", now - hour),
                OpenCheckIn(null, "Guest", now - hour),
                OpenCheckIn(null, "Guest", now - hour),
            ),
            now,
            ::rows,
        )

        assertEquals(3, roll.people.size)
        assertEquals(1, roll.people.count { it.personId == "w1" })
        assertEquals(2, roll.people.count { it.personId == null })
    }

    @Test
    fun `a check-in with no name falls back to the account, not to nothing`() {
        val roll = Muster.start(listOf(OpenCheckIn("w9", "   ", now)), now, ::rows)

        assertEquals("w9", roll.people.single().name)
    }

    @Test
    fun `the site is the one every check-in agrees on, or none at all`() {
        fun on(vararg jobs: String?) = jobs.mapIndexed { index, job ->
            OpenCheckIn(personId = "w$index", name = "W$index", checkInAt = now, projectId = job)
        }

        assertNull(Muster.siteOf(emptyList()))
        assertEquals("site.a", Muster.siteOf(on("site.a")))
        assertEquals("site.a", Muster.siteOf(on("site.a", "site.a", "site.a")))
        assertNull("two sites is not one site", Muster.siteOf(on("site.a", "site.b")))
        assertNull("a check-in on no job makes it no job", Muster.siteOf(on("site.a", null)))
        assertNull(Muster.siteOf(on(null, "site.a")))
        assertNull(Muster.siteOf(on(null, null)))
    }

    @Test
    fun `present needs nobody to explain anything`() {
        val roll = Muster.start(listOf(OpenCheckIn("w1", "Ahmad", now - hour)), now, ::rows)

        val after = changed(Muster.settle(roll, "r0", State.PRESENT, at = now + minute))

        assertEquals(State.PRESENT, after.people.single().state)
        assertNull(after.people.single().account)
        assertEquals(now + minute, after.people.single().settledAt)
        assertTrue(after.everyoneAccountedFor)
    }

    @Test
    fun `accounted for elsewhere without saying how is refused`() {
        val roll = Muster.start(listOf(OpenCheckIn("w1", "Ahmad", now - hour)), now, ::rows)

        assertEquals(
            Refusal.NO_ACCOUNT_GIVEN,
            refusal(Muster.settle(roll, "r0", State.ACCOUNTED_ELSEWHERE, "   ", now)),
        )
        assertEquals(
            Refusal.NO_ACCOUNT_GIVEN,
            refusal(Muster.settle(roll, "r0", State.ACCOUNTED_ELSEWHERE, null, now)),
        )

        val after = changed(
            Muster.settle(roll, "r0", State.ACCOUNTED_ELSEWHERE, "rang him, went home at four", now),
        )
        assertEquals("rang him, went home at four", after.people.single().account)
    }

    @Test
    fun `a row ticked by mistake goes back on the missing list`() {
        val roll = Muster.start(listOf(OpenCheckIn("w1", "Ahmad", now - hour)), now, ::rows)
        val ticked = changed(Muster.settle(roll, "r0", State.ACCOUNTED_ELSEWHERE, "rang him", now))

        val undone = changed(Muster.settle(ticked, "r0", State.UNACCOUNTED, at = now + minute))

        assertEquals(State.UNACCOUNTED, undone.people.single().state)
        assertNull(undone.people.single().account)
        assertNull(undone.people.single().settledAt)
        assertEquals(1, undone.unaccountedCount)
    }

    @Test
    fun `a name that is not on the list cannot be settled`() {
        val roll = Muster.start(listOf(OpenCheckIn("w1", "Ahmad", now - hour)), now, ::rows)

        assertEquals(
            Refusal.NOT_ON_THE_LIST,
            refusal(Muster.settle(roll, "nobody", State.PRESENT, at = now)),
        )
    }

    @Test
    fun `somebody who never checked in can be added, and is not safe by being added`() {
        val roll = Muster.start(listOf(OpenCheckIn("w1", "Ahmad", now - hour)), now, ::rows)

        val after = changed(Muster.add(roll, "  Visitor  ", "rx"))

        val added = after.people.last()
        assertEquals("Visitor", added.name)
        assertEquals(State.UNACCOUNTED, added.state)
        assertTrue(added.addedDuringRollCall)
        assertNull(added.personId)
        assertEquals(2, after.unaccountedCount)
        assertEquals(Refusal.BLANK_NAME, refusal(Muster.add(after, "   ", "ry")))
    }

    @Test
    fun `missing names are read first`() {
        var roll = Muster.start(
            listOf(
                OpenCheckIn("w1", "Ahmad", now - 3 * hour),
                OpenCheckIn("w2", "Yossi", now - 40 * hour),
                OpenCheckIn(null, "Delivery", now - 2 * hour),
            ),
            now,
            ::rows,
        )
        roll = changed(Muster.settle(roll, "r0", State.PRESENT, at = now))
        roll = changed(Muster.add(roll, "Visitor", "rx"))

        assertEquals(
            listOf("Ahmad", "Delivery", "Visitor", "Yossi"),
            roll.ordered().map { it.name },
        )
        assertEquals(listOf("Ahmad", "Delivery", "Visitor"), roll.unaccounted().map { it.name })
    }

    @Test
    fun `a roll call can be ended with people still missing, and it names them`() {
        var roll = Muster.start(
            listOf(
                OpenCheckIn("w1", "Ahmad", now - 3 * hour),
                OpenCheckIn("w2", "Yossi", now - hour),
            ),
            now,
            ::rows,
        )
        roll = changed(Muster.settle(roll, "r1", State.PRESENT, at = now + minute))

        val ended = changed(Muster.end(roll, now + 11 * minute))
        val record = Muster.ended(ended)

        assertFalse(ended.isLive)
        assertEquals(listOf("Ahmad"), record!!.unaccounted.map { it.name })
        assertFalse(record.everybodyOut)
        assertEquals(11L, ended.minutesRunning(now + 99 * minute))
    }

    @Test
    fun `an ended roll call is a record and does not change`() {
        val roll = Muster.start(listOf(OpenCheckIn("w1", "Ahmad", now - hour)), now, ::rows)
        val ended = changed(Muster.end(roll, now + minute))

        assertEquals(Refusal.ALREADY_ENDED, refusal(Muster.settle(ended, "r0", State.PRESENT, at = now)))
        assertEquals(Refusal.ALREADY_ENDED, refusal(Muster.add(ended, "Late", "rz")))
        assertEquals(Refusal.ALREADY_ENDED, refusal(Muster.end(ended, now + 2 * minute)))
    }

    @Test
    fun `everybody accounted for is not the same as ended`() {
        var roll = Muster.start(listOf(OpenCheckIn("w1", "Ahmad", now - hour)), now, ::rows)
        roll = changed(Muster.settle(roll, "r0", State.PRESENT, at = now))

        assertTrue(roll.everyoneAccountedFor)
        assertTrue(roll.isLive)
        assertNull(Muster.ended(roll))

        val ended = changed(Muster.end(roll, now + 2 * minute))
        assertTrue(Muster.ended(ended)!!.everybodyOut)
    }

    @Test
    fun `a clock that goes backwards cannot produce a negative roll call`() {
        val roll = Roll(startedAt = now)

        assertEquals(0L, roll.minutesRunning(now - 5 * minute))

        val ended = changed(Muster.end(roll, now - minute))
        assertEquals(now, ended.endedAt)
        assertEquals(0L, ended.minutesRunning(now))
    }

    @Test
    fun `a roll call still running after ten minutes says so`() {
        val roll = Roll(startedAt = now)

        assertFalse(roll.isLongRunning(now + 9 * minute))
        assertTrue(roll.isLongRunning(now + Muster.LONG_RUNNING_MINUTES * minute))

        val ended = changed(Muster.end(roll, now + 30 * minute))
        assertFalse("an ended roll call is not still running", ended.isLongRunning(now + 99 * minute))
    }
}
