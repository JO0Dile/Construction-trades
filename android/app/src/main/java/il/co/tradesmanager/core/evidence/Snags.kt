package il.co.tradesmanager.core.evidence

/**
 * Snagging: the defects found on a job, and whether they are actually done.
 *
 * The distinction the whole thing turns on is between *fixed* and *closed*.
 * Fixed is a claim — the person who did the work says they did it. Closed is a
 * verification — somebody else went and looked. A snag list that treats those
 * as the same thing is a list a subcontractor can complete on their own phone
 * without leaving the van, and it is worth nothing at handover.
 *
 * So a snag that has been marked fixed is still outstanding. It stays on the
 * count, and on the screen, until somebody checks it.
 */
object Snags {

    /** What is stored: what somebody did, not what it means. */
    object Status {
        const val OPEN = "OPEN"

        /** The work is claimed done, by whoever did it. Not verified. */
        const val FIXED = "FIXED"

        /** Checked and accepted. The only status that is finished. */
        const val CLOSED = "CLOSED"

        /** Checked and not accepted. Back to the trade who claimed it. */
        const val REJECTED = "REJECTED"
    }

    enum class State {
        OPEN,

        /** Claimed fixed, waiting on somebody to go and look. */
        AWAITING_CHECK,

        CLOSED,

        /** Somebody looked, and it is not done. */
        REJECTED,
        ;

        /**
         * Everything except closed. A claim is not a completion, so
         * [AWAITING_CHECK] counts against the job exactly as [OPEN] does.
         */
        val isOutstanding: Boolean get() = this != CLOSED
    }

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    /**
     * An unknown status reads as [State.OPEN] rather than closed.
     *
     * Least privilege, applied to a record: a status this build does not
     * understand must never be the one that makes a defect disappear off the
     * list.
     */
    fun state(status: String): State = when (status) {
        Status.FIXED -> State.AWAITING_CHECK
        Status.CLOSED -> State.CLOSED
        Status.REJECTED -> State.REJECTED
        else -> State.OPEN
    }

    /**
     * Past its date and not finished.
     *
     * A snag claimed fixed yesterday but never checked is still overdue,
     * because the date is a date by which it has to be *done*, and nobody has
     * established that it is.
     */
    fun isOverdue(status: String, dueOn: Long?, now: Long): Boolean {
        if (dueOn == null) return false
        return state(status).isOutstanding && now >= dueOn
    }

    /** Whole days past the date; zero before it. Floored, never negative. */
    fun daysOverdue(dueOn: Long?, now: Long): Long {
        if (dueOn == null || now < dueOn) return 0L
        return Math.floorDiv(now - dueOn, DAY_MILLIS)
    }

    /** Only somebody who has not claimed it yet can claim it. */
    fun canMarkFixed(status: String): Boolean =
        status == Status.OPEN || status == Status.REJECTED

    /**
     * Only a claim can be checked.
     *
     * Closing an open snag directly is deliberately not possible: it would
     * collapse the two steps back into one and take the point of the list
     * with it.
     */
    fun canVerify(status: String): Boolean = status == Status.FIXED

    /**
     * Where a job stands on its defects.
     *
     * [blocking] is the number that decides handover, and it is separate from
     * [outstanding] on purpose. A scuff to touch up next week is a real snag
     * that should stay on the list, and pretending it holds up a building is
     * how snag lists end up ignored.
     */
    data class Handover(
        val total: Int,
        val closed: Int,
        val outstanding: Int,
        val awaitingCheck: Int,
        val overdue: Int,
        val blocking: Int,
    ) {
        /** Nothing left that anybody said must be done first. */
        val isReady: Boolean get() = blocking == 0

        /** For a progress bar. Zero snags reads as nothing done, not as done. */
        val fraction: Double get() = if (total == 0) 0.0 else closed.toDouble() / total
    }

    /**
     * Counts a job's snags. Takes the three facts about each one rather than a
     * database row, so the arithmetic stays testable and knows nothing about
     * where snags are stored.
     */
    fun handover(
        snags: List<Triple<String, Long?, Boolean>>,
        now: Long,
    ): Handover {
        var closed = 0
        var outstanding = 0
        var awaiting = 0
        var overdue = 0
        var blocking = 0
        snags.forEach { (status, dueOn, blocksHandover) ->
            val state = state(status)
            if (state == State.CLOSED) closed++
            if (state.isOutstanding) {
                outstanding++
                if (blocksHandover) blocking++
                if (isOverdue(status, dueOn, now)) overdue++
            }
            if (state == State.AWAITING_CHECK) awaiting++
        }
        return Handover(
            total = snags.size,
            closed = closed,
            outstanding = outstanding,
            awaitingCheck = awaiting,
            overdue = overdue,
            blocking = blocking,
        )
    }
}
