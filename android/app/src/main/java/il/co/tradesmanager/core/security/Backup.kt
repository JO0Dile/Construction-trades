package il.co.tradesmanager.core.security

/**
 * Taking the site's record off the phone, and putting it back.
 *
 * This app keeps everything on the device and nothing on a server. That is the
 * point on a site with no signal, and it has a consequence nobody notices
 * until it happens: **a lost phone is a lost site diary.** Every permit, every
 * signed induction, every concrete ticket, every photograph somebody took to
 * prove a wall was right before it was covered — gone, on the one product
 * whose entire pitch is that you can prove what happened.
 *
 * Android's own cloud backup is off (`android:allowBackup="false"`), and
 * deliberately: this database holds ID numbers, photographs of faces,
 * signatures and wages, and handing that to an automatic uploader is not a
 * decision to make on a user's behalf. So the backup is a thing the person
 * does, to a file they choose, in a place they choose.
 *
 * ## Why it is not a copy of the database file
 *
 * The database is encrypted with a random key held in this device's Keystore
 * — see [DatabaseKey]. Copy the file to another phone and it is noise, and a
 * factory reset destroys the key along with everything else, so a file copy
 * would not survive the two events a backup exists for.
 *
 * So the archive is re-encrypted under a passphrase the person chooses. That
 * moves the risk somewhere honest: a backup is exactly as recoverable as the
 * passphrase is remembered, and there is no reset link, because there is
 * nobody holding a key to reset it with.
 */
object Backup {

    /**
     * The archive format, not the app version.
     *
     * Written into every file so that a version of this app years from now can
     * tell what it is looking at before it tries to read it. Bumped only when
     * the container changes shape, which should be almost never.
     *
     * 1 sealed the whole archive as one AES-GCM message, which cannot be read
     * back without holding all of it in memory at once -- so a backup big
     * enough to matter could not be restored at all. 2 seals it in frames.
     * Files written as 1 are still read; see `data.backup.BackupArchive`.
     */
    const val FORMAT_VERSION = 2

    /** Marks the file as ours before anything tries to decrypt it. */
    const val MAGIC = "TRADESMGR-BACKUP"

    /**
     * The shortest passphrase this will accept.
     *
     * Twelve, not four. The passcode that unlocks the app is a lock on a phone
     * somebody is holding, where a wrong guess costs a delay; this is a lock on
     * a file that can be copied, taken away and attacked for as long as anybody
     * likes. Four digits is ten thousand guesses, which is not a lock.
     */
    const val MIN_PASSPHRASE = 12

    /**
     * Rounds of PBKDF2 used to turn the passphrase into a key.
     *
     * High on purpose, and the cost is paid once per backup rather than on
     * every screen. It is the only thing standing between a stolen archive and
     * a list of everybody's ID number.
     */
    const val KEY_ROUNDS = 210_000

    /**
     * How many different characters a passphrase must contain.
     *
     * Length alone is not strength: "aaaaaaaaaaaa" clears twelve characters
     * and is one guess. Six is low enough that a real passphrase never trips
     * on it and high enough to catch a held-down key.
     */
    const val DISTINCT_CHARACTERS = 6

    /** What a file says about itself, before anybody trusts a byte of it. */
    data class Header(
        val magic: String,
        val formatVersion: Int,
        /** The Room schema the database was on when this was written. */
        val schemaVersion: Int,
        /** The app's own version name, for a human reading the file list. */
        val appVersion: String,
        val createdAt: Long,
        /** How many photographs and videos are inside, so a restore can say. */
        val mediaCount: Int,
    )

    /** Null when this file may be restored onto an app at [schemaVersion]. */
    fun blocksRestore(header: Header, schemaVersion: Int): Blocker? = when {
        header.magic != MAGIC -> Blocker.NOT_A_BACKUP
        header.formatVersion <= 0 -> Blocker.NOT_A_BACKUP
        header.schemaVersion <= 0 -> Blocker.NOT_A_BACKUP
        header.formatVersion > FORMAT_VERSION -> Blocker.FROM_A_NEWER_APP
        header.schemaVersion > schemaVersion -> Blocker.FROM_A_NEWER_APP
        else -> null
    }

    enum class Blocker {
        /**
         * Not one of ours, or damaged.
         *
         * Said before the passphrase is tried, so somebody who picked the
         * wrong file is told that rather than being told their passphrase is
         * wrong, which would send them looking for the wrong problem.
         */
        NOT_A_BACKUP,

        /**
         * Written by a newer version of the app than this one.
         *
         * Refused rather than attempted. Migrations run forward only; there is
         * no path from a schema this app has never seen back to the one it
         * has, and a restore that dropped the columns it did not recognise
         * would look like it worked.
         */
        FROM_A_NEWER_APP,
    }

    fun canRestore(header: Header, schemaVersion: Int): Boolean =
        blocksRestore(header, schemaVersion) == null

    /**
     * Whether a backup from [header] will need migrating on the way in.
     *
     * Not a warning — this is the ordinary case for anybody restoring a backup
     * taken before their last update, and Room runs the migrations when the
     * restored file is opened. It is here so the screen can say so, because
     * "this backup is from an older version" is a sentence people want to see
     * acknowledged rather than discover.
     */
    fun needsMigrating(header: Header, schemaVersion: Int): Boolean =
        header.schemaVersion in 1 until schemaVersion

    /** Null when [passphrase] may be used; otherwise why not. */
    fun blocksPassphrase(passphrase: String, repeated: String): PassphraseFault? = when {
        passphrase.length < MIN_PASSPHRASE -> PassphraseFault.TOO_SHORT
        passphrase != repeated -> PassphraseFault.DOES_NOT_MATCH
        passphrase.toSet().size < DISTINCT_CHARACTERS -> PassphraseFault.TOO_FEW_CHARACTERS
        else -> null
    }

    enum class PassphraseFault {
        TOO_SHORT,

        /**
         * Typed twice and the two do not agree.
         *
         * Asked for twice because there is no way to check it later. Every
         * other password in this app can be reset by somebody; this one is the
         * only thing that can open the file, and a typo in it is discovered
         * on the worst possible day.
         */
        DOES_NOT_MATCH,

        /** "aaaaaaaaaaaa" is twelve characters and one guess. */
        TOO_FEW_CHARACTERS,
    }

    fun passphraseOk(passphrase: String, repeated: String): Boolean =
        blocksPassphrase(passphrase, repeated) == null

    /**
     * What the file is called.
     *
     * Dated, because the first question anybody asks of a backup is how old it
     * is, and the second is whether there is a newer one. Both should be
     * answerable from a file listing without opening anything.
     */
    fun fileName(year: Int, month: Int, day: Int): String =
        "tradesmanager-%04d-%02d-%02d.%s".format(year, month, day, EXTENSION)

    const val EXTENSION = "tmbak"

    /**
     * The summary written into the audit trail *after* a restore.
     *
     * After, not before, so the entry chains onto the restored head rather
     * than onto a head that is about to be replaced. This matters more than it
     * reads: the trail is tamper-evident, and a restore rewinds it — which,
     * to anybody verifying it later, is indistinguishable from somebody having
     * cut entries out. The entry is what tells the difference between a
     * restore and an attack, so it has to be inside the chain it explains.
     */
    fun restoreSummary(takenAt: Long, schemaVersion: Int): String =
        "Restored from a backup taken at $takenAt, schema $schemaVersion"
}
