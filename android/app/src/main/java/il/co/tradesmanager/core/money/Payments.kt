package il.co.tradesmanager.core.money

import java.time.LocalDate

/**
 * Payment applications, retention, and when the money actually arrives.
 *
 * Two things in here are got wrong constantly, and both cost real money.
 *
 * The first is that **applications are cumulative**. Application three says
 * "four hundred thousand of work done to date", not "four hundred thousand
 * this month". Paying the gross figure on the face of each application in turn
 * pays for the same work three times, and it happens because the number that
 * is printed largest is the number nobody should be paying.
 *
 * The second is what "שוטף + 30" means. The clock does not start on the
 * invoice date; it starts at the **end of the month the invoice falls in**.
 * An invoice dated the 3rd of March on shotef+30 is due on the 30th of April,
 * not the 2nd of April — and an invoice dated the 31st of March is due on the
 * same day as the one from the 3rd. That is why nobody wants to invoice on the
 * 1st, and why a subcontractor budgeting from the invoice date runs out of cash.
 */
object Payments {

    /** A common retention on a subcontract. Only ever a default. */
    const val DEFAULT_RETENTION = 0.05

    /**
     * Retention stops accruing once it reaches this share of the contract.
     *
     * The "limit of retention": without it, a percentage held on every
     * application would keep growing with the job, and on a contract that runs
     * over it would end up holding back more than was ever agreed.
     */
    const val DEFAULT_RETENTION_LIMIT = 0.05

    /**
     * What a payment application says, and what it means once the arithmetic
     * has been done.
     *
     * [grossToDate] and [previouslyPaidNet] are both cumulative — the first is
     * what this application claims for all work to date, the second is what has
     * actually been paid out across everything before it.
     */
    data class Assessment(
        /** What the application claims, for all work to date. */
        val grossToDate: Double,
        /** Held back, capped at the limit of retention. */
        val retentionHeld: Double,
        /** Gross less retention: what is certified as payable to date. */
        val netToDate: Double,
        /** Net to date less everything already paid. The cheque. */
        val dueNow: Double,
    )

    /**
     * Works out what this application is actually worth.
     *
     * [contractSum] is what the retention limit is a share of — the revised
     * contract, variations included, because retention is held against the
     * work being done rather than against what was first agreed.
     *
     * [dueNow] can come out negative, and is left that way on purpose. It means
     * the previous applications over-certified: the work has been re-measured
     * downwards, or something has been rejected. Clamping it to zero would hide
     * that, and the money has to come back one way or another.
     *
     * [previouslyPaidNet] belongs to [previouslyPaidNet]. Passing a figure that
     * was worked out earlier and kept is how this comes out wrong.
     */
    fun assess(
        grossToDate: Double,
        previouslyPaidNet: Double,
        contractSum: Double,
        retentionRate: Double = DEFAULT_RETENTION,
        retentionLimit: Double = DEFAULT_RETENTION_LIMIT,
    ): Assessment {
        val held = retentionOn(grossToDate, contractSum, retentionRate, retentionLimit)
        val net = grossToDate - held
        return Assessment(
            grossToDate = grossToDate,
            retentionHeld = held,
            netToDate = net,
            dueNow = net - previouslyPaidNet,
        )
    }

    /**
     * Retention on the work certified so far, capped at the limit.
     *
     * A zero or negative contract sum means there is nothing to take a share
     * of, so the cap is not applied — a job whose contract value has not been
     * entered yet should not silently have its retention capped at nothing.
     */
    fun retentionOn(
        grossToDate: Double,
        contractSum: Double,
        retentionRate: Double = DEFAULT_RETENTION,
        retentionLimit: Double = DEFAULT_RETENTION_LIMIT,
    ): Double {
        if (grossToDate <= 0.0) return 0.0
        val uncapped = grossToDate * retentionRate
        if (contractSum <= 0.0) return uncapped
        return minOf(uncapped, contractSum * retentionLimit)
    }

    /**
     * One earlier application, reduced to what the running total needs of it.
     *
     * A bare figure would not do. The retention rate and its limit are stored
     * per application because they are what was agreed when that one was
     * raised; running today's rate over all of them would restate every cheque
     * already banked on a job whose retention was renegotiated halfway through.
     */
    data class Settled(
        /** Its place in the sequence. Applications are numbered from one. */
        val number: Int,
        /**
         * Which sequence it belongs to.
         *
         * Our applications to the client and a subcontractor's to us are two
         * sequences that both start at one. Left as free text because this
         * file has no business knowing what the directions are called — only
         * that two applications in different ones never measure each other.
         */
        val direction: String,
        /** The figure that was certified on it — not the figure claimed. */
        val certifiedGrossToDate: Double,
        val retentionRate: Double = DEFAULT_RETENTION,
        val retentionLimit: Double = DEFAULT_RETENTION_LIMIT,
    )

