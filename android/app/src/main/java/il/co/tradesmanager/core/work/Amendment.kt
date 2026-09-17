package il.co.tradesmanager.core.work

/**
 * Changing a price that has already been agreed.
 *
 * Once both firms have accepted a figure, that figure stops being a field and
 * becomes a record. Editing it in place is the failure this exists to prevent:
 * the crew agreed 7,000, the number now says 6,500, and there is nothing to
 * point at. So the agreed figure is never written over. A new version is
 * raised, both sides accept it, and the old one stays readable for as long as
 * anybody might ask.
 *
 * How long "as long as anybody might ask" is depends on the jurisdiction, so
 * it is a configured retention period rather than a number in this file. In
 * Israel the accounting-records period under the bookkeeping regulations is
 * the one to set it from; confirm the current figure with an accountant
 * rather than taking it from here. See docs/COMPLIANCE.md.
 */
object Amendment {

    object Status {
        /** One side has proposed a change. */
        const val PROPOSED = "PROPOSED"

        /** The other side accepted. This version is now the live one. */
        const val ACCEPTED = "ACCEPTED"

        /** The other side refused. The previous version stands. */
        const val REJECTED = "REJECTED"

        /** The proposer took it back before an answer. */
        const val WITHDRAWN = "WITHDRAWN"
    }

    /**
     * Whether an amendment may be raised against a contract at all.
     *
     * Not while another is outstanding. Two open proposals against one
     * contract means two people believe two different numbers are about to be
     * true, and whichever is accepted second silently overwrites the first.
     */
    fun canPropose(contractSigned: Boolean, openAmendments: Int): Boolean =
        contractSigned && openAmendments == 0

    /**
     * Whether a proposal carries what an auditor will ask for.
     *
     * A reason is required. "Why is this contract worth 7,500 and not 7,000"
     * is asked years later, by somebody who was not there, and the answer has
     * to be in the record rather than in a foreman's memory.
     */
    fun isComplete(newAmount: Double?, reason: String?): Boolean =
        newAmount != null && newAmount >= 0.0 && !reason.isNullOrBlank()

    /**
     * Whether [orgId] may accept this proposal.
     *
     * The counterparty, never the proposer. A firm that could accept its own
     * amendment could raise the price of its own subcontract on a Friday
     * afternoon, and the two-signature requirement would be decoration.
     */
    fun canAccept(
        proposedByOrgId: String,
        payerOrgId: String,
        payeeOrgId: String,
        orgId: String,
    ): Boolean {
        if (orgId.isBlank() || orgId == proposedByOrgId) return false
        return orgId == payerOrgId || orgId == payeeOrgId
    }

    /**
     * What one accepted amendment changes, kept whole.
     *
     * [previousAmount] is stored rather than derived. Deriving it from the
     * version before means the history is only readable while every version
     * survives, and the one question asked in a dispute — what did it go from
     * and to — should not depend on that.
     */
    data class Version(
        val version: Int,
        val previousAmount: Double,
        val newAmount: Double,
        val reason: String,
        val proposedByOrgId: String,
        val acceptedByOrgId: String,
        val acceptedAt: Long,
    )

    fun delta(version: Version): Double = version.newAmount - version.previousAmount

    /**
     * The figure in force now: the latest accepted version, or the original.
     *
     * By version number rather than by acceptance time. Two amendments
     * accepted in the same second on two phones would otherwise resolve
     * differently on each of them.
     */
    fun currentAmount(original: Double, accepted: List<Version>): Double =
        accepted.maxByOrNull { it.version }?.newAmount ?: original

    /** Every figure this contract has ever been, oldest first. */
    fun history(original: Double, accepted: List<Version>): List<Double> =
        listOf(original) + accepted.sortedBy { it.version }.map { it.newAmount }
}
