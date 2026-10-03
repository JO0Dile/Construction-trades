package il.co.tradesmanager.core.access

/**
 * What an organisation is on one job.
 *
 * Not the same question as [Role], and confusing the two is how a permission
 * system gets a hole in it. [Role] asks what a person may do inside their own
 * company — a foreman writes the daily log, the bookkeeper does not. [Party]
 * asks who the company is on this particular job, which decides what it may
 * ever be told about anybody else's money.
 *
 * The same firm is a first-tier subcontractor on one tower and a general
 * contractor on the villa next door, so this is a property of the engagement,
 * never of the company.
 *
 * [depth] runs from the money outwards. The client's money reaches the general
 * contractor, whose money reaches the first tier, whose money reaches the
 * crew. Every step down is somebody's margin, and that margin is the thing
 * nobody below it may see.
 */
enum class Party(val depth: Int) {

    /** The developer or owner. Pays for the building, signs nothing else. */
    CLIENT(0),

    /**
     * Engineer, supervisor, safety consultant. Beside the chain rather than
     * in it: appointed by whoever appointed them, carries no subcontract of
     * its own, and sees no commercial terms unless it is a party to them.
     */
    CONSULTANT(0),

    /** Holds the main contract. Everything below hangs off it. */
    GENERAL_CONTRACTOR(1),

    /** Contracts directly with the general contractor. */
    FIRST_TIER(2),

    /**
     * Contracts with a first-tier subcontractor. Crew leader, foreman with
     * his own men, labour-only gang — the app does not distinguish, because
     * the commercial position is identical whatever the card says.
     */
    SECOND_TIER(3),

    /** Sells materials or plant. Never holds a work package. */
    SUPPLIER(4),
    ;

    /** Whether a firm in this position may subcontract work at all. */
    val canSubcontract: Boolean
        get() = this == GENERAL_CONTRACTOR || this == FIRST_TIER || this == CLIENT

    /**
     * The positions this one may hand a work package to.
     *
     * Downward only. A second-tier crew that could engage a first-tier
     * subcontractor would make the chain a graph, and a graph has no "above"
     * — which is the word the whole confidentiality rule is written in.
     */
    fun mayEngage(): Set<Party> = when (this) {
        CLIENT -> setOf(GENERAL_CONTRACTOR, CONSULTANT, FIRST_TIER)
        GENERAL_CONTRACTOR -> setOf(FIRST_TIER, CONSULTANT, SUPPLIER)
        FIRST_TIER -> setOf(SECOND_TIER, SUPPLIER)
        SECOND_TIER, SUPPLIER, CONSULTANT -> emptySet()
    }

    companion object {
        /**
         * Stored as a string so a new position costs no migration. An
         * unrecognised value falls to [SECOND_TIER] — the position that may
         * engage nobody and see least. A corrupted row must never read as a
         * general contractor.
         */
        fun parse(value: String?): Party =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
                ?: SECOND_TIER
    }
}
