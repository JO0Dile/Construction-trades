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

    /** Adds certifications: the tickets people hold, and when they lapse. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_3_4.forEach(db::execSQL)
        }
    }

    /** Adds the plant register: kit the firm owns or hires. */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_4_5.forEach(db::execSQL)
        }
    }

    /** Adds purchase orders and their lines. */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_5_6.forEach(db::execSQL)
        }
    }

    /** Adds toolbox talks with their registers, and permits to work. */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_6_7.forEach(db::execSQL)
        }
    }

    /**
     * Adds the identity and induction fields to an account.
     *
     * Three ALTER TABLEs rather than a rebuild. Every one is nullable and has
     * no default, which is what lets it be added in place: a NOT NULL column
     * would need a DEFAULT that Room does not know about, and the schema check
     * on the next open would fail on every device that already has the app.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_7_8.forEach(db::execSQL)
        }
    }

    /**
     * Splits identity from membership.
     *
     * Everything that says what somebody may do moves out of the account row
     * and into its own table, because the same person is a site manager for
     * one firm and on the tools for another and the account row could only
     * hold one answer. Every existing account is backfilled with exactly the
     * membership it already had, so nobody's permissions change across the
     * upgrade — including people who had been removed, who get a membership
     * they have already left.
     *
     * Jobs gain a company at the same time. Without it, switching firms would
     * change your role and still show you the other firm's work, which is
     * worse than not being able to switch at all.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_8_9.forEach(db::execSQL)
            BACKFILL_8_9.forEach(db::execSQL)
        }
    }

    /** Adds snagging: the defects found on a job, and who checked them. */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_9_10.forEach(db::execSQL)
        }
    }

    /** Adds the daily site log — the יומן עבודה a site manager has to keep. */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_10_11.forEach(db::execSQL)
        }
    }

    /** Adds concrete pours and the delivery tickets that fill them. */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_11_12.forEach(db::execSQL)
        }
    }

    /** Adds the scaffold register and its seven-day inspections. */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_12_13.forEach(db::execSQL)
        }
    }

    /** Adds lift plans and the three crew roles every lift needs. */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            SQL_13_14.forEach(db::execSQL)
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
    )

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

    val SQL_3_4: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `certifications` (`id` TEXT NOT NULL, " +
            "`accountId` TEXT NOT NULL, `title` TEXT NOT NULL, `reference` TEXT, " +
            "`issuedOn` INTEGER, `expiresOn` INTEGER, `notes` TEXT, " +
            "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_certifications_accountId` " +
            "ON `certifications` (`accountId`)",
    )

    val SQL_4_5: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `equipment` (`id` TEXT NOT NULL, " +
            "`name` TEXT NOT NULL, `ownership` TEXT NOT NULL, `status` TEXT NOT NULL, " +
            "`serialNumber` TEXT, `supplierId` TEXT, `assignedProjectId` TEXT, " +
            "`hireRatePerDay` REAL, `hireStartedOn` INTEGER, `hireEndsOn` INTEGER, " +
            "`lastServicedOn` INTEGER, `serviceDueOn` INTEGER, `notes` TEXT, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_equipment_assignedProjectId` " +
            "ON `equipment` (`assignedProjectId`)",
        "CREATE INDEX IF NOT EXISTS `index_equipment_supplierId` " +
            "ON `equipment` (`supplierId`)",
    )

    val SQL_5_6: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `purchase_orders` (`id` TEXT NOT NULL, " +
            "`reference` TEXT NOT NULL, `projectId` TEXT, `supplierId` TEXT, " +
            "`supplierName` TEXT NOT NULL, `status` TEXT NOT NULL, " +
            "`orderedOn` INTEGER, `expectedOn` INTEGER, `notes` TEXT, " +
            "`createdBy` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_purchase_orders_projectId` " +
            "ON `purchase_orders` (`projectId`)",
        "CREATE INDEX IF NOT EXISTS `index_purchase_orders_supplierId` " +
            "ON `purchase_orders` (`supplierId`)",
        "CREATE INDEX IF NOT EXISTS `index_purchase_orders_status` " +
            "ON `purchase_orders` (`status`)",
        "CREATE TABLE IF NOT EXISTS `purchase_order_lines` (`id` TEXT NOT NULL, " +
            "`orderId` TEXT NOT NULL, `catalogItemId` TEXT, `inventoryItemId` TEXT, " +
            "`label` TEXT NOT NULL, `unit` TEXT NOT NULL, " +
            "`quantityOrdered` REAL NOT NULL, `quantityReceived` REAL NOT NULL, " +
            "`unitPrice` REAL NOT NULL, `sortOrder` INTEGER NOT NULL, " +
            "PRIMARY KEY(`id`), FOREIGN KEY(`orderId`) REFERENCES " +
            "`purchase_orders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_purchase_order_lines_orderId` " +
            "ON `purchase_order_lines` (`orderId`)",
        "CREATE INDEX IF NOT EXISTS `index_purchase_order_lines_catalogItemId` " +
            "ON `purchase_order_lines` (`catalogItemId`)",
    )

    val SQL_6_7: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `toolbox_talks` (`id` TEXT NOT NULL, " +
            "`projectId` TEXT, `topic` TEXT NOT NULL, `notes` TEXT, " +
            "`heldAt` INTEGER NOT NULL, `presenterName` TEXT NOT NULL, " +
            "`presenterId` TEXT, `signatureStrokes` TEXT, " +
            "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_toolbox_talks_projectId` " +
            "ON `toolbox_talks` (`projectId`)",
        "CREATE INDEX IF NOT EXISTS `index_toolbox_talks_heldAt` " +
            "ON `toolbox_talks` (`heldAt`)",
        "CREATE TABLE IF NOT EXISTS `toolbox_talk_attendees` (`id` TEXT NOT NULL, " +
            "`talkId` TEXT NOT NULL, `accountId` TEXT, `name` TEXT NOT NULL, " +
            "`signatureStrokes` TEXT, `signedAt` INTEGER, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`talkId`) REFERENCES `toolbox_talks`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_toolbox_talk_attendees_talkId` " +
            "ON `toolbox_talk_attendees` (`talkId`)",
        "CREATE INDEX IF NOT EXISTS `index_toolbox_talk_attendees_accountId` " +
            "ON `toolbox_talk_attendees` (`accountId`)",
        "CREATE TABLE IF NOT EXISTS `permits` (`id` TEXT NOT NULL, " +
            "`reference` TEXT NOT NULL, `projectId` TEXT, `type` TEXT NOT NULL, " +
            "`status` TEXT NOT NULL, `description` TEXT NOT NULL, `location` TEXT, " +
            "`issuedByName` TEXT, `issuedToName` TEXT NOT NULL, `validFrom` INTEGER, " +
            "`validTo` INTEGER, `issuedAt` INTEGER, `issuerSignature` TEXT, " +
            "`closedAt` INTEGER, `closedByName` TEXT, `closeNotes` TEXT, " +
            "`createdBy` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_permits_projectId` ON `permits` (`projectId`)",
        "CREATE INDEX IF NOT EXISTS `index_permits_status` ON `permits` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_permits_validTo` ON `permits` (`validTo`)",
        "CREATE TABLE IF NOT EXISTS `permit_precautions` (`id` TEXT NOT NULL, " +
            "`permitId` TEXT NOT NULL, `label` TEXT NOT NULL, " +
            "`checked` INTEGER NOT NULL, `checkedAt` INTEGER, " +
            "`sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`permitId`) REFERENCES `permits`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_permit_precautions_permitId` " +
            "ON `permit_precautions` (`permitId`)",
    )

    val SQL_7_8: List<String> = listOf(
        "ALTER TABLE `accounts` ADD COLUMN `username` TEXT",
        "ALTER TABLE `accounts` ADD COLUMN `idNumber` TEXT",
        "ALTER TABLE `accounts` ADD COLUMN `inductionSignature` TEXT",
        "ALTER TABLE `accounts` ADD COLUMN `inductedAt` INTEGER",
    )

    val SQL_8_9: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `memberships` (`id` TEXT NOT NULL, " +
            "`accountId` TEXT NOT NULL, `companyId` TEXT, `role` TEXT NOT NULL, " +
            "`joinedAt` INTEGER NOT NULL, `leftAt` INTEGER, PRIMARY KEY(`id`), " +
            "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_memberships_accountId` " +
            "ON `memberships` (`accountId`)",
        "CREATE INDEX IF NOT EXISTS `index_memberships_companyId` " +
            "ON `memberships` (`companyId`)",
        "ALTER TABLE `projects` ADD COLUMN `companyId` TEXT",
        "CREATE INDEX IF NOT EXISTS `index_projects_companyId` ON `projects` (`companyId`)",
    )

    /**
     * Data, not schema — kept apart from [SQL_8_9] so the CI check compares
     * only the statements that decide whether Room will open the database.
     *
     * A removed account becomes a membership with the same leftAt, rather than
     * no membership at all: the audit trail names those people, and a register
     * that cannot say what somebody's role was in March is not a register.
     */
    val BACKFILL_8_9: List<String> = listOf(
        "INSERT INTO memberships (id, accountId, companyId, role, joinedAt, leftAt) " +
            "SELECT lower(hex(randomblob(16))), id, companyId, role, createdAt, deletedAt " +
            "FROM accounts",
        // One company per device until now, so every job belongs to it. A
        // personal account has none, and its jobs stay company-less.
        "UPDATE projects SET companyId = (SELECT id FROM companies LIMIT 1)",
    )

    val SQL_9_10: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `snags` (`id` TEXT NOT NULL, " +
            "`reference` TEXT NOT NULL, `projectId` TEXT NOT NULL, " +
            "`title` TEXT NOT NULL, `location` TEXT, `tradeId` TEXT, " +
            "`assignedToName` TEXT, `status` TEXT NOT NULL, " +
            "`blocksHandover` INTEGER NOT NULL, `raisedByName` TEXT NOT NULL, " +
            "`raisedAt` INTEGER NOT NULL, `dueOn` INTEGER, `fixedByName` TEXT, " +
            "`fixedAt` INTEGER, `verifiedByName` TEXT, `verifiedAt` INTEGER, " +
            "`verifyNotes` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_snags_projectId` ON `snags` (`projectId`)",
        "CREATE INDEX IF NOT EXISTS `index_snags_status` ON `snags` (`status`)",
        "CREATE INDEX IF NOT EXISTS `index_snags_dueOn` ON `snags` (`dueOn`)",
    )

    val SQL_10_11: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `daily_logs` (`id` TEXT NOT NULL, " +
            "`projectId` TEXT NOT NULL, `logDate` INTEGER NOT NULL, " +
            "`status` TEXT NOT NULL, `weather` TEXT, `workforceCount` INTEGER, " +
            "`notes` TEXT, `preparedByName` TEXT NOT NULL, `preparedById` TEXT, " +
            "`signature` TEXT, `signedAt` INTEGER, `createdAt` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_logs_projectId_logDate` " +
            "ON `daily_logs` (`projectId`, `logDate`)",
        "CREATE INDEX IF NOT EXISTS `index_daily_logs_logDate` ON `daily_logs` (`logDate`)",
    )

    val SQL_11_12: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `concrete_pours` (`id` TEXT NOT NULL, " +
            "`reference` TEXT NOT NULL, `projectId` TEXT NOT NULL, " +
            "`element` TEXT NOT NULL, `mixDesign` TEXT, `orderedVolume` REAL, " +
            "`supplierName` TEXT, `temperatureCelsius` REAL, " +
            "`startedAt` INTEGER NOT NULL, `completedAt` INTEGER, `notes` TEXT, " +
            "`recordedByName` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
            "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_concrete_pours_projectId` " +
            "ON `concrete_pours` (`projectId`)",
        "CREATE INDEX IF NOT EXISTS `index_concrete_pours_startedAt` " +
            "ON `concrete_pours` (`startedAt`)",
        "CREATE TABLE IF NOT EXISTS `concrete_tickets` (`id` TEXT NOT NULL, " +
            "`pourId` TEXT NOT NULL, `ticketNumber` TEXT, `truckNumber` TEXT, " +
            "`volume` REAL NOT NULL, `dispatchedAt` INTEGER NOT NULL, " +
            "`arrivedAt` INTEGER, `dischargedAt` INTEGER, `slumpCm` REAL, " +
            "`rejected` INTEGER NOT NULL, `rejectionReason` TEXT, " +
            "`recordedByName` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`id`), FOREIGN KEY(`pourId`) REFERENCES " +
            "`concrete_pours`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_concrete_tickets_pourId` " +
            "ON `concrete_tickets` (`pourId`)",
    )

    val SQL_12_13: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `scaffolds` (`id` TEXT NOT NULL, " +
            "`reference` TEXT NOT NULL, `projectId` TEXT NOT NULL, " +
            "`location` TEXT NOT NULL, `type` TEXT, `erectedByName` TEXT, " +
            "`erectedAt` INTEGER, `lastInspectedAt` INTEGER, " +
            "`lastInspectionPassed` INTEGER NOT NULL, `lastInspectorName` TEXT, " +
            "`alteredAt` INTEGER, `dismantledAt` INTEGER, `notes` TEXT, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_scaffolds_projectId` " +
            "ON `scaffolds` (`projectId`)",
        "CREATE TABLE IF NOT EXISTS `scaffold_inspections` (`id` TEXT NOT NULL, " +
            "`scaffoldId` TEXT NOT NULL, `inspectedAt` INTEGER NOT NULL, " +
            "`inspectorName` TEXT NOT NULL, `passed` INTEGER NOT NULL, " +
            "`defects` TEXT, `actionTaken` TEXT, `reason` TEXT NOT NULL, " +
            "`signature` TEXT, `createdAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`id`), FOREIGN KEY(`scaffoldId`) REFERENCES " +
            "`scaffolds`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_scaffold_inspections_scaffoldId` " +
            "ON `scaffold_inspections` (`scaffoldId`)",
        "CREATE INDEX IF NOT EXISTS `index_scaffold_inspections_inspectedAt` " +
            "ON `scaffold_inspections` (`inspectedAt`)",
    )

    val SQL_13_14: List<String> = listOf(
        "CREATE TABLE IF NOT EXISTS `lift_plans` (`id` TEXT NOT NULL, " +
            "`reference` TEXT NOT NULL, `projectId` TEXT NOT NULL, " +
            "`description` TEXT NOT NULL, `loadWeightKg` REAL, " +
            "`riggingWeightKg` REAL, `applianceName` TEXT, `applianceId` TEXT, " +
            "`applianceCertificateExpiresOn` INTEGER, " +
            "`applianceCertificateRequired` INTEGER NOT NULL, " +
            "`radiusMetres` REAL, `capacityAtRadiusKg` REAL, " +
            "`windLimitKmh` REAL NOT NULL, `windSpeedKmh` REAL, " +
            "`plannedFor` INTEGER, `approvedByName` TEXT, `approvedAt` INTEGER, " +
            "`completedAt` INTEGER, `notes` TEXT, `createdByName` TEXT NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`id`))",
        "CREATE INDEX IF NOT EXISTS `index_lift_plans_projectId` " +
            "ON `lift_plans` (`projectId`)",
        "CREATE INDEX IF NOT EXISTS `index_lift_plans_plannedFor` " +
            "ON `lift_plans` (`plannedFor`)",
        "CREATE TABLE IF NOT EXISTS `lift_crew` (`id` TEXT NOT NULL, " +
            "`planId` TEXT NOT NULL, `role` TEXT NOT NULL, `accountId` TEXT, " +
            "`name` TEXT NOT NULL, `certificationId` TEXT, " +
            "`certificateReference` TEXT, `certificateExpiresOn` INTEGER, " +
            "`createdAt` INTEGER NOT NULL, " +
            "PRIMARY KEY(`id`), FOREIGN KEY(`planId`) REFERENCES " +
            "`lift_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_lift_crew_planId` " +
            "ON `lift_crew` (`planId`)",
    )
}
