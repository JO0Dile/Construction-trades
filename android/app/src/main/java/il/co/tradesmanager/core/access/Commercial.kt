package il.co.tradesmanager.core.access

/**
 * Who may be told what a job is worth.
 *
 * The rule is one line and everything else here follows from it:
 *
 * > **A commercial figure may be sent only to a party to the contract it
 * > belongs to.**
 *
 * Not "a second-tier crew must not see the first tier's margin" — that is a
 * consequence, not the rule, and a rule written that way needs a new clause
 * for every new shape of job. Written this way, the crew cannot see the main
 * contract because it is not party to it, the general contractor cannot see
 * what the crew was paid because it is not party to that either, and a
 * consultant sees nothing at all unless somebody engaged it.
 *
 * The rule is expressed here, in `core`, so that the phone and the server run
 * the identical function rather than two implementations that disagree. It is
 * the server's answer that matters: hiding a field the API already sent is not
 * confidentiality, it is a stylesheet. See docs/SERVER.md.
 */
object Commercial {

    /**
     * One agreement, reduced to the question of who may see its money.
     *
     * [payerOrgId] and [payeeOrgId] are the two parties. [disclosedToOrgIds]
     * is the deliberate exception: a client that has required open-book
     * reporting, or a receiver appointed over an insolvent contractor. It is
     * a list rather than a flag because every disclosure is to somebody
     * specific, and "visible to everyone above" is how a margin leaks.
     */
    data class Agreement(
        val id: String,
        val payerOrgId: String,
        val payeeOrgId: String,
        val disclosedToOrgIds: Set<String> = emptySet(),
    )

    /** Whether [orgId] is one of the two parties. */
    fun isParty(agreement: Agreement, orgId: String): Boolean =
        orgId == agreement.payerOrgId || orgId == agreement.payeeOrgId

    /**
     * Whether [orgId] may be sent the figures on [agreement].
     *
     * A blank organisation id is nobody, and nobody sees anything. That
     * matters more than it reads: an unauthenticated request arrives with no
     * organisation, and a rule that treated blank as a wildcard would answer
     * it in full.
     */
    fun maySeeMoney(agreement: Agreement, orgId: String): Boolean {
        if (orgId.isBlank()) return false
        return isParty(agreement, orgId) || orgId in agreement.disclosedToOrgIds
    }

    /**
     * Whether [orgId] may be told the agreement exists at all.
     *
     * Wider than [maySeeMoney] on purpose. A second-tier crew has to know
     * that the scope it is working to sits under a package somebody else
     * holds, or it cannot report against it — but knowing a package exists is
     * not knowing what it is worth. Existence follows the work; money follows
     * the contract.
     */
    fun maySeeExistence(agreement: Agreement, orgId: String, engagedOnJob: Boolean): Boolean =
        maySeeMoney(agreement, orgId) || (orgId.isNotBlank() && engagedOnJob)

    /**
     * What actually goes over the wire about one agreement.
     *
     * Returned rather than a boolean so that there is one place that decides
     * and one shape that leaves. A caller cannot forget to null a field it
     * never received: [Visible.money] is absent, not blanked, when the viewer
     * is not entitled to it.
     */
    data class Money(
        val contractSum: Double,
        val certifiedToDate: Double,
        val paidToDate: Double,
    )

    data class Visible(
        val agreementId: String,
        val money: Money?,
    )

    fun disclose(
        agreement: Agreement,
        money: Money,
        toOrgId: String,
    ): Visible = Visible(
        agreementId = agreement.id,
        money = money.takeIf { maySeeMoney(agreement, toOrgId) },
    )

    /**
     * The margin between what a firm is paid and what it pays out.
     *
     * Only ever answered for the firm in the middle. It is not a field on an
     * agreement — it is the difference between two sets of them, and the whole
     * point is that both sets are never visible to anybody else. Returns null
     * rather than zero when the viewer is not that firm, because zero is an
     * answer and this must not be answerable.
     *
     * [receivables] is a list rather than one agreement because a firm
     * commonly holds more than one package from the same client on one job —
     * the frame and the fit-out, let through separately. Netting off against
     * only the first of them understates the margin by the whole of the
     * second, which is a wrong number that looks entirely plausible.
     *
     * Every receivable must be one this firm is paid on and every payable one
     * it pays on. A list with somebody else's agreement in it produces a
     * figure that looks like a margin and is not one, so it is refused rather
     * than filtered — silently dropping a row would answer a different
     * question from the one asked.
     */
    fun margin(
        receivables: List<Pair<Agreement, Money>>,
        payables: List<Pair<Agreement, Money>>,
        viewerOrgId: String,
    ): Double? {
        if (viewerOrgId.isBlank()) return null
        if (receivables.isEmpty()) return null
        if (receivables.any { (agreement, _) -> agreement.payeeOrgId != viewerOrgId }) return null
        if (payables.any { (agreement, _) -> agreement.payerOrgId != viewerOrgId }) return null
        return receivables.sumOf { it.second.certifiedToDate } -
            payables.sumOf { it.second.certifiedToDate }
    }
}
