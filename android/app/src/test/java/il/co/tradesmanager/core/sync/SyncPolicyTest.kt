package il.co.tradesmanager.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Getting the transport wrong costs a retry. Getting these wrong destroys a
 * signed record and nobody finds out until they go looking for it.
 */
class SyncPolicyTest {

    private val now = 1_760_000_000_000L
    private val phoneA = Revision(now, "device-a")
    private val phoneB = Revision(now, "device-b")

    private fun job(revision: Revision, sealed: Boolean = false) =
        SyncPolicy.Version("projects", revision, sealed)

    private fun permit(revision: Revision, sealed: Boolean = true) =
        SyncPolicy.Version("permits", revision, sealed)

    @Test
    fun `the later write wins`() {
        val older = job(Revision(now - 1000, "device-a"))
        val newer = job(Revision(now, "device-b"))
        assertEquals(SyncPolicy.Resolution.TakeRemote, SyncPolicy.resolve(older, newer))
        assertEquals(SyncPolicy.Resolution.KeepLocal, SyncPolicy.resolve(newer, older))
    }

    @Test
    fun `a tie is broken the same way on both devices`() {
        // Two phones offline on the same site, editing the same job in the same
        // millisecond. If the answer depended on who was asking, the row would
        // flip back and forth forever.
        val fromA = SyncPolicy.resolve(job(phoneA), job(phoneB))
        val fromB = SyncPolicy.resolve(job(phoneB), job(phoneA))
        assertEquals(SyncPolicy.Resolution.TakeRemote, fromA)
        assertEquals(SyncPolicy.Resolution.KeepLocal, fromB)
        // Both devices end up holding device-b's row.
    }

    @Test
    fun `a signature beats a later edit made in ignorance of it`() {
        val signedYesterday = permit(Revision(now - 86_400_000, "device-a"))
        val editedJustNow = permit(Revision(now, "device-b"), sealed = false)
        assertEquals(
            SyncPolicy.Resolution.KeepLocal,
            SyncPolicy.resolve(signedYesterday, editedJustNow),
        )
        assertEquals(
            SyncPolicy.Resolution.TakeRemote,
            SyncPolicy.resolve(editedJustNow, signedYesterday),
        )
    }

    @Test
    fun `the same signature synced twice is not a conflict`() {
        val same = permit(phoneA)
        assertEquals(SyncPolicy.Resolution.KeepLocal, SyncPolicy.resolve(same, same))
    }

    @Test
    fun `two different signatures on one row need a person`() {
        // Two people signed the same permit on two phones. No rule may pick.
        assertEquals(
            SyncPolicy.Resolution.NeedsAttention,
            SyncPolicy.resolve(permit(phoneA), permit(phoneB)),
        )
    }

    @Test
    fun `things that record what happened are kept, never merged away`() {
        val a = SyncPolicy.Version("stock_movements", phoneA)
        val b = SyncPolicy.Version("stock_movements", phoneB)
        assertEquals(SyncPolicy.Resolution.KeepBoth, SyncPolicy.resolve(a, b))

        val auditA = SyncPolicy.Version("audit_log", phoneA)
        val auditB = SyncPolicy.Version("audit_log", phoneB)
        assertEquals(SyncPolicy.Resolution.KeepBoth, SyncPolicy.resolve(auditA, auditB))
    }

    @Test
    fun `anything not named as append-only is treated as mutable`() {
        // The costs are not symmetrical: a row wrongly called append-only stops
        // accepting corrections, which is worse than resolving one carefully.
        assertEquals(SyncPolicy.Kind.MUTABLE, SyncPolicy.kindOf("projects", isSealed = false))
        assertEquals(SyncPolicy.Kind.MUTABLE, SyncPolicy.kindOf("something_new", isSealed = false))
        assertEquals(
            SyncPolicy.Kind.APPEND_ONLY,
            SyncPolicy.kindOf("stock_movements", isSealed = false),
        )
    }

    @Test
    fun `sealing outranks being append-only`() {
        assertEquals(SyncPolicy.Kind.SEALED, SyncPolicy.kindOf("incidents", isSealed = true))
    }

    @Test
    fun `a phone with its clock set wrong is spotted rather than obeyed`() {
        // The failure that quietly wrecks last-writer-wins: one wrong clock
        // wins every argument on every device from then on.
        val nextYear = Revision(now + 365L * 86_400_000, "device-a")
        assertTrue(Revisions.isFromTheFuture(nextYear, now))
        assertFalse(Revisions.isFromTheFuture(Revision(now, "device-a"), now))
        // An hour of ordinary drift is not a broken clock.
        assertFalse(Revisions.isFromTheFuture(Revision(now + 3_600_000, "device-a"), now))
    }

    @Test
    fun `laterOf agrees with the ordering`() {
        assertEquals(phoneB, Revisions.laterOf(phoneA, phoneB))
        assertEquals(phoneB, Revisions.laterOf(phoneB, phoneA))
        val newer = Revision(now + 1, "device-a")
        assertEquals(newer, Revisions.laterOf(newer, phoneB))
    }
}
