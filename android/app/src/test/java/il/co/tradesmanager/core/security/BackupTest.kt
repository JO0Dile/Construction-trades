package il.co.tradesmanager.core.security

import il.co.tradesmanager.core.security.Backup.Blocker
import il.co.tradesmanager.core.security.Backup.Header
import il.co.tradesmanager.core.security.Backup.PassphraseFault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The promises a backup makes.
 *
 * Every one of these is a sentence somebody would say on the day they need it,
 * and the day they need it is the day the phone went into the concrete.
 */
class BackupTest {

    private fun header(
        magic: String = Backup.MAGIC,
        formatVersion: Int = Backup.FORMAT_VERSION,
        schemaVersion: Int = 20,
        appVersion: String = "0.12.1",
        createdAt: Long = 1_700_000_000_000,
        mediaCount: Int = 12,
    ) = Header(magic, formatVersion, schemaVersion, appVersion, createdAt, mediaCount)

    @Test
    fun `a backup from this app restores onto this app`() {
        assertNull(Backup.blocksRestore(header(schemaVersion = 28), 28))
        assertTrue(Backup.canRestore(header(schemaVersion = 28), 28))
    }

    @Test
    fun `a backup from before the last update restores, and is migrated on the way in`() {
        // The ordinary case. Somebody backed up in March, updated in June,
        // dropped the phone in July.
        assertNull(Backup.blocksRestore(header(schemaVersion = 20), 28))
        assertTrue(Backup.needsMigrating(header(schemaVersion = 20), 28))
        assertFalse(Backup.needsMigrating(header(schemaVersion = 28), 28))
    }

    @Test
    fun `a backup from a newer app is refused rather than attempted`() {
        // Migrations run forward only. A restore that quietly dropped the
        // columns it did not recognise would look exactly like one that
        // worked, and the person would find out months later.
        assertEquals(
            Blocker.FROM_A_NEWER_APP,
            Backup.blocksRestore(header(schemaVersion = 31), 28),
        )
        assertEquals(
            Blocker.FROM_A_NEWER_APP,
            Backup.blocksRestore(header(formatVersion = Backup.FORMAT_VERSION + 1), 28),
        )
    }

    @Test
    fun `the wrong file is named as the wrong file, before any passphrase is tried`() {
        // Otherwise somebody who picked a photograph out of the file browser
        // is told their passphrase is wrong and goes looking for the wrong
        // problem.
        assertEquals(Blocker.NOT_A_BACKUP, Backup.blocksRestore(header(magic = "PK"), 28))
        assertEquals(Blocker.NOT_A_BACKUP, Backup.blocksRestore(header(magic = ""), 28))
    }

    @Test
    fun `a header with nonsense versions is not a backup`() {
        assertEquals(Blocker.NOT_A_BACKUP, Backup.blocksRestore(header(formatVersion = 0), 28))
        assertEquals(Blocker.NOT_A_BACKUP, Backup.blocksRestore(header(schemaVersion = 0), 28))
        assertEquals(Blocker.NOT_A_BACKUP, Backup.blocksRestore(header(schemaVersion = -1), 28))
    }

    @Test
    fun `nothing from before version one is migratable`() {
        assertFalse(Backup.needsMigrating(header(schemaVersion = 0), 28))
    }

    @Test
    fun `a good passphrase is accepted`() {
        assertNull(Backup.blocksPassphrase("concrete-tuesday", "concrete-tuesday"))
        assertTrue(Backup.passphraseOk("concrete-tuesday", "concrete-tuesday"))
    }

    @Test
    fun `a passcode is not a passphrase`() {
        // Four digits is ten thousand guesses against a file somebody can take
        // away and attack for as long as they like.
        assertEquals(PassphraseFault.TOO_SHORT, Backup.blocksPassphrase("4821", "4821"))
        assertEquals(
            PassphraseFault.TOO_SHORT,
            Backup.blocksPassphrase("elevenchars", "elevenchars"),
        )
    }

    @Test
    fun `exactly the minimum is enough`() {
        val twelve = "abcdef123456"
        assertEquals(Backup.MIN_PASSPHRASE, twelve.length)
        assertNull(Backup.blocksPassphrase(twelve, twelve))
    }

    @Test
    fun `length without variety is not strength`() {
        assertEquals(
            PassphraseFault.TOO_FEW_CHARACTERS,
            Backup.blocksPassphrase("aaaaaaaaaaaa", "aaaaaaaaaaaa"),
        )
        assertEquals(
            PassphraseFault.TOO_FEW_CHARACTERS,
            Backup.blocksPassphrase("ababababababab", "ababababababab"),
        )
    }

    @Test
    fun `a typo is caught while it can still be fixed`() {
        // There is no way to check this later and nobody to reset it. A typo
        // is discovered on the worst possible day, so it is asked for twice.
        assertEquals(
            PassphraseFault.DOES_NOT_MATCH,
            Backup.blocksPassphrase("concrete-tuesday", "concrete-tuesdya"),
        )
    }

    @Test
    fun `too short outranks not matching`() {
        // One message at a time, and the first thing to fix is the one that
        // would still be wrong after the other was fixed.
        assertEquals(PassphraseFault.TOO_SHORT, Backup.blocksPassphrase("short", "different"))
    }

    @Test
    fun `the file name carries its date, so a file listing answers the first question`() {
        assertEquals("tradesmanager-2026-09-07.tmbak", Backup.fileName(2026, 9, 7))
        assertEquals("tradesmanager-2026-12-31.tmbak", Backup.fileName(2026, 12, 31))
    }

    @Test
    fun `the file name sorts by date as text`() {
        // Zero-padded so a file browser's alphabetical order is chronological
        // order, which is the only ordering some of them offer.
        val names = listOf(
            Backup.fileName(2026, 12, 1),
            Backup.fileName(2026, 2, 28),
            Backup.fileName(2025, 11, 3),
        )
        assertEquals(listOf(names[2], names[1], names[0]), names.sorted())
    }

    @Test
    fun `a restore says what it restored, inside the chain it rewound`() {
        // The trail is tamper-evident, and a restore rewinds it, which to
        // anybody verifying it later is indistinguishable from entries having
        // been cut out. This line is what tells the difference.
        val summary = Backup.restoreSummary(takenAt = 1_700_000_000_000, schemaVersion = 20)
        assertTrue(summary.contains("1700000000000"))
        assertTrue(summary.contains("20"))
    }

    @Test
    fun `the rounds are not a token gesture`() {
        // The only thing between a stolen archive and everybody's ID number.
        assertTrue(Backup.KEY_ROUNDS >= 100_000)
    }
}
