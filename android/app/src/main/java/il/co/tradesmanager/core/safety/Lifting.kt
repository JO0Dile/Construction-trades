package il.co.tradesmanager.core.safety

import il.co.tradesmanager.core.people.Expiry

/**
 * Whether a lift may go ahead, and the reasons it may not.
 *
 * Everything a crane needs is known before the load leaves the ground and
 * forgotten while it is in the air. A crane at ninety-six per cent of its chart
 * at thirty metres is a different machine from the same crane at forty per
 * cent, and nobody recalculates that with a load swinging. A slinger whose
 * ticket ran out last week is exactly as good at slinging as he was a fortnight
 * ago and exactly as illegal to use.
 *
 * So this is a gate, not a form. [blockers] returns everything wrong at once
 * rather than the first thing wrong, because a plan that fails four checks and
 * reports one is a plan somebody fixes four times.
 */
object Lifting {

    /**
     * Above three quarters of the chart, a lift stops being routine.
     *
     * Not a legal line — the chart is the legal line — but the point at which
     * the margin for a mis-estimated load, a gust, or a radius read off by a
     * metre has gone. Plans above it are marked so somebody senior looks.
     */
    const val HEAVY_UTILISATION = 0.75

    /** Tower cranes come out of service around twenty metres a second. */
    const val DEFAULT_WIND_LIMIT_KMH = 72.0

    enum class Blocker {
        /** Nobody has said what it weighs. Everything else is guesswork. */
        NO_WEIGHT,

        /** No capacity from the duty chart at the radius being worked. */
        NO_CAPACITY,

        /** Gross load is over the chart. Not a warning. */
        OVERLOADED,

        NO_OPERATOR,
        NO_SLINGER,
        NO_BANKSMAN,

        /** Somebody on the lift is working on a ticket that has run out. */
        TICKET_EXPIRED,

        /** The appliance's own periodic inspection has lapsed. */
        APPLIANCE_UNCERTIFIED,

        /** Wind over the limit for this appliance. */
        TOO_WINDY,

        /** Written but never signed off by anyone. */
        NOT_APPROVED,
    }

    /**
     * The three people, and whether each is named and in ticket.
     *
     * A null state means nobody is in the role. [Expiry.State.NO_EXPIRY] is a
     * pass, not a gap: a licence that does not expire is still a licence.
     */
    data class Crew(
        val operator: Expiry.State?,
        val slinger: Expiry.State?,
        val banksman: Expiry.State?,
    )

    /**
     * What is actually on the hook.
     *
     * Slings, shackles, a spreader beam and the hook block all hang off the
     * same rope as the load. Leaving them out is the ordinary way a lift that
     * was planned at ninety per cent turns out to have been at a hundred and
     * four, and it is invisible on the day because the number on the plan
     * still reads ninety.
     */
    fun grossWeightKg(loadWeightKg: Double?, riggingWeightKg: Double?): Double? =
        if (loadWeightKg == null) null else loadWeightKg + (riggingWeightKg ?: 0.0)

    /**
     * Share of the chart this lift uses: 0.96 means ninety-six per cent.
     *
     * Null when either number is missing, because a lift with an unknown weight
     * has no utilisation — and returning zero for it would paint the most
     * dangerous plan on the screen green.
     */
    fun utilisation(grossWeightKg: Double?, capacityAtRadiusKg: Double?): Double? {
        if (grossWeightKg == null || capacityAtRadiusKg == null) return null
        if (capacityAtRadiusKg <= 0.0) return null
        return grossWeightKg / capacityAtRadiusKg
    }

    /** Over three quarters of the chart. Still legal; no longer routine. */
    fun isHeavy(utilisation: Double?): Boolean =
        utilisation != null && utilisation >= HEAVY_UTILISATION && utilisation <= 1.0

    /**
     * Everything wrong with this plan, at once.
     *
     * [windSpeedKmh] null means nobody has looked, which is not treated as a
     * blocker: the app has no anemometer and inventing one would train people
     * to ignore the wind row. A number over the limit is a blocker.
     */
    fun blockers(
        loadWeightKg: Double?,
        riggingWeightKg: Double?,
        capacityAtRadiusKg: Double?,
        crew: Crew,
        applianceCertificateExpiresOn: Long?,
        applianceCertificateRequired: Boolean,
        windSpeedKmh: Double?,
        windLimitKmh: Double,
        approvedAt: Long?,
        now: Long,
    ): Set<Blocker> {
        val found = linkedSetOf<Blocker>()
        val gross = grossWeightKg(loadWeightKg, riggingWeightKg)

        if (gross == null) found += Blocker.NO_WEIGHT
        if (capacityAtRadiusKg == null || capacityAtRadiusKg <= 0.0) found += Blocker.NO_CAPACITY
        val share = utilisation(gross, capacityAtRadiusKg)
        if (share != null && share > 1.0) found += Blocker.OVERLOADED

        if (crew.operator == null) found += Blocker.NO_OPERATOR
        if (crew.slinger == null) found += Blocker.NO_SLINGER
        if (crew.banksman == null) found += Blocker.NO_BANKSMAN
        // Expiring soon is not a blocker: the ticket is valid today, and
        // stopping a lift over a course booked for next month would teach
        // everybody to work around the app.
        if (listOfNotNull(crew.operator, crew.slinger, crew.banksman)
                .any { it == Expiry.State.EXPIRED }
        ) {
            found += Blocker.TICKET_EXPIRED
        }

        if (applianceCertificateRequired) {
            val state = Expiry.state(applianceCertificateExpiresOn, now)
            // Unlike a person's licence, a crane with no recorded certificate
            // is not assumed to have one that never expires. Periodic testing
            // is not optional, so a blank date is a missing certificate.
            if (applianceCertificateExpiresOn == null || state == Expiry.State.EXPIRED) {
                found += Blocker.APPLIANCE_UNCERTIFIED
            }
        }

        if (windSpeedKmh != null && windSpeedKmh > windLimitKmh) found += Blocker.TOO_WINDY
        if (approvedAt == null) found += Blocker.NOT_APPROVED
        return found
    }

    /** The only question. */
    fun mayLift(blockers: Set<Blocker>): Boolean = blockers.isEmpty()
}
