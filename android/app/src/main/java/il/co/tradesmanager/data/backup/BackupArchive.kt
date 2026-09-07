package il.co.tradesmanager.data.backup

import il.co.tradesmanager.core.security.Backup
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json

/**
 * The backup file itself: what is in it, and how it is locked.
 *
 * ```
 *   magic          16 bytes, ASCII, so a wrong file is named as one
 *   headerLength    4 bytes
 *   header          UTF-8 JSON, in the clear
 *   salt           16 bytes
 *   iv             12 bytes
 *   ciphertext     AES-256-GCM over a zip of the database and the media
 * ```
 *
 * The header is deliberately outside the encryption. It carries the schema
 * version, the app version, the date and how many photographs are inside — no
 * personal data, nothing about anybody — and having it readable is what lets
 * the app say "this is from a newer version" or "this is not one of ours"
 * *before* asking for a passphrase. Refusing a wrong file by telling somebody
 * their passphrase is wrong sends them looking for the wrong problem.
 *
 * GCM is the whole integrity story: a file that has been altered by a byte
 * fails the tag and does not decrypt at all. There is no separate checksum,
 * because a checksum an attacker can recompute is decoration.
 */
object BackupArchive {

    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    /** Entry names inside the zip. Fixed, so a reader knows what to look for. */
    const val DATABASE_ENTRY = "database.db"
    const val MEDIA_PREFIX = "media/"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Reads the header and leaves the stream positioned at the salt.
     *
     * Returns null when this is not one of ours at all -- a stream too short to
     * hold a header, or one whose magic does not match. The caller turns that
     * into [Backup.Blocker.NOT_A_BACKUP]; it is not an exception, because
     * picking the wrong file out of a file browser is an ordinary thing to do.
     */
    fun readHeader(input: DataInputStream): Backup.Header? {
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
            json.decodeFromString<StoredHeader>(String(bytes, Charsets.UTF_8)).toHeader()
        }.getOrNull()
    }

    /**
     * Writes everything: header, salt, iv, then the encrypted zip.
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
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }

        val stream = DataOutputStream(output)
        stream.write(Backup.MAGIC.toByteArray(Charsets.US_ASCII))
        val headerBytes = json.encodeToString(StoredHeader.of(header))
            .toByteArray(Charsets.UTF_8)
        stream.writeInt(headerBytes.size)
        stream.write(headerBytes)
        stream.write(salt)
        stream.write(iv)
        stream.flush()

        val cipher = cipher(Cipher.ENCRYPT_MODE, passphrase, salt, iv)
        // CipherOutputStream writes the GCM tag when it is closed, and closing
        // it closes the zip beneath it in the right order. Nothing here may
        // close `output` itself -- that belongs to the caller, who opened it
        // from a content URI and has to be the one to let it go.
        ZipOutputStream(CipherOutputStream(NonClosing(stream), cipher)).use { zip ->
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
     * Throws when the passphrase is wrong: GCM fails its tag and the stream
     * throws rather than handing back plausible rubbish, which is the property
     * that makes "wrong passphrase" and "damaged file" the same safe answer.
     */
    fun read(input: DataInputStream, passphrase: CharArray, into: File): Unpacked {
        val salt = ByteArray(SALT_BYTES).also { input.readFully(it) }
        val iv = ByteArray(IV_BYTES).also { input.readFully(it) }
        val cipher = cipher(Cipher.DECRYPT_MODE, passphrase, salt, iv)

        into.mkdirs()
        val mediaDir = File(into, "media").apply { mkdirs() }
        var database: File? = null
        val media = mutableListOf<File>()

        ZipInputStream(CipherInputStream(input, cipher)).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == DATABASE_ENTRY ->
                        database = File(into, DATABASE_ENTRY).also { zip.copyInto(it) }
                    name.startsWith(MEDIA_PREFIX) -> {
                        // Only the file name, never the path from the archive.
                        // A zip entry called "media/../../databases/x" would
                        // otherwise write outside the directory it was given,
                        // and this file is read from wherever the user points
                        // it -- including from somewhere they did not write.
                        val leaf = File(name).name
                        if (leaf.isNotBlank() && leaf != "." && leaf != "..") {
                            media += File(mediaDir, leaf).also { zip.copyInto(it) }
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return Unpacked(database, media)
    }

    data class Unpacked(val database: File?, val media: List<File>)

    private fun ZipInputStream.copyInto(target: File) {
        target.outputStream().use { copyTo(it) }
    }

    private fun cipher(mode: Int, passphrase: CharArray, salt: ByteArray, iv: ByteArray): Cipher {
        val spec = PBEKeySpec(passphrase, salt, Backup.KEY_ROUNDS, KEY_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
        spec.clearPassword()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        }
    }

    /**
     * Passes writes through and refuses to close what it wraps.
     *
     * CipherOutputStream closes the stream under it, which here would close
     * the content URI the caller opened. The caller has to be the one to close
     * that, in its own `use`, or a failure part way through leaks the handle.
     */
    private class NonClosing(private val target: OutputStream) : OutputStream() {
        override fun write(b: Int) = target.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = target.write(b, off, len)
        override fun flush() = target.flush()
        override fun close() = target.flush()
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
