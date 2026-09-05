package il.co.tradesmanager.core.evidence

/**
 * How long ago somebody was last briefed.
 *
 * A toolbox talk register answers "did we brief the crew?" A safety officer
 * arriving on site asks the harder question: "who have we *not* briefed?" That
 * is the one this exists for, and the answer that matters most is the person
 * with no record at all — a subcontractor who started three weeks ago and has
 * never been in the room.
 */
object Briefings {

    enum class State {
        /** Never attended anything. The row that matters most. */
        NEVER,

        /** Long enough ago that it needs doing again. */
        DUE,

        RECENT,
    }

    /**
     * A quarter. Long enough that a working crew is not sat in briefings every
     * fortnight, short enough that "we covered that" still means this job.
     */
    const val REFRESH_DAYS = 90L

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun state(lastAttendedAt: Long?, now: Long): State {
        if (lastAttendedAt == null) return State.NEVER
        return if (daysSince(lastAttendedAt, now) >= REFRESH_DAYS) State.DUE else State.RECENT
    }

    /**
     * Whole days since the last briefing. Never negative: a talk recorded with
     * tomorrow's date is a typo, not time travel, and reading it as "-1 days
     * ago" would put it at the top of the overdue list.
     */
    fun daysSince(lastAttendedAt: Long, now: Long): Long =
        Math.floorDiv(now - lastAttendedAt, DAY_MILLIS).coerceAtLeast(0L)

    /** Sorts the people who need briefing to the top: never first, then oldest. */
    fun urgency(lastAttendedAt: Long?, now: Long): Long =
        lastAttendedAt?.let { -daysSince(it, now) } ?: Long.MIN_VALUE
}
