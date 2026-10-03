package il.co.tradesmanager.data.backup

import il.co.tradesmanager.core.security.Backup
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The backup file, opened again.
 *
 * This is the only code in the app that can destroy what it was asked to
 * protect, and nothing exercised it: it was read carefully and shipped. The
 * format is plain JVM -- streams, zip, javax.crypto -- so it runs here, on
 * every push, instead of only on a phone somebody remembers to try.
 *
 * Every test is a sentence about the day the file is needed, and most of them
 * are about the file being wrong, because a backup that fails loudly is worth
 * having and one that fails quietly is worse than none.
 */
class BackupArchiveTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** One frame of payload, so sizes can be written either side of a boundary. */
    private val frame = 64 * 1024

    private fun passphrase() = "correct horse battery".toCharArray()

    private fun header(
        schemaVersion: Int = 28,
        appVersion: String = "0.13.1",
        createdAt: Long = 1_757_260_000_000,
        mediaCount: Int = 2,
    ) = Backup.Header(
        magic = Backup.MAGIC,
        formatVersion = Backup.FORMAT_VERSION,
        schemaVersion = schemaVersion,
        appVersion = appVersion,
        createdAt = createdAt,
        mediaCount = mediaCount,
    )

    private var made = 0

    private fun file(name: String, bytes: ByteArray): File =
        File(folder.newFolder("in${made++}"), name).apply { writeBytes(bytes) }

    /**
     * Bytes that do not compress, so a payload of a stated size stays that
     * size once zipped -- which is what makes "either side of a frame
     * boundary" mean anything. Photographs and a SQLCipher database are both
     * incompressible, so this is also the real case.
     */
    private fun blob(seed: Int, size: Int): ByteArray {
        var state = seed * 2_654_435_761L + 1
        return ByteArray(size) {
            state = state * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
            (state ushr 33).toByte()
        }
    }

    private fun write(
        header: Backup.Header = header(),
        database: File,
        media: List<File> = emptyList(),
        passphrase: CharArray = passphrase(),
    ): ByteArray = ByteArrayOutputStream().also { out ->
        BackupArchive.write(out, header, passphrase, database, media)
    }.toByteArray()

    private fun read(
        bytes: ByteArray,
        passphrase: CharArray = passphrase(),
    ): BackupArchive.Unpacked = DataInputStream(ByteArrayInputStream(bytes)).use { input ->
        val opening = BackupArchive.open(input) ?: error("not a backup")
        BackupArchive.read(input, opening, passphrase, folder.newFolder("out${made++}"))
    }

    private fun failureFrom(bytes: ByteArray, passphrase: CharArray = passphrase()): Throwable? =
        runCatching { read(bytes, passphrase) }.exceptionOrNull()

    @Test
    fun `what went in comes out, byte for byte`() {
        // The entire promise. Not "a file appeared" -- the same file.
        val database = file("database.db", blob(seed = 1, size = 40_000))
        val one = file("a.jpg", blob(seed = 7, size = 9_000))
        val two = file("b.mp4", blob(seed = 9, size = 30_000))

        val unpacked = read(write(database = database, media = listOf(one, two)))

        assertArrayEquals(database.readBytes(), unpacked.database?.readBytes())
        assertEquals(listOf("a.jpg", "b.mp4"), unpacked.media.map { it.name }.sorted())
        assertArrayEquals(one.readBytes(), unpacked.media.first { it.name == "a.jpg" }.readBytes())
        assertArrayEquals(two.readBytes(), unpacked.media.first { it.name == "b.mp4" }.readBytes())
    }

    @Test
    fun `a backup larger than one frame comes back whole`() {
        // The case format 1 could not do at all: the payload no longer has to
        // fit in memory, so it has to be right across frame boundaries. Sizes
        // are picked either side of one because that is where an off-by-one in
        // the framing would hide, and it would hide as silently missing data.
        listOf(1, frame - 1, frame, frame + 1, 2 * frame, 3 * frame + 77).forEach { size ->
            val database = file("database.db", blob(seed = size, size = size))

            val unpacked = read(write(database = database))

            assertArrayEquals(
                "a $size byte database did not come back the same",
                database.readBytes(),
                unpacked.database?.readBytes(),
            )
        }
    }

    @Test
    fun `a backup with no photographs is still a backup`() {
        val database = file("database.db", blob(seed = 3, size = 5_000))

        val unpacked = read(write(header = header(mediaCount = 0), database = database))

        assertArrayEquals(database.readBytes(), unpacked.database?.readBytes())
        assertTrue(unpacked.media.isEmpty())
    }

    @Test
    fun `the header is readable before anybody is asked for a passphrase`() {
        // The reason it sits outside the encryption: a file from a newer app,
        // or a file that is not ours at all, has to be named as that rather
        // than as a passphrase somebody is about to doubt.
        val bytes = write(
            header = header(schemaVersion = 20, appVersion = "0.11.0", mediaCount = 41),
            database = file("database.db", blob(seed = 5, size = 1_000)),
        )

        val opened = DataInputStream(ByteArrayInputStream(bytes)).use { BackupArchive.open(it) }

        assertEquals(Backup.MAGIC, opened?.header?.magic)
        assertEquals(Backup.FORMAT_VERSION, opened?.header?.formatVersion)
        assertEquals(20, opened?.header?.schemaVersion)
        assertEquals("0.11.0", opened?.header?.appVersion)
        assertEquals(1_757_260_000_000, opened?.header?.createdAt)
        assertEquals(41, opened?.header?.mediaCount)
    }

    @Test
    fun `a photograph picked by mistake is not a backup`() {
        // Ordinary: the file browser opens on the camera roll.
        val notOurs = ByteArray(4_000) { 0xFF.toByte() }

        assertNull(DataInputStream(ByteArrayInputStream(notOurs)).use { BackupArchive.open(it) })
    }

    @Test
    fun `a file too short to hold a header is not a backup`() {
        val bytes = write(database = file("database.db", blob(seed = 2, size = 1_000)))

        assertNull(DataInputStream(ByteArrayInputStream(bytes.copyOf(8))).use { BackupArchive.open(it) })
    }

    @Test
    fun `a header length nobody could have written is refused, not allocated`() {
        // Our magic followed by rubbish -- a truncated upload, a file that was
        // half written. Believing the next four bytes here would try to
        // allocate whatever they happened to say.
        val bytes = Backup.MAGIC.toByteArray(Charsets.US_ASCII) +
            byteArrayOf(0x7F, -1, -1, -1) + ByteArray(64)

        assertNull(DataInputStream(ByteArrayInputStream(bytes)).use { BackupArchive.open(it) })
    }

    @Test
    fun `the wrong passphrase gives nothing back, not plausible rubbish`() {
        val bytes = write(database = file("database.db", blob(seed = 4, size = 20_000)))

        assertTrue(
            "a wrong passphrase opened the file",
            failureFrom(bytes, "not the passphrase".toCharArray()) != null,
        )
    }

    @Test
    fun `a single altered byte fails the whole file`() {
        // No separate checksum: the tag on each frame is the integrity story,
        // and a checksum an attacker can recompute would be decoration.
        val bytes = write(database = file("database.db", blob(seed = 6, size = 3 * frame)))

        // Early, late, and in the middle -- one flip anywhere is the file.
        listOf(bytes.size / 4, bytes.size / 2, bytes.size - 40).forEach { at ->
            val altered = bytes.copyOf().also { it[at] = (it[at] + 1).toByte() }

            assertTrue("a byte altered at $at was accepted", failureFrom(altered) != null)
        }
    }

    @Test
    fun `a header altered after the fact fails the file it describes`() {
        // The header is in the clear so it can be read without a passphrase,
        // which is not the same as being unprotected: it is sealed into every
        // frame, so editing the schema version to make an old backup look new
        // breaks the file rather than steering the restore.
        val bytes = write(database = file("database.db", blob(seed = 11, size = 20_000)))
        val at = String(bytes, 0, 400, Charsets.ISO_8859_1).indexOf("\"schemaVersion\":28")
        assertTrue("the header was not found to alter", at > 0)
        val altered = bytes.copyOf().also {
            it[at + "\"schemaVersion\":2".length] = '7'.code.toByte()
        }

        // Still reads as a backup, and still says 27 -- and then will not open.
        val opened = DataInputStream(ByteArrayInputStream(altered)).use { BackupArchive.open(it) }
        assertEquals(27, opened?.header?.schemaVersion)
        assertTrue("an altered header was accepted", failureFrom(altered) != null)
    }

    @Test
    fun `a backup with its end cut off is refused, not quietly shortened`() {
        // The attack that matters, and the one a whole-file tag would have
        // caught only by accident: a zip reader stops at the central directory
        // and would report the entries it did find. A backup silently missing
        // everything after some date is worse than one that will not open.
        val database = file("database.db", blob(seed = 12, size = frame))
        val media = (1..4).map { file("photo$it.jpg", blob(seed = 20 + it, size = frame)) }
        val bytes = write(header = header(mediaCount = 4), database = database, media = media)

        listOf(1, frame, 2 * (frame + 16)).forEach { cut ->
            assertTrue(
                "a file $cut bytes short was accepted",
                failureFrom(bytes.copyOf(bytes.size - cut)) != null,
            )
        }
    }

    @Test
    fun `a backup that failed half way through will not open`() {
        // A photograph that has gone missing since it was listed, or a card
        // that fills up. The caller is told the backup failed -- but the file
        // it half wrote is sitting there looking like a backup, and the day it
        // is needed is not the day to find out it stops in March. Sealing
        // happens only on the way out of a write that finished, so a partial
        // file is refused exactly as a truncated one is.
        val database = file("database.db", blob(seed = 13, size = 2 * frame))
        val good = file("photo.jpg", blob(seed = 14, size = frame))
        val vanished = File(folder.newFolder("gone${made++}"), "vanished.jpg")

        val partial = ByteArrayOutputStream()
        val failure = runCatching {
            BackupArchive.write(
                partial,
                header(mediaCount = 2),
                passphrase(),
                database,
                listOf(good, vanished),
            )
        }.exceptionOrNull()

        assertTrue("writing a backup of a file that is not there should fail", failure != null)
        assertTrue("nothing was written before it failed", partial.size() > frame)
        assertTrue("a half-written backup opened", failureFrom(partial.toByteArray()) != null)
    }

    @Test
    fun `two backups of the same data are not the same file`() {
        // Salt and nonce prefix are drawn fresh every time. If either were
        // fixed, the same database under the same passphrase would produce the
        // same bytes -- and reusing a GCM nonce loses the key, not just the
        // file.
        val database = file("database.db", blob(seed = 8, size = 4_000))

        val first = write(database = database)
        val second = write(database = database)

        assertEquals(first.size, second.size)
        assertNotEquals(sealedPartOf(first), sealedPartOf(second))
    }

    /** Everything after the header: salt, nonce prefix and frames. */
    private fun sealedPartOf(bytes: ByteArray): List<Byte> {
        val headerLength = DataInputStream(ByteArrayInputStream(bytes)).use {
            it.skipBytes(Backup.MAGIC.length)
            it.readInt()
        }
        return bytes.drop(Backup.MAGIC.length + Int.SIZE_BYTES + headerLength)
    }

    @Test
    fun `a media entry cannot write outside the folder it was given`() {
        // The archive is opened from wherever the picker pointed, including
        // from somewhere the user did not write. Only the last segment of an
        // entry name is ever used.
        assertEquals("photo.jpg", BackupArchive.mediaLeaf("media/photo.jpg"))
        assertEquals("app.db", BackupArchive.mediaLeaf("media/../../databases/app.db"))
        assertEquals("passwd", BackupArchive.mediaLeaf("media//etc/passwd"))
        assertNull(BackupArchive.mediaLeaf("media/"))
        assertNull(BackupArchive.mediaLeaf("media/."))
        assertNull(BackupArchive.mediaLeaf("media/.."))
    }

    @Test
    fun `whatever a media entry is called, it stays one name`() {
        // The rule is not a list of paths to refuse -- that has to be right
        // about every way of spelling one. Keeping the leaf cannot be wrong
        // about any of them, so this asserts the property rather than cases.
        listOf(
            "media/../x", "media/../../../../x", "media/./x", "media//x",
            "media/a/b/c/x", "media/x", "media/....//x",
        ).forEach { name ->
            assertEquals("$name resolved to more than a file name", "x", BackupArchive.mediaLeaf(name))
        }
    }
}