    /**
     * What has already been paid out on everything before application [number].
     *
     * Worked out, never remembered. Applications are raised in one order and
     * paid in another: under שוטף + 30 the next one is always raised before the
     * previous one is paid, so an application that wrote down "nothing has been
     * paid yet" on the day it was raised would still be saying it a month later
     * when it was certified — and would ask for every shekel of the job to date
     * a second time. That is the exact mistake this whole file exists to stop,
     * and storing the answer is how it gets made.
     *
     * [paid] is every application on the job that has actually been paid, in
     * either direction. Only the highest-numbered one below [number] in the
     * same [direction] counts: the applications are cumulative, so the earlier
     * ones of that sequence are already inside it. Order does not matter, and
     * neither do gaps.
     */
    fun previouslyPaidNet(
        number: Int,
        direction: String,
        paid: List<Settled>,
        contractSum: Double,
    ): Double {
        val previous = paid
            .filter { it.direction == direction && it.number < number }
            .maxByOrNull { it.number } ?: return 0.0
        return previous.certifiedGrossToDate - retentionOn(
            grossToDate = previous.certifiedGrossToDate,
            contractSum = contractSum,
            retentionRate = previous.retentionRate,
            retentionLimit = previous.retentionLimit,
        )
    }

    /**
     * The half of retention released at practical completion.
     *
     * Halves rather than a configurable split because that is what the split
     * is, near enough universally: half when the building is usable, half when
     * the defects period has run and the snags are done.
     */
    fun releasedAtCompletion(retentionHeld: Double): Double = retentionHeld / 2.0

    /** The balance, released when the defects liability period ends. */
    fun releasedAfterDefects(retentionHeld: Double): Double =
        retentionHeld - releasedAtCompletion(retentionHeld)

    /**
     * Israeli payment terms, as they are actually written on a contract.
     *
     * [days] is added to the end of the month, not to the invoice date. Naming
     * them "shotef plus" rather than "net" is deliberate: "net 30" means
     * thirty days from the invoice, and somebody reading that into a shotef+30
     * contract will be a month out and short of cash.
     */
    enum class Terms(val days: Long) {
        /** On presentation. Rare, and worth naming so it can be chosen. */
        IMMEDIATE(0L),
        SHOTEF_30(30L),
        SHOTEF_60(60L),
        SHOTEF_90(90L),
        SHOTEF_120(120L),
    }

    /**
     * When the money is actually due.
     *
     * The end of the invoice's month, plus the term. [Terms.IMMEDIATE] is the
     * one exception and returns the invoice date itself, because "the end of
     * the month plus nothing" would be a fortnight's wait dressed up as
     * payment on presentation.
     */
    fun dueDate(invoiceDate: LocalDate, terms: Terms): LocalDate {
        if (terms == Terms.IMMEDIATE) return invoiceDate
        return invoiceDate.withDayOfMonth(invoiceDate.lengthOfMonth()).plusDays(terms.days)
    }

    /**
     * How many days late a payment is; zero or negative if it is not yet due.
     *
     * Positive means overdue, which is the direction somebody is asking about
     * when they open this screen.
     */
    fun daysOverdue(dueOn: LocalDate, today: LocalDate): Long =
        java.time.temporal.ChronoUnit.DAYS.between(dueOn, today)

    /** What is stored on an application row. */
    object Status {
        /** Being prepared. Claims nothing yet. */
        const val DRAFT = "DRAFT"

        /** Sent to the payer, waiting to be assessed. */
        const val SUBMITTED = "SUBMITTED"

        /** Assessed. The certified figure may differ from the claim. */
        const val CERTIFIED = "CERTIFIED"

        /** The money has arrived. */
        const val PAID = "PAID"

        /** Withdrawn or superseded. */
        const val REJECTED = "REJECTED"
    }

    /**
     * Whether an application may be certified.
     *
     * Only a submitted one, and only for a figure somebody has entered. A
     * certification of nothing is how an application quietly disappears.
     */
    fun canCertify(status: String, certifiedGrossToDate: Double?): Boolean =
        status == Status.SUBMITTED && certifiedGrossToDate != null

    /** Only a certified application can be marked paid. */
    fun canMarkPaid(status: String): Boolean = status == Status.CERTIFIED
}
