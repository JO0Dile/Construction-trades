package il.co.tradesmanager.core.security

import il.co.tradesmanager.core.security.AuditChain.Entry
import il.co.tradesmanager.core.security.AuditChain.Fault
import il.co.tradesmanager.core.security.AuditChain.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trail has to be believed later, by somebody who was not there.
 *
 * The expected digests below were computed by a separate implementation in
 * Python before this file was written. If Kotlin and that implementation ever
 * disagree, one of them changed the meaning of a signed record, and these
 * tests are the only thing that would say so.
 */
class AuditChainTest {

    private fun entry(
        sequence: Long,
        entityType: String,
        entityId: String,
        action: String,
        actorName: String,
        summary: String,
        payloadJson: String? = null,
        occurredAt: Long,
        previousHash: String,
        hash: String = "",
    ) = Entry(
        id = "e$sequence",
        sequence = sequence,
        entityType = entityType,
        entityId = entityId,
        action = action,
        actorId = null,
        actorName = actorName,
        summary = summary,
        payloadJson = payloadJson,
        occurredAt = occurredAt,
        previousHash = previousHash,
        hash = hash,
    )

    /** The three rows the Python model chained, with the digests it produced. */
    private fun sample(): List<Entry> {
        val one = entry(
            1, "project", "p1", "CREATE", "Sami", "Created Ramat Gan",
            occurredAt = 1_700_000_000_000, previousHash = AuditChain.GENESIS,
            hash = "7dcd8dddab30a32e3637f93af3ab890838f2478b6def476f76505d8b2b6ee625",
        )
        val two = entry(
            2, "task", "t1", "CREATE", "Sami", "Added שלב: העקדה",
            occurredAt = 1_700_000_001_000, previousHash = one.hash,
            hash = "95934aa1f2b163c2f4d8d55799326a4f89feb8b6ed111ca695fa56315446339f",
        )
        val three = entry(
            3, "payment", "a1", "UPDATE", "Dana", "Certified 11,000",
            payloadJson = """{"n":1}""",
            occurredAt = 1_700_000_002_000, previousHash = two.hash,
            hash = "ed366654e178eb238e9bdc323ce68867bf7934fb6c605199e0c1a052bbb4fedc",
        )
        return listOf(one, two, three)
    }

    @Test
    fun `the digests match an implementation written separately`() {
        sample().forEach { assertEquals(it.hash, AuditChain.recompute(it)) }
    }

    @Test
    fun `a chain nobody has touched verifies`() {
        assertEquals(Verdict.Intact(checked = 3, unchained = 0), AuditChain.verify(sample()))
    }

    @Test
    fun `an empty trail is empty, not suspicious`() {
        assertEquals(Verdict.Empty, AuditChain.verify(emptyList()))
    }

    // The three ways a log gets doctored.

    @Test
    fun `changing what an entry says shows`() {
        val rows = sample().toMutableList()
        // The figure somebody would want to change after the fact.
        rows[2] = rows[2].copy(summary = "Certified 110,000")
        val verdict = AuditChain.verify(rows)
        assertEquals(Verdict.Failed(Fault.ALTERED, 3, "e3", checked = 2, unchained = 0), verdict)
    }

    @Test
    fun `changing who did it shows`() {
        val rows = sample().toMutableList()
        rows[1] = rows[1].copy(actorName = "Dana")
        assertTrue(AuditChain.verify(rows) is Verdict.Failed)
    }

    @Test
    fun `changing when it happened shows`() {
        val rows = sample().toMutableList()
        rows[0] = rows[0].copy(occurredAt = 1_600_000_000_000)
        assertTrue(AuditChain.verify(rows) is Verdict.Failed)
    }

    @Test
    fun `removing an entry from the middle shows`() {
        val rows = listOf(sample()[0], sample()[2])
        // Caught on the sequence before the link: 1 then 3 is a hole, and
        // saying "an entry is missing" is more use than "this does not follow".
        assertEquals(
            Verdict.Failed(Fault.MISSING, 3, "e3", checked = 1, unchained = 0),
            AuditChain.verify(rows),
        )
    }

