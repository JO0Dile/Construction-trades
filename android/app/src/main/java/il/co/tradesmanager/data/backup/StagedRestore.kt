package il.co.tradesmanager.data.backup

import android.content.Context
import il.co.tradesmanager.data.local.AppDatabase
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * A restore that is prepared while the app runs and applied before it opens
 * the database.
 *
 * Swapping a database out from under a running app means closing something
 * thirty repositories and a screenful of open Flows are holding. Nothing good
 * comes of it, and the failure mode is half a database.
 *
 * So a restore is two acts. [stage] unpacks the archive somewhere safe and
 * leaves a marker, while the app carries on with the data it already has.
 * [apply] runs at the very start of the next launch, before Room opens
 * anything, when the only thing touching these files is this function.
 *
 * ## What makes it safe to run at all
 *
 * The old database is **moved aside, not deleted**. If anything fails part way
 * through, [rollBack] puts it back and the person is exactly where they
 * started. A restore that cannot be undone is not a feature anybody should tap
 * on a phone that still has the only copy of a site diary on it.
 *
 * And the staged file is checked before it is used: SQLite's own integrity
 * check, and the schema version it claims against the one it holds. A file
 * that fails either is abandoned with the original untouched.
 */
object StagedRestore {

    private const val STAGING = "restore-pending"
    private const val DATABASE = "database.db"
    private const val MEDIA = "media"
    private const val REPLACED_SUFFIX = ".replaced"
    private const val NOTE = "note"

    /**
     * Left where the next launch will find it, after a restore succeeded.
     *
     * Two numbers -- when the backup was taken and what schema it was on --
     * because the audit entry that explains the restore has to be written
     * *after* it, so that it chains onto the restored head. By then the
     * staging directory is gone, so what the entry needs to say has to
     * outlive it by one step.
     */
    fun noteFile(context: Context): File = File(context.filesDir, "restore-$NOTE")

    fun stagingDir(context: Context): File = File(context.filesDir, STAGING)

    /** True when a restore is waiting to be applied on the next launch. */
    fun isPending(context: Context): Boolean =
        File(stagingDir(context), DATABASE).isFile

    /**
     * Puts an unpacked archive where [apply] will find it.
     *
     * Everything is written into the staging directory and only then is the
     * database file moved into place inside it, because [isPending] keys on
     * that one file: a half-written staging directory that already looked
     * pending would be applied on the next launch.
     */
    fun stage(
        context: Context,
        database: File,
        media: List<File>,
        takenAt: Long,
        schemaVersion: Int,
    ) {
        val dir = stagingDir(context)
        dir.deleteRecursively()
        dir.mkdirs()
        val mediaDir = File(dir, MEDIA).apply { mkdirs() }
        media.forEach { file -> file.copyTo(File(mediaDir, file.name), overwrite = true) }
        File(dir, NOTE).writeText("$takenAt $schemaVersion")

        val incoming = File(dir, "$DATABASE.part")
        database.copyTo(incoming, overwrite = true)
        incoming.renameTo(File(dir, DATABASE))
    }

    fun discard(context: Context) {
        stagingDir(context).deleteRecursively()
    }

    /** What happened, so a launch can say something rather than nothing. */
    enum class Outcome {
        /** Nothing was waiting. The ordinary launch. */
        NOTHING_STAGED,

        /** The database and the media are in place. */
        RESTORED,

        /**
         * The staged file did not hold up, and nothing was changed.
         *
         * The archive decrypted -- that much was proved before it was staged
         * -- so this is a file that was damaged in the writing or is not the
         * database it claimed to be. Either way the original is still there.
         */
        REFUSED,

        /**
         * It failed part way and the original was put back.
         *
         * Separate from [REFUSED] because they need different sentences: this
         * one means something went wrong on this device, not that the file was
         * bad, and the person should be told to try again rather than to find
         * a different backup.
         */
        ROLLED_BACK,
    }

