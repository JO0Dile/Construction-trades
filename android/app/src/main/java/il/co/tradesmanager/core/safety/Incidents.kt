package il.co.tradesmanager.core.safety

/**
 * Something went wrong on site, and it gets written down.
 *
 * The table, the DAO and the repository for this existed from early on and
 * nothing could reach any of them: there was no way to report an accident and
 * no way to read one back. On a safety app that is not a missing screen, it is
 * a missing obligation.
 *
 * Severity is a controlled list rather than free text. The column was a
 * `String` with nothing constraining it, which is how a register ends up
 * holding "minor", "Minor", "not bad" and "לא נורא" and cannot be counted.
 */
object Incidents {

    /**
     * Ordered least to most serious, and the order is load-bearing: the
     * register sorts by it and [needsEscalating] compares against it.
     */
    enum class Severity {
        /** Nobody was hurt and nothing was damaged. It nearly happened. */
        NEAR_MISS,

        /** First aid on site, back to work. */
        MINOR,

        /** Taken to hospital, or off work beyond the day of the accident. */
        SERIOUS,

        /** Somebody died. */
        FATAL,
    }

    /**
     * Whether this is the kind of thing a regulator is told about, not just
     * recorded in the site file.
     *
     * The threshold here is the app's own, and deliberately cautious: it flags
     * serious and fatal, and says so on screen as a prompt rather than an
     * assurance. **What Israeli law actually requires to be reported, to whom,
     * and within what time, is a question for a qualified Israeli
     * professional** — see `docs/COMPLIANCE.md`, where every legal claim in
     * this app is marked as needing confirmation. Encoding a statutory list
     * here from memory would produce a screen that looked authoritative and
     * told somebody they had discharged a duty they had not.
     */
    fun needsEscalating(severity: Severity): Boolean =
        severity >= Severity.SERIOUS

    /** Newest first, and within the same moment the more serious first. */
    fun <T> order(
        items: List<T>,
        occurredAt: (T) -> Long,
        severity: (T) -> Severity,
    ): List<T> = items.sortedWith(
        compareByDescending<T> { occurredAt(it) }.thenByDescending { severity(it).ordinal },
    )

    /** Stored as the enum name; unknown text reads as the least assuming value. */
    fun parse(stored: String?): Severity =
        Severity.entries.firstOrNull { it.name == stored } ?: Severity.NEAR_MISS

    /** A report being written, and everything the rule below needs to judge it. */
    data class Report(
        val description: String,
        /** Photographs and videos attached so far. */
        val evidenceCount: Int,
        /** What it cost, when anybody has put a figure on it. */
        val cost: Double?,
    )

    /** Null when the report can be filed; otherwise what is missing. */
    fun blocksReporting(report: Report): Blocker? = when {
        report.description.isBlank() -> Blocker.NOT_DESCRIBED
        report.evidenceCount <= 0 -> Blocker.NO_EVIDENCE
        !acceptableCost(report.cost) -> Blocker.BAD_COST
        else -> null
    }

    enum class Blocker {
        /** Nothing written. A severity chip on its own says nothing. */
        NOT_DESCRIBED,

        /**
         * No photograph and no video.
         *
         * Required rather than encouraged. An incident is argued about weeks
         * later by people who were not there, and a description on its own is
         * one person's word — the ladder is moved, the spill is mopped, the
         * guard rail goes back on, and the only thing left is what somebody
         * photographed while they were standing in front of it.
         */
        NO_EVIDENCE,

        /** A negative figure. Blank is fine; less than nothing is not. */
        BAD_COST,
    }

    fun canReport(report: Report): Boolean = blocksReporting(report) == null

    /**
     * Whether a cost may be stored. Null is a real answer -- most reports are
     * filed by somebody who has no idea yet what it will come to, and forcing
     * a number would get zero, which is a claim rather than a blank.
     */
    fun acceptableCost(cost: Double?): Boolean = cost == null || cost >= 0.0
}
