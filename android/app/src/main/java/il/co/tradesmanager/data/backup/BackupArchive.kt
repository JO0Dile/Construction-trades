package il.co.tradesmanager.data.backup

import il.co.tradesmanager.core.security.Backup
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The backup file itself: what is in it, and how it is locked.
 *
 * ```
 *   magic          16 bytes, ASCII, so a wrong file is named as one
 *   headerLength    4 bytes
 *   header          UTF-8 JSON, in the clear
 *   salt           16 bytes
 *   noncePrefix     7 bytes
 *   frames          64 KiB of payload each, sealed one at a time
 * ```
 *
 * The header is deliberately outside the encryption. It carries the schema
 * version, the app version, the date and how many photographs are inside — no
 * personal data, nothing about anybody — and having it readable is what lets
 * the app say "this is from a newer version" or "this is not one of ours"
 * *before* asking for a passphrase. Refusing a wrong file by telling somebody
 * their passphrase is wrong sends them looking for the wrong problem. Being in
 * the clear is not the same as being unprotected: every frame is sealed with
 * the header as associated data, so altering it breaks the whole file.
 *
 * ### Why the payload is in frames
 *
 * Format 1 sealed the whole zip as one AES-GCM message and read it back
 * through `CipherInputStream`. That does not work, for a reason that only
 * shows up on a real site's worth of photographs: GCM must not hand back
 * plaintext it has not authenticated yet, so the provider buffers **the entire
 * message in memory** and releases it in one piece at the end. Measured on a
 * 64 MB payload, `update()` returned nothing at all and `doFinal()` returned
 * all 64 MB at once. A backup bigger than the heap therefore cannot be
 * restored — and the backups that matter are the big ones, because they belong
 * to the sites that have been running longest.
 *
 * So the payload is cut into 64 KiB frames, each sealed on its own. A frame is
 * authenticated and released as it is read, and nothing bigger than one frame
 * is ever held. What keeps that safe is the nonce: seven random bytes for the
 * file, then the frame's number, then a byte saying whether it is the last.
 * The number stops frames being swapped round or repeated. The last-frame flag
 * stops frames being cut off the end — which is the attack that matters here,
 * because a backup silently missing everything after March is worse than one
 * that fails to open.
 *
 * Format 1 files are still read, since the app wrote them and refusing to open
 * one would be the thing this feature exists to prevent. They are subject to
 * the memory limit above, which is a fault of that format and not something
 * the reader can fix.
 */
object BackupArchive {

    private const val KEY_BITS = 256

    /** Entry names inside the zip. Fixed, so a reader knows what to look for. */
    const val DATABASE_ENTRY = "database.db"
    const val MEDIA_PREFIX = "media/"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * A backup file with its lid off: what it says it is, and the bytes it
     * said it in.
     *
     * The raw bytes are kept because they are the associated data every frame
     * is sealed with, and re-encoding the parsed header would not reliably
     * reproduce them byte for byte.
     */
    class Opening(val header: Backup.Header, internal val headerBytes: ByteArray)

    /**
     * Reads the header and leaves the stream positioned at the salt.
     *
     * Returns null when this is not one of ours at all -- a stream too short to
     * hold a header, or one whose magic does not match. The caller turns that
     * into [Backup.Blocker.NOT_A_BACKUP]; it is not an exception, because
     * picking the wrong file out of a file browser is an ordinary thing to do.
     */
    fun open(input: DataInputStream): Opening? {
        val magic = ByteArray(Backup.MAGIC.length)
        return runCatching {
            input.readFully(magic)
            if (String(magic, Charsets.US_ASCII) != Backup.MAGIC) return null
            val length = input.readInt()
            // A length nobody could have written. Guards against a file that
            // happens to start with our magic and then goes wrong, which would
            // otherwise try to allocate whatever the next four bytes said.
            if (length <= 0 || length > MAX_HEADER_BYTES) return null
            val bytes = ByteArray(length)
            input.readFully(bytes)
            val header = json.decodeFromString<StoredHeader>(String(bytes, Charsets.UTF_8)).toHeader()
            Opening(header, bytes)
        }.getOrNull()
    }

