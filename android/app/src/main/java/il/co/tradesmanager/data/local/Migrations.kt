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

    /** Adds the Money lens: budget, costs, variations, invoices. Additive. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_2_3.forEach(db::execSQL)
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

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

    val SQL_2_3: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `job_budgets` (`projectId` TEXT NOT NULL, " +
            "`contractValue` REAL NOT NULL, `vatRate` REAL NOT NULL, " +
            "`currency` TEXT NOT NULL, `notes` TEXT, `updatedAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`projectId`))",
        "CREATE TABLE IF NOT EXISTS `cost_entries` (`id` TEXT NOT NULL, " +
            "`projectId` TEXT NOT NULL, `category` TEXT NOT NULL, " +
            "`description` TEXT NOT NULL, `amount` REAL NOT NULL, " +
            "`incurredOn` INTEGER NOT NULL, `supplierId` TEXT, " +
            "`supplierInvoiceRef` TEXT, `recordedBy` TEXT NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_cost_entries_projectId` " +
            "ON `cost_entries` (`projectId`)",
        "CREATE INDEX IF NOT EXISTS `index_cost_entries_supplierId` " +
            "ON `cost_entries` (`supplierId`)",
        "CREATE TABLE IF NOT EXISTS `variations` (`id` TEXT NOT NULL, " +
            "`projectId` TEXT NOT NULL, `reference` TEXT NOT NULL, " +
            "`title` TEXT NOT NULL, `amount` REAL NOT NULL, `status` TEXT NOT NULL, " +
            "`raisedOn` INTEGER NOT NULL, `decidedOn` INTEGER, " +
            "`raisedBy` TEXT NOT NULL, `notes` TEXT, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_variations_projectId` " +
            "ON `variations` (`projectId`)",
        "CREATE TABLE IF NOT EXISTS `invoices` (`id` TEXT NOT NULL, " +
            "`projectId` TEXT NOT NULL, `number` TEXT NOT NULL, `amount` REAL NOT NULL, " +
            "`vatRate` REAL NOT NULL, `issuedOn` INTEGER NOT NULL, `dueOn` INTEGER, " +
            "`paidOn` INTEGER, `status` TEXT NOT NULL, `notes` TEXT, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_invoices_projectId` ON `invoices` (`projectId`)",
    )
}
