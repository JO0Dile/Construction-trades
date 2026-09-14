package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One payment application, up or down the chain.
 *
 * Every money column here is **cumulative** — what the work is worth in total
 * at this point in the job, not what was done since the last one. That is how
 * applications are actually written, and storing them any other way means
 * re-deriving the running total on every read and getting it wrong once.
 *
 * [claimedGrossToDate] is what was asked for; [certifiedGrossToDate] is what
 * somebody agreed to. Keeping both is the point of the row: the difference is
 * the conversation, and a single "amount" column would erase it.
 *
 * The retention rate and limit are copied onto each application rather than
 * read from the job, because they are terms of a contract. Changing a job's
 * default must not silently restate what was certified last March.
 */
@Entity(
    tableName = "payment_applications",
    indices = [Index("projectId"), Index("dueOn")],
)
data class PaymentApplicationEntity(
    @PrimaryKey val id: String,
    /** Site form: PA-001. Applications are numbered and referred to by number. */
    val reference: String,
    val projectId: String,
    /**
     * Which way the money goes.
     *
     * RECEIVABLE: we are claiming from the client. PAYABLE: a subcontractor is
     * claiming from us. The arithmetic is identical and the anxiety is not.
     */
    val direction: String,
    /** The other party: the client, or the subcontractor claiming. */
    val partyName: String,
    /** Sequence within the job, as written on the application. */
    val applicationNumber: Int,
    /** The period this application covers, for the paperwork. */
    val periodEndsOn: Long?,
    /** DRAFT, SUBMITTED, CERTIFIED, PAID or REJECTED — see core.money.Payments. */
    val status: String,
    /** What was asked for, for all work to date. */
    val claimedGrossToDate: Double,
    /** What was agreed, for all work to date. Null until somebody assesses it. */
    val certifiedGrossToDate: Double?,
    /** Net of retention, paid out across every application before this one. */
    val previouslyPaidNet: Double,
    val retentionRate: Double,
    val retentionLimit: Double,
    /** IMMEDIATE, SHOTEF_30, SHOTEF_60, SHOTEF_90 or SHOTEF_120. */
    val terms: String,
    val submittedAt: Long?,
    val certifiedAt: Long?,
    val certifiedByName: String?,
    /**
     * When the money falls due: the end of the month the invoice falls in, plus
     * the term. Stored rather than derived so an application keeps the date it
     * was given when the terms on the job later change.
     */
    val dueOn: Long?,
    val paidAt: Long?,
    val notes: String?,
    val createdByName: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One work package on one payment application.
 *
 * An application says a job is worth 11,000 to date. Without these rows,
 * nobody — not the surveyor certifying it, not an auditor two years later,
 * not the crew leader who raised it — can say what the 11,000 is made of.
 * The packages are in the database and the figure is in the database and
 * nothing joins them, which is the shape of every dispute that ends in a
 * spreadsheet built from memory.
 *
 * [title] and [amount] are copied rather than read back from the package.
 * A package can be amended after it has been claimed, and a line that
 * recomputed itself would restate an application somebody already certified
 * — the same reason the retention terms are copied onto the application
 * rather than read from the job.
 *
 * There is no foreign key to the assignment on purpose. Nothing in this app
 * deletes a work package, but a line that vanished with its package would
 * take the explanation of a certified figure with it, which is precisely
 * what these rows exist to prevent.
 */
@Entity(
    tableName = "payment_application_lines",
    indices = [Index("applicationId"), Index("assignmentId")],
)
data class PaymentApplicationLineEntity(
    @PrimaryKey val id: String,
    val applicationId: String,
    val assignmentId: String,
    /** What the package was called when it was claimed. */
    val title: String,
    /** What it was worth when it was claimed. */
    val amount: Double,
    val createdAt: Long,
)
