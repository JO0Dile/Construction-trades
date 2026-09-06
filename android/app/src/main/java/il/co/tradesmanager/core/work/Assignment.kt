package il.co.tradesmanager.core.work

/**
 * A package of work handed from one firm to another, and what may happen to
 * it next.
 *
 * The lifecycle is written as a state machine rather than a set of booleans
 * because the states are the argument. Every dispute on a site is about which
 * of these an assignment is in: the crew says it submitted, the contractor
 * says it never saw it, and nobody can show when it moved. A stored state
 * with a recorded transition can be shown.
 *
 * The one thing this deliberately refuses to do is treat approval as payment.
 * They are separate states with separate transitions and separate actors,
 * because they are separate events weeks apart, and an app that shades one
 * into the other tells a crew it has been paid when it has not.
 */
object Assignment {

    object Status {
        /** Created by the payer, not yet sent. */
        const val DRAFT = "DRAFT"

        /** Sent. The crew has been asked and has not answered. */
        const val OFFERED = "OFFERED"

        /** The crew took it. The scope and the price are now agreed. */
        const val ACCEPTED = "ACCEPTED"

        /** The crew declined. */
        const val DECLINED = "DECLINED"

        /** Work is happening. */
        const val IN_PROGRESS = "IN_PROGRESS"

        /** The crew says it is finished and has put up its evidence. */
        const val SUBMITTED = "SUBMITTED"

        /** Looked at and sent back, with a reason. */
        const val REJECTED = "REJECTED"

        /** Signed off as done. Not paid. */
        const val APPROVED = "APPROVED"

        /** Stopped by the payer before completion. */
        const val CANCELLED = "CANCELLED"
    }

    /** States from which nothing further happens. */
    val terminal = setOf(Status.DECLINED, Status.APPROVED, Status.CANCELLED)

    private val transitions: Map<String, Set<String>> = mapOf(
        Status.DRAFT to setOf(Status.OFFERED, Status.CANCELLED),
        Status.OFFERED to setOf(Status.ACCEPTED, Status.DECLINED, Status.CANCELLED),
        Status.ACCEPTED to setOf(Status.IN_PROGRESS, Status.CANCELLED),
        Status.IN_PROGRESS to setOf(Status.SUBMITTED, Status.CANCELLED),
        Status.SUBMITTED to setOf(Status.APPROVED, Status.REJECTED),
        // Back to work, not back to offered: the price was agreed once and a
        // rejection is not a chance to renegotiate it.
        Status.REJECTED to setOf(Status.IN_PROGRESS, Status.CANCELLED),
        Status.APPROVED to emptySet(),
        Status.DECLINED to emptySet(),
        Status.CANCELLED to emptySet(),
    )

    fun canMove(from: String, to: String): Boolean = to in transitions[from].orEmpty()

    fun next(from: String): Set<String> = transitions[from].orEmpty()

    /** Who is entitled to make a move, in terms of the two firms involved. */
    enum class Side { PAYER, PAYEE }

    /**
     * The side that may perform a transition.
     *
     * The asymmetry is the point. Only the crew may accept or submit — a
     * contractor that could mark work submitted on the crew's behalf could
     * manufacture a rejection. Only the payer may approve or cancel — a crew
     * that could approve its own work needs no inspection at all.
     */
    fun actor(to: String): Side = when (to) {
        Status.ACCEPTED, Status.DECLINED, Status.IN_PROGRESS, Status.SUBMITTED -> Side.PAYEE
        else -> Side.PAYER
    }

    fun mayMove(from: String, to: String, side: Side): Boolean =
        canMove(from, to) && actor(to) == side

    /**
     * Whether a rejection carries what it has to carry.
     *
     * A rejection with no reason is a crew standing on a floor with no idea
     * what to change, and it is the single most common way a week is lost.
     */
    fun canReject(reason: String?): Boolean = !reason.isNullOrBlank()

    /**
     * Whether submitted work is complete enough to be looked at.
     *
     * Photographs are demanded at submission rather than at approval because
     * approval happens in an office days later, by which time the wall is
     * plastered and there is nothing left to photograph.
     */
    fun canSubmit(status: String, photoCount: Int, requiredPhotos: Int = 1): Boolean =
        status == Status.IN_PROGRESS && photoCount >= requiredPhotos

    /**
     * Whether an approval may raise a payment application.
     *
     * Approved work is a debt, not a payment. The application is a separate
     * record with its own terms, and `core.money.Payments` decides when the
     * money is actually due — usually שוטף + 30 from the end of the month,
     * which is a good six weeks after the sign-off that this function permits.
     */
    fun canInvoice(status: String, alreadyInvoiced: Boolean): Boolean =
        status == Status.APPROVED && !alreadyInvoiced

    /**
     * One package, as the claim arithmetic sees it.
     *
     * [amount] is what was agreed for this package alone. A package is a
     * discrete piece of work; a payment application is not. Keeping the two
     * apart is the entire point of the functions below.
     */
    data class Claimable(
        val id: String,
        val amount: Double,
        val status: String,
        val invoiced: Boolean,
    )

    /** The packages an application raised now would newly cover. */
    fun readyToClaim(packages: List<Claimable>): List<Claimable> =
        packages.filter { canInvoice(it.status, it.invoiced) }

    /**
     * What an application raised now should claim — **cumulative**.
     *
     * This is the one place the two shapes meet, and it is where the mistake
     * would be made. A package is worth what it is worth on its own; an
     * application says what the work is worth **in total to date**, and the
     * difference between that and what has already been paid is the cheque.
     * Claiming one package's amount on application three would ask for that
     * package and hand back everything claimed on one and two.
     *
     * So every approved package counts, whether it was invoiced on an earlier
     * application or is being invoiced on this one. Approved-but-not-yet-
     * claimed work is work done to date; leaving it out would understate the
     * claim by exactly the packages this application exists to cover.
     *
     * Packages that are not approved are excluded. Work that has not been
     * signed off is not work to date, whatever the crew thinks of it.
     */
    fun claimToDate(packages: List<Claimable>): Double =
        packages.filter { it.status == Status.APPROVED }.sumOf { it.amount }

    /**
     * Whether raising an application from these packages is worth doing.
     *
     * False when nothing new has been approved since the last one. An
     * application for nothing is a document somebody has to read, decline and
     * file, and it makes the next real one look like more of the same.
     */
    fun canRaiseApplication(packages: List<Claimable>): Boolean =
        readyToClaim(packages).isNotEmpty()
}