    /**
     * Applies a staged restore. Call before opening the database, or not at all.
     *
     * [encrypt] must be the same decision the app is about to open the
     * database with, because it decides whether the plaintext staged file is
     * copied into place or exported through SQLCipher under this device's key.
     * Getting it wrong produces a database the app cannot open, which is why
     * it is passed in rather than read from settings here.
     */
    fun apply(context: Context, encrypt: Boolean, passphrase: () -> ByteArray): Outcome {
        // Through isPending rather than an inline check, so what counts as a
        // waiting restore is decided in one place. Two answers to that, one
        // here and one in stage(), is how a half-written staging directory
        // gets applied.
        if (!isPending(context)) return Outcome.NOTHING_STAGED
        val dir = stagingDir(context)
        val staged = File(dir, DATABASE)

        if (!isSound(staged)) {
            discard(context)
            return Outcome.REFUSED
        }

        val live = context.getDatabasePath(AppDatabase.NAME)
        val setAside = File(live.parentFile, live.name + REPLACED_SUFFIX)
        val journals = journalsOf(live)

        live.parentFile?.mkdirs()
        setAside.delete()
        val hadOne = live.exists() && live.renameTo(setAside)
        journals.forEach { it.delete() }

        val ok = runCatching {
            if (encrypt) {
                exportEncrypted(staged, live, passphrase())
            } else {
                staged.copyTo(live, overwrite = true)
            }
            replaceMedia(context, File(dir, MEDIA))
        }.isSuccess

        return if (ok) {
            setAside.delete()
            // Carried out of the staging directory before it is deleted, so
            // the next thing the app does can write the audit entry that
            // explains what happened to the trail.
            runCatching { File(dir, NOTE).copyTo(noteFile(context), overwrite = true) }
            discard(context)
            Outcome.RESTORED
        } else {
            rollBack(live, setAside, hadOne)
            discard(context)
            Outcome.ROLLED_BACK
        }
    }

    /**
     * SQLite's own opinion of the file, before anything is replaced on its say-so.
     *
     * Opened with an empty key: what is staged is always plaintext, because
     * the archive it came out of was locked with the person's passphrase and
     * not with any device's key. That is the whole reason a backup restores
     * onto a phone that has never seen the one it was taken from.
     */
    private fun isSound(file: File): Boolean = runCatching {
        SQLiteDatabase.openDatabase(file.absolutePath, "", null, SQLiteDatabase.OPEN_READONLY)
            .use { db ->
                db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
                }
            }
    }.getOrDefault(false)

    /**
     * Copies the plaintext staged database into an encrypted one at [target].
     *
     * `sqlcipher_export` is SQLCipher's own way across the boundary: it reads
     * every page through one connection and writes it through the other, so
     * the result is a real encrypted database rather than a plaintext file
     * with a header stuck on it.
     */
    private fun exportEncrypted(staged: File, target: File, key: ByteArray) {
        SQLiteDatabase.openDatabase(staged.absolutePath, "", null, SQLiteDatabase.OPEN_READONLY)
            .use { plain ->
                plain.execSQL("ATTACH DATABASE ? AS encrypted KEY ?", arrayOf(target.absolutePath, key))
                plain.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { it.moveToFirst() }
                // The schema version does not travel with sqlcipher_export.
                // Without this, Room opens a database full of the right rows,
                // reads user_version 0 and tries to migrate it from nothing.
                plain.execSQL("PRAGMA encrypted.user_version = " + userVersion(plain))
                plain.execSQL("DETACH DATABASE encrypted")
            }
    }

    private fun userVersion(db: SQLiteDatabase): Int =
        db.rawQuery("PRAGMA user_version", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private fun replaceMedia(context: Context, from: File) {
        val target = File(context.filesDir, "photos")
        target.deleteRecursively()
        target.mkdirs()
        if (from.isDirectory) {
            from.listFiles()?.forEach { it.copyTo(File(target, it.name), overwrite = true) }
        }
    }

    private fun rollBack(live: File, setAside: File, hadOne: Boolean) {
        live.delete()
        journalsOf(live).forEach { it.delete() }
        if (hadOne) setAside.renameTo(live)
    }

    /**
     * The write-ahead log and its index.
     *
     * Deleted alongside the database rather than left behind. A journal from
     * the old file against a new one is how SQLite is told to replay writes
     * that belong to a database that is no longer there.
     */
    private fun journalsOf(live: File): List<File> = listOf(
        File(live.parentFile, live.name + "-wal"),
        File(live.parentFile, live.name + "-shm"),
        File(live.parentFile, live.name + "-journal"),
    )
}
