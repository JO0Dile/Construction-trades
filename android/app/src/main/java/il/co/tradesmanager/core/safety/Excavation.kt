package il.co.tradesmanager.core.safety

import java.time.Instant
import java.time.ZoneId

/**
 * Trenches and excavations, and whether anybody may go into one.
 *
 * Two things kill people in excavations and they are not the same thing. One is
 * the sides coming in — a cubic metre of soil weighs about a tonne and a half,
 * and somebody buried to the chest cannot be pulled out by hand. The other is
 * what was already in the ground: a live cable, a gas main, a water main that
 * floods the trench with the man still in it.
 *
 * The second is decided before the first spade goes in and cannot be undone
 * afterwards, which is why locating services is a gate here rather than a
 * checklist item somewhere else.
 *
 * The inspection window is a **day**, not a week. A trench is inspected before
 * each shift because it changes overnight: it rains, the sides dry and crack,
 * a lorry parks near the edge. An inspection from yesterday is not an
 * inspection of the trench somebody is about to climb into today.
 */
object Excavation {

    /**
     * Below this depth a trench is a hole; at or above it, the sides have to be
     * held back somehow.
     *
     * A round number from the regulations rather than from soil mechanics.
     * Ground collapses at less than this and the register does not pretend
     * otherwise — it is the depth below which nobody is *required* to have
     * decided, not the depth below which nothing can happen.
     */
    const val SUPPORT_REQUIRED_METRES = 1.2

    /** How the sides are being held back. */
    enum class Support {
        /** Nothing. Only honest below [SUPPORT_REQUIRED_METRES]. */
        NONE,

        /** Cut back to a safe angle. */
        BATTERED,

        /** Trench boxes, sheets, props. */
        SHORED,

        /** Sheet piles or similar, designed as temporary works. */
        PILED,
        ;

        val holdsTheSidesBack: Boolean get() = this != NONE
    }

    enum class Blocker {
        /** Nobody has recorded what is buried here. */
        SERVICES_NOT_LOCATED,

        /** Deep enough to need support, and none recorded. */
        NO_SUPPORT,

        /** Never inspected. */
        NEVER_INSPECTED,

        /** The last inspection was on an earlier day. */
        NOT_INSPECTED_TODAY,

        /** The last inspection failed. */
        LAST_INSPECTION_FAILED,

        /**
         * Rain, a collapse, vibration, or a load put near the edge since the
         * last inspection. Anything that could have changed the sides voids the
         * inspection the way an alteration voids a scaffold's.
         */
        DISTURBED,

        /** Filled in. */
        BACKFILLED,
    }

    /**
     * Whether an inspection covers the moment somebody is climbing in.
     *
     * Same calendar day in the site's own zone. Not twenty-four hours: a trench
     * inspected at seven last night is not inspected for the shift starting at
     * six this morning, and a rolling window would say it was.
     */
    fun inspectionCoversNow(lastInspectedAt: Long?, now: Long, zone: ZoneId): Boolean {
        if (lastInspectedAt == null) return false
        val inspected = Instant.ofEpochMilli(lastInspectedAt).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return inspected == today
    }

    /** Whether this depth obliges somebody to have decided about the sides. */
    fun needsSupport(depthMetres: Double?): Boolean =
        depthMetres != null && depthMetres >= SUPPORT_REQUIRED_METRES

    /**
     * Everything stopping somebody going into this trench, all at once.
     *
     * [depthMetres] null is treated as not yet deep enough to oblige a decision
     * rather than as a blocker of its own: a trench nobody has measured is
     * usually a trench nobody has dug yet, and inventing a blocker for it would
     * put a red banner on every excavation the moment it is created.
     */
    fun blockers(
        servicesLocatedAt: Long?,
        depthMetres: Double?,
        support: Support,
        lastInspectedAt: Long?,
        lastInspectionPassed: Boolean,
        disturbedAt: Long?,
        backfilledAt: Long?,
        now: Long,
        zone: ZoneId,
    ): Set<Blocker> {
        val found = linkedSetOf<Blocker>()
        if (backfilledAt != null) found += Blocker.BACKFILLED
        if (servicesLocatedAt == null) found += Blocker.SERVICES_NOT_LOCATED
        if (needsSupport(depthMetres) && !support.holdsTheSidesBack) found += Blocker.NO_SUPPORT

        when {
            lastInspectedAt == null -> found += Blocker.NEVER_INSPECTED
            !lastInspectionPassed -> found += Blocker.LAST_INSPECTION_FAILED
            !inspectionCoversNow(lastInspectedAt, now, zone) -> found += Blocker.NOT_INSPECTED_TODAY
        }

        if (disturbedAt != null &&
            (lastInspectedAt == null || disturbedAt > lastInspectedAt)
        ) {
            found += Blocker.DISTURBED
        }
        return found
    }

    /** The only question somebody standing at the edge is asking. */
    fun mayEnter(blockers: Set<Blocker>): Boolean = blockers.isEmpty()

    /**
     * Sorts the trench somebody must deal with first to the top.
     *
     * Backfilled ones sink; everything else is ranked by how many things are
     * wrong with it, so the trench with no services check and no support sits
     * above the one that merely wants today's inspection.
     */
    fun urgency(blockers: Set<Blocker>): Int = when {
        blockers.contains(Blocker.BACKFILLED) -> Int.MAX_VALUE
        blockers.isEmpty() -> Int.MAX_VALUE - 1
        else -> -blockers.size
    }
}
