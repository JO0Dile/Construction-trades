package il.co.tradesmanager.core.time

import il.co.tradesmanager.core.time.DayPlan.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which block a shift is worked against.
 *
 * A shift on the wrong block is worse than a shift on none: it makes the
 * planned-against-actual comparison read backwards for two pieces of work at
 * once. So every case here is about refusing to guess.
 */
class DayPlanTest {

    private val dani = "acc.dani"
    private val sami = "acc.sami"
    private val tower = "proj.tower"
    private val villa = "proj.villa"

    private fun block(
        id: String,
        project: String? = tower,
        assignee: String? = dani,
        from: Int = 8 * 60,
        to: Int = 12 * 60,
    ) = Block(id, project, assignee, from, to)

    @Test
    fun `a shift lands on the block running at the time`() {
        val blocks = listOf(block("morning"), block("afternoon", from = 13 * 60, to = 17 * 60))
        assertEquals("morning", DayPlan.blockForShift(blocks, dani, tower, 9 * 60))
        assertEquals("afternoon", DayPlan.blockForShift(blocks, dani, tower, 14 * 60))
    }

    @Test
    fun `arriving early lands on the block still to come`() {
        // On site at ten to seven for an eight o'clock start, which is most
        // of a site most mornings.
        val blocks = listOf(block("morning"), block("afternoon", from = 13 * 60, to = 17 * 60))
        assertEquals("morning", DayPlan.blockForShift(blocks, dani, tower, 6 * 60 + 50))
    }

    @Test
    fun `starting late lands on the block already running, not the next one`() {
        // Half nine on an eight-to-twelve block is late for that one, not
        // early for the afternoon.
        val blocks = listOf(block("morning"), block("afternoon", from = 13 * 60, to = 17 * 60))
        assertEquals("morning", DayPlan.blockForShift(blocks, dani, tower, 9 * 60 + 30))
    }

    @Test
    fun `working after everything is over is on no block at all`() {
        val blocks = listOf(block("morning"), block("afternoon", from = 13 * 60, to = 17 * 60))
        assertNull(DayPlan.blockForShift(blocks, dani, tower, 19 * 60))
    }

    @Test
    fun `a block belonging to somebody else is not taken`() {
        // The plan says who does it. Attaching this shift to it would put
        // Dani's name on Sami's work.
        assertNull(DayPlan.blockForShift(listOf(block("morning", assignee = sami)), dani, tower, 9 * 60))
    }

    @Test
    fun `an unassigned block is not taken either`() {
        // The plan saying what should happen is not the plan saying who.
        assertNull(DayPlan.blockForShift(listOf(block("morning", assignee = null)), dani, tower, 9 * 60))
    }

    @Test
    fun `a block on another job is not taken`() {
        // Recording the job is the whole reason the labour arithmetic works
        // per job. A block on the villa must not absorb a shift on the tower.
        assertNull(DayPlan.blockForShift(listOf(block("morning", project = villa)), dani, tower, 9 * 60))
    }

    @Test
    fun `a shift with no job still matches their own block`() {
        // A sole trader, or somebody who checked in before picking a job.
        // Their own block is still theirs.
        assertEquals("morning", DayPlan.blockForShift(listOf(block("morning")), dani, null, 9 * 60))
    }

    @Test
    fun `nobody signed in matches nothing`() {
        // A shift recorded against a typed name cannot be tied to an
        // assignment, because there is nothing to tie it to.
        assertNull(DayPlan.blockForShift(listOf(block("morning")), null, tower, 9 * 60))
        assertNull(DayPlan.blockForShift(listOf(block("morning")), "  ", tower, 9 * 60))
    }

    @Test
    fun `a double booking takes the one just moved onto`() {
        val blocks = listOf(
            block("all.day", from = 7 * 60, to = 17 * 60),
            block("the.pour", from = 9 * 60, to = 11 * 60),
        )
        assertEquals("the.pour", DayPlan.blockForShift(blocks, dani, tower, 10 * 60))
    }

    @Test
    fun `the same day always gives the same answer`() {
        // Two blocks starting at the same minute is a plan somebody has to
        // fix, but it must not make the shift land somewhere different every
        // time the list arrives in a different order.
        val a = block("a.first", from = 8 * 60, to = 12 * 60)
        val b = block("b.second", from = 8 * 60, to = 12 * 60)
        assertEquals(
            DayPlan.blockForShift(listOf(a, b), dani, tower, 9 * 60),
            DayPlan.blockForShift(listOf(b, a), dani, tower, 9 * 60),
        )
        assertEquals("b.second", DayPlan.blockForShift(listOf(a, b), dani, tower, 9 * 60))
    }

    @Test
    fun `the end minute is not part of the block`() {
        // Twelve o'clock is the start of the afternoon, not the end of the
        // morning: an end that included its own last minute would put a shift
        // on two blocks at the boundary.
        val blocks = listOf(block("morning", from = 8 * 60, to = 12 * 60))
        assertNull(DayPlan.blockForShift(blocks, dani, tower, 12 * 60))
    }

    @Test
    fun `an empty plan is no block`() {
        assertNull(DayPlan.blockForShift(emptyList(), dani, tower, 9 * 60))
    }
}
