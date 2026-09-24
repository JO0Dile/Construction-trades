package il.co.tradesmanager.core.work

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Materials sent for approval before they are ordered.
 *
 * The tile, the waterproofing product, the window system: the specification
 * names one, the contractor proposes one, and the architect or the
 * supervisor says yes, yes with notes, or no. The one that hurts is the tile
 * ordered on a nod in a corridor, laid over two floors, and rejected at
 * handover. So each submittal gets a number, what was proposed, to whom it
 * went, and the day the answer is needed to order in time; and the decision
 * is written once, with the name of whoever made it.
 *
 * A rejected submittal keeps its number and goes up a revision, the way the
 * trade numbers them: MS-004 rev. 1 is the second go at the same thing. The
 * rejected one stays on the record under it.
 *
 * Whether a material conforms is the designer's call under the specification;
 * this keeps the record of what was asked and what was said.
 */
object Submittals {

    enum class Decision {
        APPROVED,

        /** Approved, with conditions to follow -- the notes are the conditions. */
        APPROVED_AS_NOTED,

        /** Not this; revise and submit again. */
        REJECTED,
    }

    enum class Refusal {
        /** Nothing said about what is being proposed. */
        BLANK_ITEM,
        NOBODY_ASKED,
        NEEDED_BY_TOO_EARLY,

        /** The decision goes in once. */
        ALREADY_DECIDED,
        NO_REVIEWER,
        REJECTED_WITHOUT_REASON,
        NOTES_MISSING,

        /** Only a rejected submittal is submitted again. */
        NOT_REJECTED,
        ALREADY_RESUBMITTED,
    }

    enum class State {
        /** Past the day the answer was needed by, and none has come. */
        OVERDUE,

        /** Rejected, and not yet submitted again. */
        REJECTED,
        WAITING,
        APPROVED,

        /** Rejected once; a later revision is the one to follow. */
        SUPERSEDED,
    }

    fun submitRefusal(item: String, submittedTo: String, submittedAt: Long, neededBy: Long?, zone: ZoneId): Refusal? = when {
        item.isBlank() -> Refusal.BLANK_ITEM
        submittedTo.isBlank() -> Refusal.NOBODY_ASKED
        neededBy != null && day(neededBy, zone) < day(submittedAt, zone) -> Refusal.NEEDED_BY_TOO_EARLY
        else -> null
    }

    fun decisionRefusal(decision: Decision, reviewerName: String, notes: String, decidedAt: Long?): Refusal? = when {
        decidedAt != null -> Refusal.ALREADY_DECIDED
        reviewerName.isBlank() -> Refusal.NO_REVIEWER
        decision == Decision.REJECTED && notes.isBlank() -> Refusal.REJECTED_WITHOUT_REASON
        decision == Decision.APPROVED_AS_NOTED && notes.isBlank() -> Refusal.NOTES_MISSING
        else -> null
    }

    fun resubmitRefusal(previous: Decision?, alreadyResubmitted: Boolean): Refusal? = when {
        previous != Decision.REJECTED -> Refusal.NOT_REJECTED
        alreadyResubmitted -> Refusal.ALREADY_RESUBMITTED
        else -> null
    }

    fun approved(decision: Decision?): Boolean = decision == Decision.APPROVED || decision == Decision.APPROVED_AS_NOTED

    /** Overdue from the day after the needed-by date, on the site's own calendar. */
    fun state(decision: Decision?, neededBy: Long?, resubmitted: Boolean, now: Long, zone: ZoneId): State = when {
        decision == Decision.REJECTED && resubmitted -> State.SUPERSEDED
        decision == Decision.REJECTED -> State.REJECTED
        approved(decision) -> State.APPROVED
        neededBy != null && day(now, zone) > day(neededBy, zone) -> State.OVERDUE
        else -> State.WAITING
    }

    /** Still standing between the material and the order for it. */
    fun outstanding(state: State): Boolean = state == State.OVERDUE || state == State.REJECTED || state == State.WAITING

    /** Overdue, then rejected, then waiting, then done; within each, the oldest first. */
    fun order(rows: List<Pair<State, Long>>): List<Int> =
        rows.indices.sortedWith(compareBy({ rows[it].first.ordinal }, { rows[it].second }))

    /** "MS-001": material submittals, numbered per job. A revision keeps the number. */
    fun reference(countOnJob: Int): String = "MS-" + (countOnJob + 1).toString().padStart(3, '0')

    fun decisionOf(stored: String?): Decision? = stored?.let { name -> Decision.entries.firstOrNull { it.name == name } }

    private fun day(at: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
}
