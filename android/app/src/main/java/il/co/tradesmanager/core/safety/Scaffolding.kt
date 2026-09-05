package il.co.tradesmanager.core.safety

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The scaffold register, and the seven days it turns on.
 *
 * Israeli construction regulations require a scaffold to be inspected before
 * it is first used, after any alteration, after weather that could have
 * affected it, and at least once every seven days — with the result written
 * down. The register is not paperwork about the scaffold; on a lapsed
 * inspection it is the reason nobody may climb it.
 *
 * Which is why the states below are not a traffic light with an amber that
 * means "carry on carefully". There are exactly two things a person standing
 * at the bottom of a scaffold needs from this: may I go up, or not. Everything
 * else — how many days are left, what the last inspector wrote — is detail
 * hung off that one answer, and [State.mayBeClimbed] is the only reading of it
 * the rest of the app is allowed to do for itself.
 */
object Scaffolding {

    /** Seven days, from the regulation. Not a preference to be tuned. */
    const val INSPECTION_INTERVAL_DAYS = 7L

    /**
     * How long before the deadline the register starts asking.
     *
     * A day, because arranging an inspection means finding a competent person
     * and a gap in their morning. Warning on the day it expires would mean
     * either an inspection nobody had time to arrange or a scaffold standing
     * idle, and the second one is what actually happens.
     */
    const val WARNING_DAYS = 1L

    enum class State {
        /** Inspected, passed, and inside the seven days. */
        SAFE,

        /** Passed, but the next inspection falls due today or tomorrow. */
        DUE_SOON,

        /** The seven days have run out. Not a warning — it may not be used. */
        OVERDUE,

        /** The last inspection failed. It stays failed until one passes. */
        FAILED,

        /**
         * Altered, struck by weather, or otherwise changed since it was last
         * passed. A scaffold that has been modified is a different scaffold,
         * and the inspection that passed the old one says nothing about it.
         */
        ALTERED,

        /** Erected and never inspected. */
        NEVER_INSPECTED,
        ;

        /**
         * The only question the screen is really answering.
         *
         * Deliberately narrow: everything that is not a passed inspection
         * inside its seven days is a scaffold nobody goes up. There is no
         * state here that means "probably fine".
         */
        val mayBeClimbed: Boolean get() = this == SAFE || this == DUE_SOON

        /** Whether somebody has to do something about this one today. */
        val needsAttention: Boolean get() = this != SAFE
    }

    /**
     * When the next inspection falls due.
     *
     * Seven calendar days in the site's own zone rather than a fixed
     * 168 hours, because Israel moves its clocks in March and October and one
     * week a year is 167 hours long. A fixed span would bring the deadline
     * forward by an hour on that week — or push it back by one on the other,
     * which is the direction that matters.
     */
    fun nextDueAt(lastInspectedAt: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(lastInspectedAt)
            .atZone(zone)
            .plusDays(INSPECTION_INTERVAL_DAYS)
            .toInstant()
            .toEpochMilli()

    /**
     * Where a scaffold stands.
     *
     * The order of the checks is the order of severity, and it is not
     * arbitrary: an alteration voids a passing inspection even when the seven
     * days have days left on them, and a failed inspection is not redeemed by
     * being recent.
     */
    fun state(
        lastInspectedAt: Long?,
        lastInspectionPassed: Boolean,
        alteredAt: Long?,
        now: Long,
        zone: ZoneId,
    ): State {
        if (lastInspectedAt == null) return State.NEVER_INSPECTED
        if (!lastInspectionPassed) return State.FAILED
        if (alteredAt != null && alteredAt > lastInspectedAt) return State.ALTERED

        val due = nextDueAt(lastInspectedAt, zone)
        if (now >= due) return State.OVERDUE
        return if (daysUntil(now, due, zone) <= WARNING_DAYS) State.DUE_SOON else State.SAFE
    }

    /**
     * Whole days from now until the next inspection is due; negative once it
     * has passed.
     *
     * Counted in local calendar days rather than in twenty-four-hour blocks,
     * because "due tomorrow" is a statement about the date on the wall, not
     * about the next 1,440 minutes. An inspection due at nine tomorrow morning
     * is due tomorrow whether it is now eight in the evening or eight in the
     * morning.
     */
    fun daysUntilDue(lastInspectedAt: Long, now: Long, zone: ZoneId): Long =
        daysUntil(now, nextDueAt(lastInspectedAt, zone), zone)

    private fun daysUntil(from: Long, to: Long, zone: ZoneId): Long {
        val fromDate = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val toDate = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(fromDate, toDate)
    }

    /**
     * Sorts the scaffold somebody must deal with first to the top.
     *
     * Everything unusable comes above everything usable, and inside those the
     * one that has been wrong longest comes first — a scaffold that went out of
     * date a fortnight ago is a worse fact about the site than one that went
     * out of date this morning.
     */
    fun urgency(state: State, lastInspectedAt: Long?, now: Long, zone: ZoneId): Long =
        if (!state.mayBeClimbed) {
            // Below every usable scaffold, and among themselves oldest first.
            // A never-inspected one has no date to sort by and goes to the very
            // top, which is where it belongs.
            Long.MIN_VALUE + (lastInspectedAt ?: 0L)
        } else {
            // Always non-negative here: a climbable scaffold has been inspected
            // and its deadline has not passed.
            daysUntilDue(requireNotNull(lastInspectedAt), now, zone)
        }
}
