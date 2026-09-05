package il.co.tradesmanager.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations.
 *
 * There is no destructive fallback anywhere in this app. A tradesperson's
 * quantities, job sheets and signed-off safety checklists are a record, and an
 * upgrade that silently empties them is worse than an upgrade that fails.
 *
 * Every statement here has to match, character for character, the DDL Room
 * generates from the entities — Room re-validates the schema on the first open
 * after a migration and throws if a column type or an index name differs. That
 * is checked in CI by `tools/check-migration.py`, which reads the generated
 * `AppDatabase_Impl` and compares it against the SQL below, because getting it
 * wrong means a crash on launch for everyone who already has the app.
 */
object Migrations {

    /** Adds accounts and companies. Purely additive: nothing existing is touched. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_1_2.forEach(db::execSQL)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)

    /** Exposed so the CI check can read the same strings the migration runs. */
    val SQL_1_2: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `companies` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
            "`registrationNumber` TEXT, `createdAt` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `accounts` (`id` TEXT NOT NULL, " +
            "`displayName` TEXT NOT NULL, `companyId` TEXT, `role` TEXT NOT NULL, " +
            "`passcodeHash` TEXT, `passcodeSalt` TEXT, `createdAt` INTEGER NOT NULL, " +
            "`lastSignInAt` INTEGER, `deletedAt` INTEGER, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_accounts_companyId` ON `accounts` (`companyId`)",
    )
}