    /**
     * Writes everything: header, salt, nonce prefix, then the sealed frames.
     *
     * [database] is a plaintext SQLite file already exported out of whatever
     * the device holds -- see BackupRepository, which is where the difference
     * between an encrypted and an unencrypted device database is dealt with.
     * By the time anything reaches here there is one kind of file.
     */
    fun write(
        output: OutputStream,
        header: Backup.Header,
        passphrase: CharArray,
        database: File,
        media: List<File>,
    ) {
        require(header.formatVersion == Backup.FORMAT_VERSION) {
            "this writes format ${Backup.FORMAT_VERSION}, not ${header.formatVersion}"
        }
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val prefix = ByteArray(NONCE_PREFIX_BYTES).also { random.nextBytes(it) }
        val headerBytes = json.encodeToString(StoredHeader.of(header))
            .toByteArray(Charsets.UTF_8)

        val stream = DataOutputStream(output)
        stream.write(Backup.MAGIC.toByteArray(Charsets.US_ASCII))
        stream.writeInt(headerBytes.size)
        stream.write(headerBytes)
        stream.write(salt)
        stream.write(prefix)
        stream.flush()

        // Nothing here closes `output`: it belongs to the caller, who opened it
        // from a content URI and has to be the one to let it go. Closing the
        // zip closes the frames beneath it, which seals the last one.
        val frames = FrameOutput(stream, keyOf(passphrase, salt), prefix, headerBytes)
        ZipOutputStream(frames).use { zip ->
            zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
            database.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            media.forEach { file ->
                zip.putNextEntry(ZipEntry(MEDIA_PREFIX + file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Unpacks into [into], and returns what was found.
     *
     * Throws when the passphrase is wrong, when the file has been altered, and
     * when the end of it has been removed. Nothing is returned in any of those
     * cases: a backup that opens half way is the failure this whole format is
     * shaped to avoid.
     */
    fun read(
        input: DataInputStream,
        opening: Opening,
        passphrase: CharArray,
        into: File,
    ): Unpacked {
        val salt = ByteArray(SALT_BYTES).also { input.readFully(it) }
        val key = keyOf(passphrase, salt)
        val payload: InputStream = when (opening.header.formatVersion) {
            FORMAT_WHOLE_FILE -> {
                val iv = ByteArray(NONCE_BYTES).also { input.readFully(it) }
                CipherInputStream(input, wholeFileCipher(key, iv))
            }
            FORMAT_FRAMED -> {
                val prefix = ByteArray(NONCE_PREFIX_BYTES).also { input.readFully(it) }
                FrameInput(input, key, prefix, opening.headerBytes)
            }
            else -> error("unsupported backup format ${opening.header.formatVersion}")
        }

        into.mkdirs()
        val mediaDir = File(into, "media").apply { mkdirs() }
        var database: File? = null
        val media = mutableListOf<File>()

        ZipInputStream(NonClosing(payload)).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == DATABASE_ENTRY ->
                        database = File(into, DATABASE_ENTRY).also { zip.copyInto(it) }
                    name.startsWith(MEDIA_PREFIX) -> {
                        val leaf = mediaLeaf(name)
                        if (leaf != null) {
                            media += File(mediaDir, leaf).also { zip.copyInto(it) }
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        // A zip reader stops at the central directory, several hundred bytes
        // short of the end of the file, so nothing above has necessarily
        // reached the last frame -- and the last frame is the only thing that
        // says the file ended where it was meant to. Reading the rest is what
        // refuses one somebody has cut short, and it has to happen before any
        // of what was just unpacked is used.
        payload.drainToEnd()

        return Unpacked(database, media)
    }

    data class Unpacked(val database: File?, val media: List<File>)

    /**
     * The name a media entry may be written under, or null for one to ignore.
     *
     * Only the leaf is ever taken, never the path the archive asked for. An
     * entry called `media/../../databases/app.db` would otherwise be written
     * outside the directory it was handed, and this file comes from wherever
     * the user pointed the picker -- including somewhere they did not write.
     *
     * Stripping to the leaf is the whole defence, and it is deliberately not a
     * check that the path "looks safe": a rule that decides which paths to
     * refuse has to be right about every way of spelling one, and a rule that
     * keeps only the last segment cannot be wrong about any of them.
     */
    internal fun mediaLeaf(name: String): String? {
        val leaf = File(name).name
        return if (leaf.isBlank() || leaf == "." || leaf == "..") null else leaf
    }

    private fun ZipInputStream.copyInto(target: File) {
        target.outputStream().use { copyTo(it) }
    }

    private fun InputStream.drainToEnd() {
        val scratch = ByteArray(8 * 1024)
        var count = read(scratch)
        while (count != -1) {
            count = read(scratch)
        }
    }

    private fun keyOf(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, Backup.KEY_ROUNDS, KEY_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
        spec.clearPassword()
        return key
    }

    /** Format 1: one GCM message over the whole zip. Read only — see above. */
    private fun wholeFileCipher(key: ByteArray, iv: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        }

    private const val MAX_HEADER_BYTES = 64 * 1024

    @kotlinx.serialization.Serializable
    private data class StoredHeader(
        val formatVersion: Int,
        val schemaVersion: Int,
        val appVersion: String,
        val createdAt: Long,
        val mediaCount: Int,
    ) {
        fun toHeader() = Backup.Header(
            magic = Backup.MAGIC,
            formatVersion = formatVersion,
            schemaVersion = schemaVersion,
            appVersion = appVersion,
            createdAt = createdAt,
            mediaCount = mediaCount,
        )

        companion object {
            fun of(header: Backup.Header) = StoredHeader(
                formatVersion = header.formatVersion,
                schemaVersion = header.schemaVersion,
                appVersion = header.appVersion,
                createdAt = header.createdAt,
                mediaCount = header.mediaCount,
            )
        }
    }
}

private const val SALT_BYTES = 16
private const val NONCE_BYTES = 12
private const val NONCE_PREFIX_BYTES = 7
private const val TAG_BYTES = 16
private const val TAG_BITS = 128

/**
 * How much payload one frame holds.
 *
 * The cost of a frame is 16 bytes of tag, so this trades memory against size:
 * 64 KiB is a quarter of a per-cent of overhead and a quarter of a megabyte
 * held while reading, which is nothing on any phone that can run the app.
 */
private const val FRAME_BYTES = 64 * 1024

private const val FORMAT_WHOLE_FILE = 1
private const val FORMAT_FRAMED = 2

/**
 * Points an already-made cipher at one frame.
 *
 * The nonce is the whole argument for why cutting a file into frames is
 * safe: seven bytes that belong to this file, the frame's own number, and
 * whether it is the last one. Every frame therefore gets a nonce no other
 * frame has, and a frame moved, repeated or removed is asked to open under
 * a nonce that is not the one it was sealed with, which fails.
 */
private fun Cipher.forFrame(
    mode: Int,
    key: ByteArray,
    prefix: ByteArray,
    index: Int,
    last: Boolean,
    aad: ByteArray,
): Cipher {
    val nonce = ByteArray(NONCE_BYTES)
    prefix.copyInto(nonce, 0, 0, NONCE_PREFIX_BYTES)
    nonce[7] = (index ushr 24).toByte()
    nonce[8] = (index ushr 16).toByte()
    nonce[9] = (index ushr 8).toByte()
    nonce[10] = index.toByte()
    nonce[11] = if (last) 1 else 0
    init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
    updateAAD(aad)
    return this
}

/**
 * Seals the payload one frame at a time.
 *
 * A full buffer is not written the moment it fills: it might turn out to
 * be the last frame, and the last frame is sealed under a different nonce.
 * So it is held until either more data arrives — which proves it was not
 * the last — or the stream is closed.
 */
private class FrameOutput(
    private val target: OutputStream,
    private val key: ByteArray,
    private val prefix: ByteArray,
    private val aad: ByteArray,
) : OutputStream() {

    private val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    private val buffer = ByteArray(FRAME_BYTES)
    private var count = 0
    private var index = 0
    private var closed = false

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) {
        var from = off
        var left = len
        while (left > 0) {
            if (count == FRAME_BYTES) emit(last = false)
            val take = minOf(left, FRAME_BYTES - count)
            b.copyInto(buffer, count, from, from + take)
            count += take
            from += take
            left -= take
        }
    }

    override fun flush() = target.flush()

    override fun close() {
        if (closed) return
        closed = true
        // Always at least one frame, even for an empty payload: the last
        // frame is the only thing that says the file ends here on purpose.
        emit(last = true)
        target.flush()
    }

    private fun emit(last: Boolean) {
        target.write(
            cipher.forFrame(Cipher.ENCRYPT_MODE, key, prefix, index, last, aad)
                .doFinal(buffer, 0, count),
        )
        index++
        count = 0
    }
}

/**
 * Opens one frame at a time, and refuses any that is not the frame that
 * belongs in this position.
 */
private class FrameInput(
    wrapped: InputStream,
    private val key: ByteArray,
    private val prefix: ByteArray,
    private val aad: ByteArray,
) : InputStream() {

    /** One byte of pushback: enough to ask whether another frame follows. */
    private val source = PushbackInputStream(wrapped, 1)
    private val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    private val sealed = ByteArray(FRAME_BYTES + TAG_BYTES)
    private var plain = ByteArray(0)
    private var offset = 0
    private var index = 0
    private var done = false

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (offset >= plain.size) {
            if (done) return -1
            fill()
        }
        val take = minOf(len, plain.size - offset)
        plain.copyInto(b, off, offset, offset + take)
        offset += take
        return take
    }

    /** The caller owns the stream underneath. Closing here would take it. */
    override fun close() = Unit

    private fun fill() {
        var got = 0
        while (got < sealed.size) {
            val read = source.read(sealed, got, sealed.size - got)
            if (read == -1) break
            got += read
        }
        // A short frame is the last one. A full frame is the last one only
        // if nothing follows it, which costs one byte to find out.
        val last = if (got < sealed.size) {
            true
        } else {
            val next = source.read()
            if (next == -1) true else { source.unread(next); false }
        }
        plain = cipher.forFrame(Cipher.DECRYPT_MODE, key, prefix, index, last, aad)
            .doFinal(sealed, 0, got)
        offset = 0
        index++
        if (last) done = true
    }
}

/**
 * Passes reads through and refuses to close what it wraps.
 *
 * The zip reader is finished several hundred bytes before the file is, and
 * closing the payload with it would lose the chance to read the rest —
 * which is where a file with its end removed is caught.
 */
private class NonClosing(private val source: InputStream) : InputStream() {
    override fun read(): Int = source.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = source.read(b, off, len)
    override fun close() = Unit
}
