package il.co.tradesmanager.data.local

import android.content.Context
import androidx.room.Room
import il.co.tradesmanager.core.security.DatabaseKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Builds the local database, encrypted with SQLCipher where the device can
 * support it.
 *
 * Encryption is not optional in the product sense — the Israeli Privacy
 * Protection Law obligations that come with holding site personnel data are
 * why it exists — but it must not be a way to lose a day's work: if the native
 * SQLCipher library cannot load on a particular device, the app opens the
 * database unencrypted and reports it, rather than refusing to start on a
 * building site with no signal.
 */
object DatabaseFactory {

    data class Result(val database: AppDatabase, val encrypted: Boolean)

    fun create(context: Context, encrypt: Boolean = true): Result {
        val builder = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // No destructive fallback, deliberately. Quantities, job sheets and
            // signed-off checklists are a record; an upgrade that quietly
            // empties them is worse than one that refuses to open.
            .addMigrations(*Migrations.ALL)

        if (!encrypt) return Result(builder.build(), encrypted = false)

        return runCatching {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKey(context).passphrase()
            builder.openHelperFactory(SupportOpenHelperFactory(passphrase)).build()
        }.fold(
            onSuccess = { Result(it, encrypted = true) },
            onFailure = { Result(builder.build(), encrypted = false) },
        )
    }
}
