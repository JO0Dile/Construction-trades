package il.co.tradesmanager.core.money

/**
 * What a firm has paid for, and the one thing that is never for sale.
 *
 * The rule that comes before the price, from docs/PRICING.md:
 *
 * > Nothing that keeps somebody safe is ever behind a payment.
 *
 * That is written here as [NEVER_CHARGED] rather than left as a sentence in a
 * document, because a sentence in a document does not fail a build. Every
 * capability in that set is allowed on every plan, including a lapsed one, and
 * a test walks the whole enum against every plan to prove it. A future edit
 * that puts a permit or an incident report behind a payment has to delete a
 * passing test to do it, which is a thing somebody notices.
 *
 * The reasoning, which is worth keeping next to the code: a worker who cannot
 * report a near miss because their foreman's card expired is a worker who does
 * not report it, and the register that was supposed to prove the site was run
 * properly then has a hole in it on exactly the day somebody asks.
 *
 * Priced per firm rather than per person, because that is how the money works
 * on a site: the boss pays and the crew does not. A labourer who installs this
 * to see their own hours is a user, not a line item.
 */
object Plans {

    enum class Plan { FREE, PRO, SITE }

    /**
     * Everything a plan can be asked about.
     *
     * One enum rather than scattered booleans so that [NEVER_CHARGED] can be a
     * set over it and the invariant can be tested exhaustively. A new
     * capability that nobody classifies fails the test that every entry is
     * either never-charged or listed against a plan.
     */
    enum class Capability {
        /* ---- never charged for, on any plan, ever ---- */

        /** Being signed onto a site at the gate. */
        ADMISSION_AT_THE_GATE,

        /** Reading and signing the safety induction. */
        SAFETY_INDUCTION,

        /** Reading a permit to work, and the register it sits in. */
        READ_PERMITS,

        /** Reporting an incident or a near miss. */
        REPORT_INCIDENT,

        /** Seeing a violation written against you. */
        SEE_OWN_VIOLATION,

        /** Clocking in and out. */
        CHECK_IN_AND_OUT,

        /**
         * Reading anything already recorded, and getting it out again.
         *
         * Never charged even when a subscription has lapsed. A firm that
         * cannot get its own site diary out of an app is a firm that will
         * never put one in, and the day they most need the export is the day
         * they have stopped paying.
         */
        READ_EVERYTHING,
        EXPORT_EVERYTHING,

        /* ---- what a plan buys ---- */

        /** More than one company on the one device. */
        SECOND_COMPANY,

        /** The audit export, signed so a third party can check it. */
        SIGNED_AUDIT_EXPORT,

        /** Subcontractor chains: who engaged whom, and what they may see. */
        SUBCONTRACT_CHAIN,

        /** Contracts and work packages against them. */
        CONTRACTS,

        /** Payment applications, certification and retention. */
        PAYMENT_APPLICATIONS,
    }

    /** The rule, as a set the compiler and a test can both see. */
    val NEVER_CHARGED: Set<Capability> = setOf(
        Capability.ADMISSION_AT_THE_GATE,
        Capability.SAFETY_INDUCTION,
        Capability.READ_PERMITS,
        Capability.REPORT_INCIDENT,
        Capability.SEE_OWN_VIOLATION,
        Capability.CHECK_IN_AND_OUT,
        Capability.READ_EVERYTHING,
        Capability.EXPORT_EVERYTHING,
    )

    private val PRO_ADDS = setOf(Capability.SECOND_COMPANY, Capability.SIGNED_AUDIT_EXPORT)

    private val SITE_ADDS = setOf(
        Capability.SUBCONTRACT_CHAIN,
        Capability.CONTRACTS,
        Capability.PAYMENT_APPLICATIONS,
    )

    /**
     * How many people a plan may have on its books, or null for no limit.
     *
     * Free is three rather than one. A sole trader and a two-man crew never
     * pay and everything works for them — they are not a lost sale, they are
     * how the third person who joins the firm finds out the app exists.
     */
    fun seats(plan: Plan): Int? = when (plan) {
        Plan.FREE -> 3
        Plan.PRO -> 15
        Plan.SITE -> null
    }

    /**
     * What a firm holds: the plan it bought, and whether it has run out.
     *
     * [lapsed] is a separate flag rather than a fourth plan because what
     * lapses is the *writing*. The records stay readable and exportable for
     * ever — see [NEVER_CHARGED].
     */
    data class Entitlement(val plan: Plan = Plan.FREE, val lapsed: Boolean = false) {
        /** What the firm can write as things stand. */
        val effective: Plan get() = if (lapsed) Plan.FREE else plan
    }

    fun allows(entitlement: Entitlement, capability: Capability): Boolean {
        if (capability in NEVER_CHARGED) return true
        return when (entitlement.effective) {
            Plan.FREE -> false
            Plan.PRO -> capability in PRO_ADDS
            Plan.SITE -> capability in PRO_ADDS || capability in SITE_ADDS
        }
    }

    /**
     * Whether the office may put one more person on the books.
     *
     * Deliberately not asked at the gate. Admission is in [NEVER_CHARGED], so
     * a man who turns up to work is signed in whatever the firm has paid, and
     * that can carry a firm past its own seat limit. That is the rule working
     * as intended rather than a hole in it: the limit is on a firm choosing to
     * add somebody in the office, not on a person arriving at a site.
     */
    fun mayAddPerson(entitlement: Entitlement, onTheBooks: Int): Boolean {
        val limit = seats(entitlement.effective) ?: return true
        return onTheBooks < limit
    }

    /** How many more the plan allows, or null when there is no limit. */
    fun seatsLeft(entitlement: Entitlement, onTheBooks: Int): Int? {
        val limit = seats(entitlement.effective) ?: return null
        return (limit - onTheBooks).coerceAtLeast(0)
    }
}
