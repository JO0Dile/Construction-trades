package il.co.tradesmanager.core.people

/**
 * How close a ticket is to running out.
 *
 * This is not a formatting helper. On an Israeli site a lapsed work-at-height
 * or site-manager certificate is a legal exposure for the company and a
 * genuine danger for the person holding it, and the moment it matters is the
 * moment somebody is already on the scaffold. So the app warns before it
 * lapses, not on the day, and the window is long enough to actually renew.
 */
object Expiry {

    enum class State {
        /** Nothing to watch: this ticket does not expire. */
        NO_EXPIRY,
        VALID,
        /** Inside the renewal window — still legal, but book the course now. */
        EXPIRING_SOON,
        EXPIRED,
    }

    /**
     * Thirty days. Long enough to get a place on a renewal course, short
     * enough that the warning still means something when it appears.
     */
    const val WARNING_DAYS = 30L

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    fun state(expiresOn: Long?, now: Long): State {
        if (expiresOn == null) return State.NO_EXPIRY
        val days = daysUntil(expiresOn, now)
        return when {
            days < 0L -> State.EXPIRED
            days <= WARNING_DAYS -> State.EXPIRING_SOON
            else -> State.VALID
        }
    }

    /**
     * Whole days from [now] until [expiresOn]; negative once it has passed.
     *
     * Floored rather than rounded, so a ticket that runs out in twenty-three
     * hours reads "0 days" and not "1 day". Rounding up here would let someone
     * plan a day they do not have.
     */
    fun daysUntil(expiresOn: Long, now: Long): Long =
        Math.floorDiv(expiresOn - now, DAY_MILLIS)

    /** Sorts the ones that need attention to the top: expired, then soonest. */
    fun urgency(expiresOn: Long?, now: Long): Long = expiresOn?.let { daysUntil(it, now) }
        ?: Long.MAX_VALUE
}
