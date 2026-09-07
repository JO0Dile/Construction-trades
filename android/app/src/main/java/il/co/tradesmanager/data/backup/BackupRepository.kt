package il.co.tradesmanager.data.backup

import android.content.Context
import android.net.Uri
import il.co.tradesmanager.BuildConfig
import il.co.tradesmanager.core.security.Backup
import il.co.tradesmanager.data.local.AppDatabase
import il.co.tradesmanager.data.local.DATABASE_VERSION
import il.co.tradesmanager.data.repository.AuditTrail
import java.io.DataInputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Taking the record off the phone, and putting it back.
 *
 * The rules are in [Backup] and the file format is in [BackupArchive]. What is
 * here is the awkward part neither of those should know about: the database on
 * this device may or may not be encrypted with a key that belongs to this
 * device, and a backup has to work either way and restore onto a phone that
 * has never seen this one.
 *
 * So the archive always holds a **plaintext** SQLite file, locked by the
 * archive's own encryption under the person's passphrase. Where the device
 * database is encrypted, that plaintext copy is made through SQLCipher's
 * `sqlcipher_export`; where it is not, it is the file itself after a
 * checkpoint. One kind of file goes in, and the difference is dealt with once,
 * here.
 */
class BackupRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val audit: AuditTrail,
    private val databaseIsEncrypted: Boolean,
) {

    private val photoDir: File get() = File(context.filesDir, "photos")

    private val workDir: File
        get() = File(context.cacheDir, "backup").apply { mkdirs() }

    /* -------------------------------------------------------------- taking */

    /**
     * Writes a backup to [target], which is a document the user chose.
     *
     * The working copy lives in the cache directory and is deleted on the way
     * out, whatever happened. It is a plaintext copy of everything the app
     * knows about everybody, and leaving one behind because a write failed
     * would undo the reason the database is encrypted at all.
     */
    suspend fun backUp(target: Uri, passphrase: CharArray, actorName: String): Result<Int> =
        withContext(Dispatchers.IO) {
            val plain = File(workDir, "plain.db")
            runCatching {
                plain.delete()
                copyDatabase(plain)
                val media = photoDir.listFiles()?.toList().orEmpty()
                val header = Backup.Header(
                    magic = Backup.MAGIC,
                    formatVersion = Backup.FORMAT_VERSION,
                    schemaVersion = DATABASE_VERSION,
                    appVersion = BuildConfig.VERSION_NAME,
                    createdAt = System.currentTimeMillis(),
                    mediaCount = media.size,
                )
                context.contentResolver.openOutputStream(target)?.use { out ->
                    BackupArchive.write(out, header, passphrase, plain, media)
                } ?: error("could not open the file for writing")

                // Recorded like everything else. A backup is a copy of every
                // ID number and wage in the firm leaving the device, which is
                // exactly the kind of thing a trail exists to have a line
                // about.
                audit.record(
                    entityType = ENTITY,
                    entityId = header.createdAt.toString(),
                    action = AuditTrail.Action.EXPORT,
                    actorName = actorName,
                    summary = "Backed up, ${media.size} photographs and videos",
                )
                media.size
            }.also { plain.delete() }
        }

    /**
     * A plaintext copy of the live database.
     *
     * The checkpoint matters in both branches: without it the newest writes
     * are still in the write-ahead log and a copy of the main file alone is a
     * backup missing this morning.
     */
    private fun copyDatabase(target: File) {
        val db = database.openHelper.writableDatabase
        db.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        if (databaseIsEncrypted) {
            target.delete()
            db.execSQL("ATTACH DATABASE ? AS plaintext KEY ''", arrayOf(target.absolutePath))
            db.query("SELECT sqlcipher_export('plaintext')").use { it.moveToFirst() }
            // Carried across by hand: sqlcipher_export copies the tables and
            // not the schema version, and Room reads that number to decide
            // which migrations to run. A copy that arrives claiming version
            // zero gets migrated from the beginning over rows that are
            // already current.
            db.execSQL("PRAGMA plaintext.user_version = $DATABASE_VERSION")
            db.execSQL("DETACH DATABASE plaintext")
        } else {
            context.getDatabasePath(AppDatabase.NAME).copyTo(target, overwrite = true)
        }
    }

    /* ------------------------------------------------------------ putting back */

    /** What a file says it is, before anybody is asked for a passphrase. */
    data class Inspection(val header: Backup.Header?, val blocker: Backup.Blocker?) {
        val ok: Boolean get() = header != null && blocker == null
    }

    /**
     * Reads the header only.
     *
     * Separate from the restore so the screen can say "this is not one of
     * ours" or "this is from a newer version" without asking for anything.
     * Telling somebody who picked the wrong file that their passphrase is
     * wrong sends them looking for the wrong problem.
     */
    suspend fun inspect(source: Uri): Inspection = withContext(Dispatchers.IO) {
        val header = runCatching {
            context.contentResolver.openInputStream(source)?.use { stream ->
                BackupArchive.readHeader(DataInputStream(stream))
            }
        }.getOrNull()
        if (header == null) {
            Inspection(null, Backup.Blocker.NOT_A_BACKUP)
        } else {
            Inspection(header, Backup.blocksRestore(header, DATABASE_VERSION))
        }
    }

    /**
     * Unpacks a backup and leaves it ready for the next launch.
     *
     * Nothing on the device changes here. The staged copy is checked and
     * swapped in by [StagedRestore.apply] before the database is opened, when
     * nothing else is holding it — see the reasoning there.
     */
    suspend fun stageRestore(source: Uri, passphrase: CharArray): Result<Backup.Header> =
        withContext(Dispatchers.IO) {
            val unpackDir = File(workDir, "incoming")
            runCatching {
                unpackDir.deleteRecursively()
                context.contentResolver.openInputStream(source)?.use { stream ->
                    val data = DataInputStream(stream)
                    val header = BackupArchive.readHeader(data)
                        ?: error("not a backup")
                    Backup.blocksRestore(header, DATABASE_VERSION)?.let {
                        error("cannot restore: $it")
                    }
                    // Throws on a wrong passphrase: GCM fails its tag rather
                    // than handing back plausible rubbish.
                    val unpacked = BackupArchive.read(data, passphrase, unpackDir)
                    val db = unpacked.database ?: error("the archive holds no database")
                    StagedRestore.stage(
                        context = context,
                        database = db,
                        media = unpacked.media,
                        takenAt = header.createdAt,
                        schemaVersion = header.schemaVersion,
                    )
                    header
                } ?: error("could not open the file for reading")
            }.also { unpackDir.deleteRecursively() }
        }

    /**
     * Writes the entry that explains a restore, if one happened at launch.
     *
     * After the restore and not before, so it chains onto the restored head.
     * The trail is tamper-evident and a restore rewinds it, which to anybody
     * verifying it later is indistinguishable from entries having been cut
     * out. This line is the difference between a restore and an attack, and it
     * is no use sitting in the part of the trail that was replaced.
     *
     * Does nothing when no restore happened, and clears the note either way so
     * a second launch does not record it twice.
     */
    suspend fun recordRestoreIfAny(actorName: String) = withContext(Dispatchers.IO) {
        val note = StagedRestore.noteFile(context)
        if (!note.isFile) return@withContext
        val parts = runCatching { note.readText().trim().split(" ") }.getOrDefault(emptyList())
        val takenAt = parts.getOrNull(0)?.toLongOrNull()
        val schema = parts.getOrNull(1)?.toIntOrNull()
        note.delete()
        if (takenAt == null || schema == null) return@withContext
        audit.record(
            entityType = ENTITY,
            entityId = takenAt.toString(),
            action = AuditTrail.Action.CREATE,
            actorName = actorName,
            summary = Backup.restoreSummary(takenAt, schema),
        )
    }

    /** Changes their mind before the restart. Nothing had been replaced yet. */
    fun cancelPendingRestore() = StagedRestore.discard(context)

    private companion object {
        const val ENTITY = "backup"
    }
}
