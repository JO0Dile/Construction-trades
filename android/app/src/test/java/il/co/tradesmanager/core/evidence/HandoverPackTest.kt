package il.co.tradesmanager.core.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A handover pack that quietly omits what is still open is worse than no pack,
 * because somebody will file it and believe it.
 */
class HandoverPackTest {

    @Test
    fun `nothing open is a complete job`() {
        val readiness = HandoverPack.readiness(emptyMap())
        assertTrue(readiness.isComplete)
        assertEquals(0, readiness.total)
        assertTrue(readiness.outstanding.isEmpty())
    }

    @Test
    fun `zeroes are left out rather than listed`() {
        // Three blocking snags buried in a wall of "0 open permits" is how a
        // list stops being read.
        val readiness = HandoverPack.readiness(
            mapOf(
                HandoverPack.Item.BLOCKING_SNAGS to 3,
                HandoverPack.Item.OPEN_PERMITS to 0,
                HandoverPack.Item.EXCAVATIONS_OPEN to 0,
            ),
        )
        assertEquals(1, readiness.outstanding.size)
        assertEquals(HandoverPack.Item.BLOCKING_SNAGS, readiness.outstanding[0].item)
        assertEquals(3, readiness.total)
        assertFalse(readiness.isComplete)
    }

    @Test
    fun `the total counts things, not kinds`() {
        val readiness = HandoverPack.readiness(
            mapOf(
                HandoverPack.Item.BLOCKING_SNAGS to 3,
                HandoverPack.Item.OPEN_PERMITS to 11,
                HandoverPack.Item.SCAFFOLDS_STANDING to 1,
            ),
        )
        assertEquals(3, readiness.outstanding.size)
        assertEquals(15, readiness.total)
    }

    @Test
    fun `the order is what matters at handover, not what is biggest`() {
        // Twenty unsigned logs must not sort above one scaffold left standing
        // in the street.
        val readiness = HandoverPack.readiness(
            mapOf(
                HandoverPack.Item.UNSIGNED_DAILY_LOGS to 20,
                HandoverPack.Item.SCAFFOLDS_STANDING to 1,
                HandoverPack.Item.BLOCKING_SNAGS to 2,
            ),
        )
        assertEquals(
            listOf(
                HandoverPack.Item.BLOCKING_SNAGS,
                HandoverPack.Item.SCAFFOLDS_STANDING,
                HandoverPack.Item.UNSIGNED_DAILY_LOGS,
            ),
            readiness.outstanding.map { it.item },
        )
    }

    @Test
    fun `a count that has gone negative cannot cancel out something real`() {
        // Only a broken query produces one, and letting it subtract would hide
        // a genuine outstanding item behind an arithmetic accident.
        val readiness = HandoverPack.readiness(
            mapOf(
                HandoverPack.Item.BLOCKING_SNAGS to 2,
                HandoverPack.Item.OPEN_PERMITS to -5,
            ),
        )
        assertEquals(1, readiness.outstanding.size)
        assertEquals(2, readiness.total)
    }

    @Test
    fun `every kind the app records has a place in the pack`() {
        // If a register is added and nothing counts it here, the pack will say
        // a job is finished while that register is full. The enum is the list
        // somebody has to add to.
        val all = HandoverPack.Item.entries.associateWith { 1 }
        val readiness = HandoverPack.readiness(all)
        assertEquals(HandoverPack.Item.entries.size, readiness.outstanding.size)
        assertEquals(HandoverPack.Item.entries.size, readiness.total)
        assertFalse(readiness.isComplete)
    }
}
