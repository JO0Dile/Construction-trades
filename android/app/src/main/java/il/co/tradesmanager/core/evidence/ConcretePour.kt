package il.co.tradesmanager.core.evidence

/**
 * Ready-mixed concrete, and the clock that starts when it is batched.
 *
 * Concrete begins setting the moment water meets cement. A truck that has been
 * standing in traffic for two hours is carrying something that will still pour
 * and still look like concrete, and will not reach the strength the engineer
 * designed for. Nothing about it is visible afterwards: it goes into a slab,
 * the slab looks like a slab, and the problem surfaces years later or never.
 *
 * So the useful thing this can do is watch the clock on every truck and say
 * plainly when one has run out of time — before it is discharged, which is the
 * only moment anybody can still act on it.
 *
 * The limits are the common defaults rather than a quotation from anybody's
 * standard. The mix design and the supplier's ticket govern, they can differ,
 * and a site that has been told otherwise should follow what it has been told.
 */
object ConcretePour {

    /**
     * Ninety minutes from batching to discharge, the ordinary working figure.
     */
    const val WORKING_LIFE_MINUTES = 90L

    /**
     * Sixty in the heat. An Israeli summer afternoon is well past the point
     * where a full ninety minutes is safe, and the shortened window is the
     * whole reason a pour gets moved to first thing in the morning.
     */
    const val HOT_WEATHER_MINUTES = 60L

    /** At or above this, the shorter window applies. */
    const val HOT_WEATHER_CELSIUS = 30.0

    /**
     * A quarter of an hour. Long enough to get a truck to the pump and
     * discharged, short enough that the warning still means something.
     */
    const val WARNING_MINUTES = 15L

    private const val MINUTE_MILLIS = 60L * 1000L

    enum class State {
        /** Batched, not yet arrived. */
        EN_ROUTE,

        /** Here, with time in hand. */
        ON_SITE,

        /** Here, and close to the end of its working life. */
        EXPIRING,

        /**
         * Out of time. Whatever is in the drum should not go into the
         * structure, and this is the one state that is worth interrupting
         * somebody over.
         */
        EXPIRED,

        /** Discharged. The clock stopped when it went in. */
        PLACED,
        ;

        val needsAttention: Boolean get() = this == EXPIRING || this == EXPIRED
    }

    /** How long this load has, given the weather. */
    fun workingLifeMinutes(temperatureCelsius: Double?): Long =
        if (temperatureCelsius != null && temperatureCelsius >= HOT_WEATHER_CELSIUS) {
            HOT_WEATHER_MINUTES
        } else {
            WORKING_LIFE_MINUTES
        }

    /**
     * Where this load stands.
     *
     * Once discharged the state is [State.PLACED] whatever the clock says: the
     * concrete is in the structure and the question is now a matter for the
     * record rather than for the person holding the phone. Whether it went in
     * late is answered by [wasPlacedLate], which is what somebody reads
     * afterwards.
     */
    fun state(
        dispatchedAt: Long,
        arrivedAt: Long?,
        dischargedAt: Long?,
        now: Long,
        temperatureCelsius: Double? = null,
    ): State {
        if (dischargedAt != null) return State.PLACED
        val remaining = minutesLeft(dispatchedAt, now, temperatureCelsius)
        return when {
            remaining <= 0L -> State.EXPIRED
            remaining <= WARNING_MINUTES -> State.EXPIRING
            arrivedAt != null -> State.ON_SITE
            else -> State.EN_ROUTE
        }
    }

    /**
     * Whole minutes of working life left; zero once it has gone.
     *
     * Floored, so a load with ninety seconds left reads as one minute rather
     * than two. Rounding up on setting concrete would hand somebody a minute
     * that does not exist.
     */
    fun minutesLeft(dispatchedAt: Long, now: Long, temperatureCelsius: Double? = null): Long {
        val limit = workingLifeMinutes(temperatureCelsius)
        val elapsed = (now - dispatchedAt) / MINUTE_MILLIS
        return (limit - elapsed).coerceAtLeast(0L)
    }

    fun minutesSinceDispatch(dispatchedAt: Long, now: Long): Long =
        ((now - dispatchedAt) / MINUTE_MILLIS).coerceAtLeast(0L)

    /**
     * Whether a load that has already gone in went in outside its window.
     *
     * The question somebody asks afterwards, with an engineer in the room, and
     * the reason the dispatch time is worth writing down at all.
     */
    fun wasPlacedLate(
        dispatchedAt: Long,
        dischargedAt: Long?,
        temperatureCelsius: Double? = null,
    ): Boolean {
        if (dischargedAt == null) return false
        return minutesSinceDispatch(dispatchedAt, dischargedAt) >
            workingLifeMinutes(temperatureCelsius)
    }

    /**
     * Sorts the truck about to run out to the top.
     *
     * A load whose clock has stopped sinks to the bottom whichever way it
     * stopped. Rejection matters here as much as discharge: a truck that was
     * sent away never gets a discharge time, so without this it would go on
     * counting down, reach zero, and sit at the top of the list ahead of every
     * load somebody can still do something about.
     */
    fun urgency(
        dispatchedAt: Long,
        dischargedAt: Long?,
        now: Long,
        temperatureCelsius: Double?,
        rejected: Boolean = false,
    ): Long =
        if (dischargedAt != null || rejected) {
            Long.MAX_VALUE
        } else {
            minutesLeft(dispatchedAt, now, temperatureCelsius)
        }
}
