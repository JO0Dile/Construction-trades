package il.co.tradesmanager.data.repository

import il.co.tradesmanager.core.money.Payments
import il.co.tradesmanager.data.local.dao.PaymentsDao
import il.co.tradesmanager.data.local.entity.PaymentApplicationEntity
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Payment applications: what has been claimed, what was agreed, and when the
 * money is due.
 *
 * The arithmetic lives in `core.money.Payments`, where it can be tested without
 * a database. This stores what that arithmetic reads and — importantly — works
 * out the running total a new application is measured against, so that nobody
 * has to type it in and get it wrong.
 */
class PaymentsRepository(
    private val dao: PaymentsDao,
    private val audit: AuditTrail,
) {

    /** Which way the money goes. */
    enum class Direction {
        /** We are claiming from the client. */
        RECEIVABLE,

        /** A subcontractor is claiming from us. */
        PAYABLE,
    }

    fun observeForProject(projectId: String): Flow<List<PaymentApplicationEntity>> =
        dao.observeForProject(projectId)

    fun observe(id: String): Flow<PaymentApplicationEntity?> = dao.observe(id)

    /**
     * Raises the next application in a sequence.
     *
     * The number and the running total are both worked out here rather than
     * typed. An application whose "previously paid" figure is entered by hand
     * is an application that pays for last month's work twice the first time
     * somebody is in a hurry.
     */
    suspend fun raise(
        projectId: String,
        direction: Direction,
        partyName: String,
        claimedGrossToDate: Double,
        contractSum: Double,
        retentionRate: Double = Payments.DEFAULT_RETENTION,
        retentionLimit: Double = Payments.DEFAULT_RETENTION_LIMIT,
        terms: Payments.Terms = Payments.Terms.SHOTEF_30,
        actorName: String,
    ): PaymentApplicationEntity {
        val now = System.currentTimeMillis()
        val previous = dao.lastPaid(projectId, direction.name)
        val application = PaymentApplicationEntity(
            id = UUID.randomUUID().toString(),
            reference = String.format(Locale.ROOT, "PA-%03d", dao.count() + 1),
            projectId = projectId,
            direction = direction.name,
            partyName = partyName.trim(),
            applicationNumber = (dao.lastNumber(projectId, direction.name) ?: 0) + 1,
            periodEndsOn = now,
            status = Payments.Status.DRAFT,
            claimedGrossToDate = claimedGrossToDate,
            certifiedGrossToDate = null,
            previouslyPaidNet = netOf(previous, contractSum),
            retentionRate = retentionRate,
            retentionLimit = retentionLimit,
            terms = terms.name,
            submittedAt = null,
            certifiedAt = null,
            certifiedByName = null,
            dueOn = null,
            paidAt = null,
            notes = null,
            createdByName = actorName,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(application)
        audit.record(
            APPLICATION, application.id, AuditTrail.Action.CREATE, actorName,
            "${application.reference} #${application.applicationNumber} ${application.partyName}",
        )
        return application
    }

    /**
     * The net figure a paid application settled at.
     *
     * Runs the same retention rule the rest of the app runs, cap and all,
     * rather than a second copy of it in SQL.
     */
    private fun netOf(previous: PaymentApplicationEntity?, contractSum: Double): Double {
        val gross = previous?.certifiedGrossToDate ?: return 0.0
        return gross - Payments.retentionOn(
            grossToDate = gross,
            contractSum = contractSum,
            retentionRate = previous.retentionRate,
            retentionLimit = previous.retentionLimit,
        )
    }

    suspend fun updateClaim(
        application: PaymentApplicationEntity,
        claimedGrossToDate: Double,
        actorName: String,
    ) {
        if (application.status != Payments.Status.DRAFT) return
        dao.upsert(
            application.copy(
                claimedGrossToDate = claimedGrossToDate,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        audit.record(APPLICATION, application.id, AuditTrail.Action.UPDATE, actorName, application.reference)
    }

    suspend fun submit(application: PaymentApplicationEntity, actorName: String) {
        if (application.status != Payments.Status.DRAFT) return
        val now = System.currentTimeMillis()
        dao.upsert(
            application.copy(
                status = Payments.Status.SUBMITTED,
                submittedAt = now,
                updatedAt = now,
            ),
        )
        audit.record(
            APPLICATION, application.id, AuditTrail.Action.UPDATE, actorName,
            "${application.reference} submitted",
        )
    }

    /**
     * Certifies a figure, which may not be the figure that was claimed.
     *
     * The due date is worked out here and stored, so that changing the terms on
     * the job afterwards does not restate when last March's money was owed.
     */
    suspend fun certify(
        application: PaymentApplicationEntity,
        certifiedGrossToDate: Double,
        actorName: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (!Payments.canCertify(application.status, certifiedGrossToDate)) return false
        val now = System.currentTimeMillis()
        val terms = runCatching { Payments.Terms.valueOf(application.terms) }
            .getOrDefault(Payments.Terms.SHOTEF_30)
        val due = Payments.dueDate(
            invoiceDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
            terms = terms,
        )
        dao.upsert(
            application.copy(
                status = Payments.Status.CERTIFIED,
                certifiedGrossToDate = certifiedGrossToDate,
                certifiedAt = now,
                certifiedByName = actorName,
                dueOn = due.atStartOfDay(zone).toInstant().toEpochMilli(),
                updatedAt = now,
            ),
        )
        audit.record(
            APPLICATION, application.id, AuditTrail.Action.SIGN_OFF, actorName,
            "${application.reference} certified",
        )
        return true
    }

    suspend fun markPaid(application: PaymentApplicationEntity, actorName: String): Boolean {
        if (!Payments.canMarkPaid(application.status)) return false
        val now = System.currentTimeMillis()
        dao.upsert(
            application.copy(status = Payments.Status.PAID, paidAt = now, updatedAt = now),
        )
        audit.record(
            APPLICATION, application.id, AuditTrail.Action.UPDATE, actorName,
            "${application.reference} paid",
        )
        return true
    }

    suspend fun reject(application: PaymentApplicationEntity, notes: String?, actorName: String) {
        val now = System.currentTimeMillis()
        dao.upsert(
            application.copy(
                status = Payments.Status.REJECTED,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() } ?: application.notes,
                updatedAt = now,
            ),
        )
        audit.record(
            APPLICATION, application.id, AuditTrail.Action.UPDATE, actorName,
            "${application.reference} rejected",
        )
    }

    suspend fun remove(application: PaymentApplicationEntity, actorName: String) {
        dao.delete(application)
        audit.record(
            APPLICATION, application.id, AuditTrail.Action.DELETE, actorName, application.reference,
        )
    }

    private companion object {
        const val APPLICATION = "payment_application"
    }
}
