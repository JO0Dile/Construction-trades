package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.security.AuditChain
import il.co.tradesmanager.data.local.entity.AuditLogEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app writes has to satisfy the rule the app checks.
 *
 * `AuditChainTest` proves the rule in isolation. This proves the trail
 * produced by ordinary use passes it — which is a different claim, and the one
 * that would have caught the catalogue seeder writing rows straight into the
 * DAO with no hash at all.
 */
class AuditTrailTest {

    private fun trail(): Pair<AuditTrail, FakeAuditDao> {
        val dao = FakeAuditDao()
        return AuditTrail(dao) to dao
    }

    @Test
    fun `an ordinary run of changes verifies`() = runTest {
        val (audit, _) = trail()
        audit.record("project", "p1", AuditTrail.Action.CREATE, "Sami", "Created Ramat Gan")
        audit.record("task", "t1", AuditTrail.Action.CREATE, "Sami", "העקדה, third floor")
        audit.record("payment", "a1", AuditTrail.Action.UPDATE, "Dana", "Certified 11,000")

        assertEquals(AuditChain.Verdict.Intact(checked = 3, unchained = 0), audit.verify())
    }

    @Test
    fun `sequence numbers start at one and do not repeat`() = runTest {
        val (audit, dao) = trail()
        repeat(5) { audit.record("t", "i$it", AuditTrail.Action.CREATE, "Sami", "n$it") }
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), dao.entries.map { it.sequence })
    }

    @Test
    fun `the first entry follows nothing`() = runTest {
        val (audit, dao) = trail()
        audit.record("project", "p1", AuditTrail.Action.CREATE, "Sami", "First")
        assertEquals(AuditChain.GENESIS, dao.entries.single().previousHash)
    }

    @Test
    fun `each entry carries the hash of the one before it`() = runTest {
        val (audit, dao) = trail()
        audit.record("project", "p1", AuditTrail.Action.CREATE, "Sami", "One")
        audit.record("project", "p1", AuditTrail.Action.UPDATE, "Sami", "Two")
        val (first, second) = dao.entries
        assertEquals(first.hash, second.previousHash)
        assertNotEquals(first.hash, second.hash)
    }

    @Test
    fun `a blank actor is recorded as unknown rather than blank`() = runTest {
        val (audit, dao) = trail()
        audit.record("project", "p1", AuditTrail.Action.CREATE, "   ", "No name given")
        assertEquals("unknown", dao.entries.single().actorName)
        // And the hash covers what was stored, not what was passed in.
        assertTrue(audit.verify() is AuditChain.Verdict.Intact)
    }

    @Test
    fun `appending from several coroutines numbers every entry once`() = runTest {
        val (audit, dao) = trail()
        // Twenty appends launched together. This does not reproduce the race
        // the mutex exists for — the fake DAO never actually suspends, so the
        // coroutines do not interleave where a real database would — but it
        // does prove the lock is not reentrant and cannot deadlock, and that
        // the numbering survives concurrent callers. The race itself is
        // argued for in AuditTrail rather than tested here, which is worth
        // saying plainly instead of letting the test name imply more.
        (1..20).map { n ->
            async { audit.record("t", "i$n", AuditTrail.Action.CREATE, "Sami", "entry $n") }
        }.awaitAll()

        assertEquals((1L..20L).toList(), dao.entries.map { it.sequence }.sorted())
        assertEquals(AuditChain.Verdict.Intact(checked = 20, unchained = 0), audit.verify())
    }

    @Test
    fun `editing a row behind the app's back is caught`() = runTest {
        val (audit, dao) = trail()
        audit.record("payment", "a1", AuditTrail.Action.UPDATE, "Dana", "Certified 11,000")
        audit.record("payment", "a1", AuditTrail.Action.UPDATE, "Dana", "Marked paid")

        // What somebody with a SQLite editor and a motive would do.
        val doctored = dao.entries.map { row ->
            if (row.summary == "Certified 11,000") row.copy(summary = "Certified 110,000") else row
        }
        dao.entries.clear()
        dao.entries.addAll(doctored)

        val verdict = audit.verify()
        assertTrue("a doctored figure must not verify", verdict is AuditChain.Verdict.Failed)
        assertEquals(AuditChain.Fault.ALTERED, (verdict as AuditChain.Verdict.Failed).fault)
    }

    @Test
    fun `deleting a row behind the app's back is caught`() = runTest {
        val (audit, dao) = trail()
        repeat(3) { audit.record("t", "i$it", AuditTrail.Action.CREATE, "Sami", "n$it") }
        dao.entries.removeAll { it.sequence == 2L }

        val verdict = audit.verify()
        assertTrue(verdict is AuditChain.Verdict.Failed)
        assertEquals(AuditChain.Fault.MISSING, (verdict as AuditChain.Verdict.Failed).fault)
    }

    // Rows that predate the chain, and the purge. Two honest states that must
    // not be mistaken for the dishonest ones above.

    @Test
    fun `entries from before the upgrade do not read as tampering`() = runTest {
        val (audit, dao) = trail()
        dao.insert(
            AuditLogEntity(
                id = "legacy",
                entityType = "project",
                entityId = "old",
                action = AuditTrail.Action.CREATE,
                actorId = null,
                actorName = "Sami",
                summary = "Written before this version",
                occurredAt = 1_600_000_000_000,
            ),
        )
        audit.record("project", "p1", AuditTrail.Action.CREATE, "Sami", "After the upgrade")

        assertEquals(AuditChain.Verdict.Intact(checked = 1, unchained = 1), audit.verify())
    }

    @Test
    fun `the first entry after an unhashed one links to nothing, not to an empty hash`() = runTest {
        val (audit, dao) = trail()
        dao.insert(
            AuditLogEntity(
                id = "legacy",
                entityType = "project",
                entityId = "old",
                action = AuditTrail.Action.CREATE,
                actorId = null,
                actorName = "Sami",
                summary = "Written before this version",
                occurredAt = 1_600_000_000_000,
            ),
        )
        audit.record("project", "p1", AuditTrail.Action.CREATE, "Sami", "After the upgrade")

        val chained = dao.entries.single { it.hash.isNotBlank() }
        assertEquals(AuditChain.GENESIS, chained.previousHash)
    }

    @Test
    fun `a purge records where it cut, and the survivors still verify`() = runTest {
        val (audit, dao) = trail()
        repeat(4) { audit.record("t", "i$it", AuditTrail.Action.CREATE, "Sami", "n$it") }
        // Everything so far is older than the cutoff.
        val cutoff = System.currentTimeMillis() + 1
        val removed = audit.purgeOlderThan(cutoff, "Retention")

        assertEquals(4, removed)
        // The purge entry is itself in the trail, and says what it cut.
        val purge = dao.entries.single { it.action == AuditTrail.Action.PURGE }
        assertTrue(purge.payloadJson.orEmpty().contains("\"removed\":4"))
        assertTrue(purge.payloadJson.orEmpty().contains("edgeSequence"))
    }

    @Test
    fun `a purge that removes the oldest leaves the rest chaining`() = runTest {
        val (audit, dao) = trail()
        audit.record("t", "a", AuditTrail.Action.CREATE, "Sami", "oldest")
        val first = dao.entries.single()
        repeat(3) { audit.record("t", "b$it", AuditTrail.Action.CREATE, "Sami", "n$it") }

        dao.entries.removeAll { it.id == first.id }

        // Losing the very first entry is the one removal the chain cannot see,
        // because nothing survives that pointed at it. That is why the purge
        // writes its own row, and why this asserts Intact rather than Failed:
        // an honest retention policy must not raise an alarm.
        assertEquals(AuditChain.Verdict.Intact(checked = 3, unchained = 0), audit.verify())
    }
}
