package il.co.tradesmanager.core.safety

import java.time.LocalDate

/**
 * What was found out after an incident, and what is being done so it does
 * not happen again.
 *
 * A report says what happened. The question the client's safety manager, the
 * insurer and the next foreman on the job all ask is the one after it: why,
 * and what changed. So each incident can carry one investigation -- what
 * directly caused it, what lay behind that, what was found -- and numbered
 * corrective actions, each with somebody to do it and a date, open until it
 * is closed with what was done.
 *
 * A serious injury or a death needs one; a near miss or a first-aid case may
 * have one. The investigation is closed only once it says what caused the
 * incident, names at least one thing behind it, and every action is done: a
 * closed investigation with an action still open is a promise marked kept.
 * When the law requires an investigation, by whom and within what time, is
 * a question for a qualified Israeli professional (see docs/COMPLIANCE.md);
 * the app's line is its own, and cautious.
 */
object Investigations {

    /** What lay behind it. Usually more than one. */
    enum class Cause {
        WAY_OF_WORKING,
        EQUIPMENT,
        SITE_CONDITIONS,
        PROTECTIVE_EQUIPMENT,
        SUPERVISION,
        TRAINING,
        PLANNING,
        COMMUNICATION,
        OTHER,
    }

    enum class Refusal {
        BLANK_IMMEDIATE_CAUSE,
        NO_CAUSE,
        ACTIONS_OPEN,
        ALREADY_CLOSED,
        BLANK_ACTION,
        DUE_BEFORE_INCIDENT,
        BLANK_CLOSING_NOTE,
        ACTION_ALREADY_CLOSED,
    }

    enum class State {
        /** Serious or worse, and nobody has started on it. */
        NEEDED,
        /** Started and not closed. */
        OPEN,
        CLOSED,
        /** A near miss or a first-aid case nobody has opened one for. Allowed. */
        NOT_STARTED,
    }

    enum class ActionState {
        /** Open, and its date has passed. */
        OVERDUE,
        OPEN,
        DONE,
    }

    /** A serious injury or a death is looked into. */
    fun required(severity: Incidents.Severity): Boolean = Incidents.needsEscalating(severity)

    fun state(severity: Incidents.Severity, started: Boolean, closed: Boolean): State = when {
        closed -> State.CLOSED
        started -> State.OPEN
        required(severity) -> State.NEEDED
        else -> State.NOT_STARTED
    }

    /** Waiting on somebody: required and not closed, whether started or not. */
    fun outstanding(state: State): Boolean = state == State.NEEDED || state == State.OPEN

    /**
     * Null when it can be closed. In the order somebody would put it right:
     * say what caused it, say what lay behind it, finish the actions.
     */
    fun closeRefusal(immediateCause: String?, causes: Set<Cause>, openActions: Int, closed: Boolean): Refusal? = when {
        closed -> Refusal.ALREADY_CLOSED
        immediateCause.isNullOrBlank() -> Refusal.BLANK_IMMEDIATE_CAUSE
        causes.isEmpty() -> Refusal.NO_CAUSE
        openActions > 0 -> Refusal.ACTIONS_OPEN
        else -> null
    }

    /** An action is something to do, and it cannot have been due before the thing it answers happened. */
    fun actionRefusal(text: String, dueOn: LocalDate?, incidentDay: LocalDate, investigationClosed: Boolean): Refusal? = when {
        investigationClosed -> Refusal.ALREADY_CLOSED
        text.isBlank() -> Refusal.BLANK_ACTION
        dueOn != null && dueOn.isBefore(incidentDay) -> Refusal.DUE_BEFORE_INCIDENT
        else -> null
    }

    /** Closed with what was done: "done" alone tells the next site nothing. */
    fun actionCloseRefusal(note: String, closed: Boolean): Refusal? = when {
        closed -> Refusal.ACTION_ALREADY_CLOSED
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
     * Overdue first, then open with the soonest date first and no date last,
     * then done; within the same, by number.
     */
    fun actionOrder(rows: List<ActionSort>): List<Int> =
        rows.indices.sortedWith(
            compareBy<Int>({ rows[it].state.ordinal })
                .thenBy { if (rows[it].state == ActionState.DONE) 0L else rows[it].dueOnDay ?: Long.MAX_VALUE }
                .thenBy { rows[it].number },
        )

    data class ActionSort(val state: ActionState, val dueOnDay: Long?, val number: Int)

    /** Stored as the names, comma separated, in the enum's order; unknown names are dropped. */
    fun encode(causes: Set<Cause>): String = Cause.entries.filter { it in causes }.joinToString(",") { it.name }

    fun decode(stored: String?): Set<Cause> =
        stored.orEmpty().split(',').mapNotNull { name -> Cause.entries.firstOrNull { it.name == name.trim() } }.toSet()
}
