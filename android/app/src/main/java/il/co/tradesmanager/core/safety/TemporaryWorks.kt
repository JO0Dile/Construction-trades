package il.co.tradesmanager.core.safety

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Propping, formwork, shoring, façade retention, edge protection, trench
 * support: the structures that hold the building up while it cannot hold
 * itself, and come down again.
 *
 * They fail differently from permanent work. A permanent structure that is
 * wrong is usually wrong slowly; temporary works fail at the moment somebody
 * loads them or the moment somebody takes them away, and both of those moments
 * are decisions a person makes on site. So the register is built around two
 * gates rather than a status field:
 *
 *  - **Permit to load.** It may not carry anything until it has been designed,
 *    independently checked, erected, and inspected against that design.
 *  - **Permit to strike.** It may not come down until somebody with the
 *    authority to say so has said so — and, where it is holding up fresh
 *    concrete, until the concrete has had the days the engineer specified.
 *
 * The second gate is the one that kills people. Striking props under a slab
 * that has not reached strength drops the slab, and the decision is usually
 * made by whoever needs the props for the next floor. It is almost always
 * verbal, and afterwards nobody can say who released it.
 */
object TemporaryWorks {

    /**
     * The number of days the app offers when nobody has typed one.
     *
     * Deliberately conservative and deliberately not authoritative. Striking
     * times depend on the cement, the element, the weather and the load, and
     * the number that matters is the one the engineer wrote on the design.
     * This is a default that errs towards leaving props in, not a calculation.
     */
    const val DEFAULT_STRIKING_DAYS = 14L

    /**
     * How hard the design has to be checked, by how much can go wrong.
     *
     * Following the categories a temporary works coordinator already works to:
     * a category rises with the consequences of getting it wrong, and the
     * higher ones need somebody independent of the designer.
     */
    enum class CheckCategory {
        /** Standard solutions used within their published limits. */
        ZERO,

        /** Simple designs, checked by another member of the design team. */
        ONE,

        /** Checked by somebody not involved in the design. */
        TWO,

        /** Checked by an organisation independent of the designer's. */
        THREE,
        ;

        /** Whether the check has to come from outside the design team. */
        val needsIndependentChecker: Boolean get() = this == TWO || this == THREE
    }

    enum class LoadBlocker {
        /** No design on record. A prop somebody guessed at is not a design. */
        NO_DESIGN,

        /** Designed but never checked. */
        NOT_CHECKED,

        /** The checker is the designer, on a category that forbids it. */
        CHECKER_NOT_INDEPENDENT,

        /** Not built yet. */
        NOT_ERECTED,

        /** Built, but nobody has confirmed it matches the design. */
        NOT_INSPECTED,

        /** Already taken down. */
        STRUCK,
    }

    enum class StrikeBlocker {
        /** Nothing standing to strike. */
        NOT_ERECTED,

        /** Already down. */
        ALREADY_STRUCK,

        /**
         * Holding up concrete that has not had its days yet.
         *
         * The app is not judging the strength of the concrete. It is refusing
         * to let anybody strike before the number the engineer wrote down.
         */
        CONCRETE_TOO_YOUNG,

        /** Nobody with the authority has released it. */
        NOT_RELEASED,
    }

    /**
     * Everything stopping this from taking load.
     *
     * All of it at once, for the same reason the lift gate reports all of it at
     * once: a list that reveals one problem at a time is a list somebody walks
     * back and forth for.
     */
    fun loadBlockers(
        designReference: String?,
        category: CheckCategory,
        checkedAt: Long?,
        checkerName: String?,
        designerName: String?,
        erectedAt: Long?,
        inspectedAt: Long?,
        struckAt: Long?,
    ): Set<LoadBlocker> {
        val found = linkedSetOf<LoadBlocker>()
        if (designReference.isNullOrBlank()) found += LoadBlocker.NO_DESIGN
        if (checkedAt == null) found += LoadBlocker.NOT_CHECKED
        if (category.needsIndependentChecker &&
            checkerName != null &&
            designerName != null &&
            checkerName.trim().equals(designerName.trim(), ignoreCase = true)
        ) {
            found += LoadBlocker.CHECKER_NOT_INDEPENDENT
        }
        if (erectedAt == null) found += LoadBlocker.NOT_ERECTED
        if (inspectedAt == null) found += LoadBlocker.NOT_INSPECTED
        if (struckAt != null) found += LoadBlocker.STRUCK
        return found
    }

    /** Everything stopping this from coming down. */
    fun strikeBlockers(
        erectedAt: Long?,
        struckAt: Long?,
        supportsPourAt: Long?,
        minimumStrikingDays: Long,
        releasedAt: Long?,
        now: Long,
        zone: ZoneId,
    ): Set<StrikeBlocker> {
        val found = linkedSetOf<StrikeBlocker>()
        if (erectedAt == null) found += StrikeBlocker.NOT_ERECTED
        if (struckAt != null) found += StrikeBlocker.ALREADY_STRUCK
        if (supportsPourAt != null &&
            daysSince(supportsPourAt, now, zone) < minimumStrikingDays
        ) {
            found += StrikeBlocker.CONCRETE_TOO_YOUNG
        }
        if (releasedAt == null) found += StrikeBlocker.NOT_RELEASED
        return found
    }

    fun mayLoad(blockers: Set<LoadBlocker>): Boolean = blockers.isEmpty()

    fun mayStrike(blockers: Set<StrikeBlocker>): Boolean = blockers.isEmpty()

    /**
     * Days the concrete still owes before the props may come out; zero once it
     * has had them.
     */
    fun daysStillOwed(
        supportsPourAt: Long?,
        minimumStrikingDays: Long,
        now: Long,
        zone: ZoneId,
    ): Long {
        if (supportsPourAt == null) return 0L
        return (minimumStrikingDays - daysSince(supportsPourAt, now, zone)).coerceAtLeast(0L)
    }

    /**
     * Whole days between two instants, counted on the site's own calendar.
     *
     * Calendar days rather than twenty-four-hour blocks, for the same reason
     * the scaffold register counts them that way: "fourteen days" is a
     * statement about dates, and Israel's clocks move twice a year.
     */
    private fun daysSince(from: Long, now: Long, zone: ZoneId): Long {
        val fromDate = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val nowDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(fromDate, nowDate)
    }
}
