package il.co.tradesmanager.core.evidence

import java.time.LocalDate

/**
 * Work that does not meet what it was meant to meet, and what is done about
 * it: the non-conformance report a supervisor, a client's quality engineer or
 * the firm's own quality control raises, and the register a public client
 * asks to see before a handover.
 *
 * A snag is "this needs finishing". A non-conformance is "this is not what
 * the drawing, the specification or the standard says", which is a different
 * conversation with different people in it. So each one says where it is,
 * which requirement it fails, what was found and by whom; then what is
 * decided about it -- taken out and done again, put right where it is, kept
 * as it is because somebody with the standing to agree has agreed, or
 * removed -- with the corrective work and a date; and it closes only with a
 * note of how the result was checked. One kept as it is names who accepted
 * it, because a concession nobody signed for is a defect nobody owns.
 */
object NonConformances {

    /** Who found it. */
    enum class FoundBy { OWN_QUALITY_CONTROL, SUPERVISOR, CLIENT, AUTHORITY, LABORATORY }

    /** What is decided about the work that does not conform. */
    enum class Disposition {
        /** Taken out and done again. */
        REWORK,

        /** Put right where it is, by an agreed method. */
        REPAIR,

        /** Kept as it is, because somebody with the standing to agree has agreed. */
        ACCEPT_AS_IS,

        /** Taken out and not replaced in this form. */
        REMOVE,
    }

    enum class Refusal {
        BLANK_ELEMENT,
        BLANK_REQUIREMENT,
        BLANK_FINDING,
        NO_ACCEPTOR,
        BLANK_CORRECTION,
        DUE_BEFORE_RAISED,
        ALREADY_DECIDED,
        NOT_DECIDED,
        BLANK_VERIFICATION,
        ALREADY_CLOSED,
    }

    enum class State {
        /** Decided, not closed, and the date for the corrective work has passed. */
        OVERDUE,
        /** Raised and nobody has decided what to do about it. */
        AWAITING_DECISION,
        /** Decided, and the work is in hand. */
        IN_HAND,
        CLOSED,
    }

    /** Where it is, which requirement it fails, and what was found: an NCR without a requirement is an opinion. */
    fun raiseRefusal(element: String, requirement: String, finding: String): Refusal? = when {
        element.isBlank() -> Refusal.BLANK_ELEMENT
        requirement.isBlank() -> Refusal.BLANK_REQUIREMENT
        finding.isBlank() -> Refusal.BLANK_FINDING
        else -> null
    }

    /**
     * Once. Kept as it is needs the name of who accepted it; anything that
     * changes the work needs to say what will be done.
     */
    fun decideRefusal(
        disposition: Disposition,
        acceptedBy: String,
        correction: String,
        dueOn: LocalDate?,
        raisedOn: LocalDate,
        decided: Boolean,
        closed: Boolean,
    ): Refusal? = when {
        closed -> Refusal.ALREADY_CLOSED
        decided -> Refusal.ALREADY_DECIDED
        disposition == Disposition.ACCEPT_AS_IS && acceptedBy.isBlank() -> Refusal.NO_ACCEPTOR
        disposition != Disposition.ACCEPT_AS_IS && correction.isBlank() -> Refusal.BLANK_CORRECTION
        dueOn != null && dueOn.isBefore(raisedOn) -> Refusal.DUE_BEFORE_RAISED
        else -> null
    }

    /** Closed with how the result was checked, and only once something was decided. */
    fun closeRefusal(decided: Boolean, verification: String, closed: Boolean): Refusal? = when {
        closed -> Refusal.ALREADY_CLOSED
        !decided -> Refusal.NOT_DECIDED
        verification.isBlank() -> Refusal.BLANK_VERIFICATION
        else -> null
    }

    /** Overdue from the day after its date. */
    fun state(decided: Boolean, dueOn: LocalDate?, closed: Boolean, today: LocalDate): State = when {
        closed -> State.CLOSED
        !decided -> State.AWAITING_DECISION
        dueOn != null && dueOn.isBefore(today) -> State.OVERDUE
        else -> State.IN_HAND
    }

    /** Overdue, then waiting for a decision, then in hand, then closed; the oldest first within each, closed newest first. */
    fun order(rows: List<Pair<State, Long>>): List<Int> =
        rows.indices.sortedWith(
            compareBy<Int>({ rows[it].first.ordinal })
                .thenBy { if (rows[it].first == State.CLOSED) -rows[it].second else rows[it].second },
        )

    /** "NCR-001": numbered per job. */
    fun reference(countOnJob: Int): String = "NCR-" + (countOnJob + 1).toString().padStart(3, '0')

    fun foundByOf(stored: String): FoundBy = FoundBy.entries.firstOrNull { it.name == stored } ?: FoundBy.OWN_QUALITY_CONTROL

    fun dispositionOf(stored: String?): Disposition? = Disposition.entries.firstOrNull { it.name == stored }
}
