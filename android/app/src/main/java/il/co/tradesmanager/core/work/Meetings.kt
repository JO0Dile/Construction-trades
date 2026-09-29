package il.co.tradesmanager.core.work

import java.time.LocalDate

/**
 * The job's meetings -- the weekly coordination with the trades, the
 * progress meeting with the client, the safety committee -- and the points
 * agreed at them.
 *
 * The minutes of a site meeting are read for one thing: what was agreed,
 * by whom, by when, and whether it happened. So each meeting keeps who was
 * there and what was said, and each point it raises is numbered under it
 * ("MT-004/2"), with an owner and a date, and stays open from meeting to
 * meeting until somebody closes it with what was done. A point past its
 * date is shown first; the meeting that raised it is kept as it was.
 */
object Meetings {

    enum class Kind { COORDINATION, PROGRESS, SAFETY, DESIGN, OTHER }

    enum class Refusal {
        HELD_IN_FUTURE,
        BLANK_ACTION,
        DUE_BEFORE_MEETING,
        BLANK_CLOSING_NOTE,
        ALREADY_CLOSED,
    }

    enum class ActionState {
        /** Open, and its date has passed. */
        OVERDUE,
        OPEN,
        DONE,
    }

    /** Minutes are written after the meeting, never before it. */
    fun meetingRefusal(heldOn: LocalDate, today: LocalDate): Refusal? =
        if (heldOn.isAfter(today)) Refusal.HELD_IN_FUTURE else null

    fun actionRefusal(text: String, dueOn: LocalDate?, heldOn: LocalDate): Refusal? = when {
        text.isBlank() -> Refusal.BLANK_ACTION
        dueOn != null && dueOn.isBefore(heldOn) -> Refusal.DUE_BEFORE_MEETING
        else -> null
    }

    /** Closed with what was done: "done" alone tells the next meeting nothing. */
    fun closeRefusal(note: String, closed: Boolean): Refusal? = when {
        closed -> Refusal.ALREADY_CLOSED
        note.isBlank() -> Refusal.BLANK_CLOSING_NOTE
        else -> null
    }

    /** Overdue from the day after its date. */
    fun actionState(dueOn: LocalDate?, closed: Boolean, today: LocalDate): ActionState = when {
        closed -> ActionState.DONE
        dueOn != null && dueOn.isBefore(today) -> ActionState.OVERDUE
        else -> ActionState.OPEN
    }

    /**
     * Overdue first, then open, then done; within the open ones, the soonest
     * date first and no date last; within done, the most recently closed first.
     */
    fun actionOrder(rows: List<ActionSort>): List<Int> =
        rows.indices.sortedWith(
            compareBy<Int>({ rows[it].state.ordinal })
                .thenBy { if (rows[it].state == ActionState.DONE) 0L else rows[it].dueOnDay ?: Long.MAX_VALUE }
                .thenByDescending { rows[it].closedAt ?: 0L }
                .thenBy { rows[it].reference },
        )

    data class ActionSort(val state: ActionState, val dueOnDay: Long?, val closedAt: Long?, val reference: String)

    /** "MT-001": meetings, numbered per job. */
    fun reference(countOnJob: Int): String = "MT-" + (countOnJob + 1).toString().padStart(3, '0')

    /** "MT-004/2": the second point raised at MT-004. */
    fun actionReference(meetingReference: String, number: Int): String = "$meetingReference/$number"

    fun kindOf(stored: String): Kind = Kind.entries.firstOrNull { it.name == stored } ?: Kind.OTHER
}