    @Test
    fun `slipping an entry in shows`() {
        val rows = sample().toMutableList()
        val forged = entry(
            2, "safety", "s9", "SIGN_OFF", "Sami", "Induction completed",
            occurredAt = 1_700_000_000_500, previousHash = rows[0].hash,
        )
        // Even hashed correctly for its own content, it cannot also be the
        // entry the real second one already committed to.
        val correct = forged.copy(hash = AuditChain.recompute(forged))
        rows[1] = correct
        assertEquals(correct.hash, AuditChain.recompute(correct))
        assertTrue(AuditChain.verify(rows) is Verdict.Failed)
    }

    @Test
    fun `re-signing the doctored entry does not save it`() {
        val rows = sample().toMutableList()
        val altered = rows[1].copy(summary = "Something else entirely")
        // Recompute so the entry is internally consistent again. The entry
        // after it still points at the hash the original had.
        rows[1] = altered.copy(hash = AuditChain.recompute(altered))
        assertEquals(
            Verdict.Failed(Fault.BROKEN_LINK, 3, "e3", checked = 2, unchained = 0),
            AuditChain.verify(rows),
        )
    }

    // Boundaries, where an honest system looks like a doctored one.

    @Test
    fun `a purge leaves the survivors verifying`() {
        // Retention removed entry one. The rest still chains; the first
        // survivor's previousHash has nothing to be checked against, which is
        // the one gap the PURGE row exists to explain.
        val rows = sample().drop(1)
        assertEquals(Verdict.Intact(checked = 2, unchained = 0), AuditChain.verify(rows))
    }

    @Test
    fun `entries written before the chain existed are reported, not failed`() {
        val legacy = entry(
            0, "project", "old", "CREATE", "Sami", "Before the upgrade",
            occurredAt = 1_600_000_000_000, previousHash = "",
        )
        val verdict = AuditChain.verify(listOf(legacy) + sample())
        assertEquals(Verdict.Intact(checked = 3, unchained = 1), verdict)
    }

    @Test
    fun `a trail that is entirely unverifiable says so rather than passing`() {
        val legacy = entry(
            0, "project", "old", "CREATE", "Sami", "Before the upgrade",
            occurredAt = 1_600_000_000_000, previousHash = "",
        )
        assertEquals(Verdict.Intact(checked = 0, unchained = 1), AuditChain.verify(listOf(legacy)))
    }

    // The encoding. A log is exactly where somebody puts the separator on purpose.

    @Test
    fun `text cannot be moved across a field boundary`() {
        val left = AuditChain.link(
            AuditChain.GENESIS, 1, "a;b", "c", "CREATE", null, "x", "y", null, 1,
        )
        val right = AuditChain.link(
            AuditChain.GENESIS, 1, "a", "b;c", "CREATE", null, "x", "y", null, 1,
        )
        assertNotEquals(left, right)
    }

    @Test
    fun `an absent payload and an empty one are different records`() {
        val absent = AuditChain.link(
            AuditChain.GENESIS, 1, "a", "b", "CREATE", null, "x", "y", null, 1,
        )
        val empty = AuditChain.link(
            AuditChain.GENESIS, 1, "a", "b", "CREATE", null, "x", "y", "", 1,
        )
        assertNotEquals(absent, empty)
    }

    @Test
    fun `a length prefix counts bytes, not characters`() {
        // "העקדה" is five characters and ten bytes. A prefix that said five
        // would leave a boundary an attacker could aim at.
        val hebrew = AuditChain.link(
            AuditChain.GENESIS, 1, "a", "b", "CREATE", null, "x", "העקדה", null, 1,
        )
        val latin = AuditChain.link(
            AuditChain.GENESIS, 1, "a", "b", "CREATE", null, "x", "abcde", null, 1,
        )
        assertNotEquals(hebrew, latin)
        assertEquals(64, hebrew.length)
    }

    @Test
    fun `a different predecessor gives a different hash`() {
        val after = AuditChain.link(
            "a".repeat(64), 1, "a", "b", "CREATE", null, "x", "y", null, 1,
        )
        val genesis = AuditChain.link(
            AuditChain.GENESIS, 1, "a", "b", "CREATE", null, "x", "y", null, 1,
        )
        assertNotEquals(after, genesis)
    }
}
