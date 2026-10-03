package il.co.tradesmanager.core.evidence

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Asking somebody to look before the work is covered up.
 *
 * The steel in a slab, the membrane under a screed, the pipes in a wall: once
 * the concrete, the screed or the plaster is on, nobody can see them again,
 * and "the engineer looked at it" is worth exactly as much as the record that
 * says who, when, and what was said. So each request gets a number, what is to
 * be seen and where, who was asked, and the day the site wants them; and the
 * answer -- passed, passed with comments, or failed and why -- is written once.
 *
 * A failed inspection is not edited into a pass. The steel is fixed and a new
 * request is raised against the failed one, so the record keeps both: that it
 * was wrong, and that it was put right.
 *
 * Nothing here says which elements need an inspection or who may give one.
 * That is the job's specification and the designer's call; this only keeps
 * the record, and shows what is still waiting.
 */
object Inspections {

    enum class Kind {
        /** The steel, before a pour. */
        REINFORCEMENT,
        FORMWORK,

        /** A membrane, usually with a flood test, before it is screeded or backfilled. */
        WATERPROOFING,

        /** Pipework held under pressure before it is closed in. */
        PIPE_PRESSURE,
        ELECTRICAL,
        OTHER,
    }

    enum class Result {
        PASSED,

        /** Passed, with something to do that does not hold the work up. */
        PASSED_WITH_COMMENTS,
        FAILED,
    }

    enum class Refusal {
        /** Nothing said about what is to be inspected or where. */
        BLANK_ELEMENT,

        /** Asked of nobody. */
        NOBODY_ASKED,

        /** Wanted on a day before the one it was asked. */
        WANTED_TOO_EARLY,

        /** The result goes in once. */
        ALREADY_DECIDED,

        /** A result without the name of whoever gave it is not a result. */
        NO_INSPECTOR,

        /** A failure with no reason gives nobody anything to fix. */
        FAILED_WITHOUT_REASON,

        /** "Passed with comments" and no comments. */
        COMMENTS_MISSING,

        /** Only a failed inspection is inspected again. */
        NOT_FAILED,

        /** It already has a re-inspection; that one is the one to follow. */
        ALREADY_REINSPECTED,

        /** A pour is cleared by a passed inspection, and only by one of the steel or the forms. */
        NOT_PASSED,
        NOT_FOR_A_POUR,

        /** Each inspection clears one pour: the next slab is new steel. */
        ALREADY_CLEARED_A_POUR,

        /** An inspection on one job does not clear a pour on another. */
        OTHER_JOB,
    }

    enum class State {
        /** Past the day the site wanted it, and nobody has come. */
        OVERDUE,

        /** Failed, and not yet asked again. The work it covers is held. */
        FAILED,
        WAITING,
        PASSED,

        /** Failed once; a later request is the one to follow. */
        REINSPECTED,
    }

    /** The kinds that stand between the steel and the concrete. */
    val POUR_KINDS: Set<Kind> = setOf(Kind.REINFORCEMENT, Kind.FORMWORK)

    fun requestRefusal(element: String, requestedOf: String, requestedAt: Long, wantedOn: Long?, zone: ZoneId): Refusal? = when {
        element.isBlank() -> Refusal.BLANK_ELEMENT
        requestedOf.isBlank() -> Refusal.NOBODY_ASKED
        wantedOn != null && day(wantedOn, zone) < day(requestedAt, zone) -> Refusal.WANTED_TOO_EARLY
        else -> null
    }

    fun resultRefusal(result: Result, inspectorName: String, comments: String, decidedAt: Long?): Refusal? = when {
        decidedAt != null -> Refusal.ALREADY_DECIDED
        inspectorName.isBlank() -> Refusal.NO_INSPECTOR
        result == Result.FAILED && comments.isBlank() -> Refusal.FAILED_WITHOUT_REASON
        result == Result.PASSED_WITH_COMMENTS && comments.isBlank() -> Refusal.COMMENTS_MISSING
        else -> null
    }

    /** Asking again after a failure: only of a failed one, and only once. */
    fun reinspectRefusal(previous: Result?, alreadyReinspected: Boolean): Refusal? = when {
        previous != Result.FAILED -> Refusal.NOT_FAILED
        alreadyReinspected -> Refusal.ALREADY_REINSPECTED
        else -> null
    }

    fun clearPourRefusal(
        kind: Kind,
        result: Result?,
        clearedPourId: String?,
        inspectionProjectId: String,
        pourProjectId: String,
    ): Refusal? = when {
        inspectionProjectId != pourProjectId -> Refusal.OTHER_JOB
        kind !in POUR_KINDS -> Refusal.NOT_FOR_A_POUR
        !passed(result) -> Refusal.NOT_PASSED
        clearedPourId != null -> Refusal.ALREADY_CLEARED_A_POUR
        else -> null
    }

    fun passed(result: Result?): Boolean = result == Result.PASSED || result == Result.PASSED_WITH_COMMENTS

    /**
     * Overdue from the day after the one it was wanted, on the site's own
     * calendar: an inspector asked for Thursday who comes on Thursday
     * evening came on the day.
     */
    fun state(result: Result?, wantedOn: Long?, reinspected: Boolean, now: Long, zone: ZoneId): State = when {
        result == Result.FAILED && reinspected -> State.REINSPECTED
        result == Result.FAILED -> State.FAILED
        passed(result) -> State.PASSED
        wantedOn != null && day(now, zone) > day(wantedOn, zone) -> State.OVERDUE
        else -> State.WAITING
    }

    /** Still standing between the work and covering it up. */
    fun outstanding(state: State): Boolean = state == State.OVERDUE || state == State.FAILED || state == State.WAITING

    /** Overdue, then failed, then waiting, then done; within each, the oldest request first. */
    fun order(rows: List<Pair<State, Long>>): List<Int> =
        rows.indices.sortedWith(compareBy({ rows[it].first.ordinal }, { rows[it].second }))

    /** "IR-001": inspection requests, numbered per job in the order asked. */
    fun reference(countOnJob: Int): String = "IR-" + (countOnJob + 1).toString().padStart(3, '0')

    /** A stored kind or result that this version does not know reads as the catch-all, not a crash. */
    fun kindOf(stored: String): Kind = Kind.entries.firstOrNull { it.name == stored } ?: Kind.OTHER

    fun resultOf(stored: String?): Result? = stored?.let { name -> Result.entries.firstOrNull { it.name == name } }

    private fun day(at: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
}
