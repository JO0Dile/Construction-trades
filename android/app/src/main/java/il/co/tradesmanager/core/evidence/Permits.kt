package il.co.tradesmanager.core.evidence

/**
 * Permits to work: whether a piece of dangerous work is authorised right now.
 *
 * A permit is not a record of a decision, it is a window in time. Somebody
 * signs a hot work permit at eight in the morning valid until five, and at ten
 * past five that permit authorises nothing at all — even though its status
 * column still says ISSUED, because nobody goes round changing database rows
 * at knocking-off time.
 *
 * So validity is derived from the clock every time it is asked for, and the
 * stored status only records what a person did. Getting that the wrong way
 * round is how a welder ends up working under a permit that expired while he
 * was having his tea, which is exactly the situation the permit exists to
 * prevent.
 */
object Permits {

    /** What is stored in the row: what a human did, not what the clock says. */
    object Status {
        /** Being filled in. Authorises nothing. */
        const val DRAFT = "DRAFT"

        /** Signed and handed over. Authorises work inside its window. */
        const val ISSUED = "ISSUED"

        /** Signed back: the work is done and the area has been made safe. */
        const val CLOSED = "CLOSED"

        /** Withdrawn before its time, usually because conditions changed. */
        const val CANCELLED = "CANCELLED"
    }

    /**
     * The kinds of work that need one.
     *
     * Held here as plain strings rather than an enum because they are written
     * into permit rows that have to keep reading correctly after this list
     * changes. The wording that goes with each — the precautions — lives in the
     * UI layer with the rest of the translated text.
     */
    object Type {
        const val HOT_WORK = "HOT_WORK"
        const val HEIGHT = "HEIGHT"
        const val CONFINED_SPACE = "CONFINED_SPACE"
        const val EXCAVATION = "EXCAVATION"
        const val ELECTRICAL = "ELECTRICAL"
    }

    /**
     * What the permit means at a given moment — the status and the clock read
     * together. This is what a screen shows and what a person acts on.
     */
    enum class State {
        DRAFT,

        /** Signed, but its window has not opened yet. */
        PENDING,

        /** Live. Work may proceed. */
        ACTIVE,

        /** Live, but not for much longer. */
        EXPIRING,

        /** The window has closed. Work must stop, permit or no permit. */
        EXPIRED,

        CLOSED,
        CANCELLED,
        ;

        /** The only two states in which work is actually authorised. */
        val authorisesWork: Boolean get() = this == ACTIVE || this == EXPIRING
    }

    /**
     * One hour. A permit is a shift-length thing, so the warning has to be
     * short enough to mean "finish up and sign back" rather than something a
     * person sees all morning and stops reading.
     */
    const val WARNING_MINUTES = 60L

    private const val MINUTE_MILLIS = 60L * 1000L

    /**
     * The permit's real state at [now].
     *
     * [validTo] being null means the permit has no end, which is a legitimate
     * shape for a long isolation — but it is never treated as expiring, so it
     * cannot be the accidental default: the screen makes an end time required
     * before a permit can be issued with a window at all.
     */
    fun state(status: String, validFrom: Long?, validTo: Long?, now: Long): State =
        when (status) {
            Status.CLOSED -> State.CLOSED
            Status.CANCELLED -> State.CANCELLED
            Status.ISSUED -> issuedState(validFrom, validTo, now)
            else -> State.DRAFT
        }

    private fun issuedState(validFrom: Long?, validTo: Long?, now: Long): State {
        if (validFrom != null && now < validFrom) return State.PENDING
        if (validTo == null) return State.ACTIVE
        if (now >= validTo) return State.EXPIRED
        return if (minutesLeft(validTo, now) <= WARNING_MINUTES) State.EXPIRING else State.ACTIVE
    }

    /**
     * Whole minutes left on the window; zero once it has passed.
     *
     * Floored, so a permit with ninety seconds left reads "1 minute" and not
     * "2". Rounding up on a permit gives somebody a minute that does not
     * exist, which is the wrong direction to be generous in.
     */
    fun minutesLeft(validTo: Long?, now: Long): Long {
        if (validTo == null) return Long.MAX_VALUE
        if (now >= validTo) return 0L
        return (validTo - now) / MINUTE_MILLIS
    }

    /**
     * Whether a permit may be signed and handed over.
     *
     * Every precaution has to be ticked first. The precautions are not advice
     * printed alongside the permit — they are what the permit consists of, and
     * a hot work permit issued without the fire watch arranged is a piece of
     * paper that makes the job look controlled while it is not.
     *
     * The window has to make sense too: a permit that ends before it starts is
     * a typo, and it would otherwise be issued straight into [State.EXPIRED],
     * where nobody would understand why the work was blocked.
     */
    fun canIssue(
        status: String,
        precautionsTotal: Int,
        precautionsChecked: Int,
        validFrom: Long?,
        validTo: Long?,
    ): Boolean {
        if (status != Status.DRAFT) return false
        if (precautionsTotal == 0 || precautionsChecked < precautionsTotal) return false
        if (validFrom == null || validTo == null) return false
        return validTo > validFrom
    }

    /**
     * How long somebody has to keep watching after the hot work stops.
     *
     * An hour. Hot work fires do not mostly start while the torch is lit —
     * they start afterwards, from a spark that fell into something an hour
     * ago and has been smouldering since, in a void nobody thought to look
     * into. The welding stopping is the moment the danger becomes invisible,
     * not the moment it ends.
     */
    const val FIRE_WATCH_MINUTES = 60L

    /** Which kinds of work leave something behind that has to be watched. */
    fun needsFireWatch(type: String): Boolean = type == Type.HOT_WORK

    /**
     * When the fire watch clock starts.
     *
     * The moment the work actually stopped, if somebody recorded it. Failing
     * that, the end of the permit's window — the latest the work could have
     * gone on for. Falling back that way is deliberately the conservative
     * direction: it can only ever hold the permit open longer than the truth,
     * never close it sooner.
     */
    fun fireWatchFrom(workStoppedAt: Long?, validTo: Long?): Long? = workStoppedAt ?: validTo

    /**
     * Minutes still to be watched; zero once the hour is up.
     *
     * Rounded **up**, unlike [minutesLeft], and the difference is the point.
     * There, flooring is generous in the safe direction — it tells somebody a
     * permit has less time left than it really does. Here the same rounding
     * would say "nothing left to wait" while thirty seconds of fire watch
     * remained, and let the permit be signed back early. The two numbers look
     * alike and pull opposite ways.
     *
     * With nothing to measure from — no stop time and no window end — the full
     * hour is still owed, so the permit cannot be closed by leaving fields
     * blank.
     */
    fun fireWatchMinutesLeft(workStoppedAt: Long?, validTo: Long?, now: Long): Long {
        val from = fireWatchFrom(workStoppedAt, validTo) ?: return FIRE_WATCH_MINUTES
        val ends = from + FIRE_WATCH_MINUTES * MINUTE_MILLIS
        if (now >= ends) return 0L
        return (ends - now + MINUTE_MILLIS - 1) / MINUTE_MILLIS
    }

    /**
     * Whether a permit may be signed back.
     *
     * An expired permit can still be closed, and normally has to be: the work
     * stopping is not the same event as the area being checked and handed
     * back, and the second one is the one that gets written down.
     *
     * On hot work it is not enough for the work to have stopped. The fire
     * watch has to have been kept for its hour, because signing the permit
     * back is what tells everybody the area is safe to leave — and an area
     * left at five past five with something smouldering in a void is exactly
     * how a site burns down at two in the morning with nobody in it.
     */
    fun canClose(
        status: String,
        type: String,
        workStoppedAt: Long?,
        validTo: Long?,
        now: Long,
    ): Boolean {
        if (status != Status.ISSUED) return false
        if (!needsFireWatch(type)) return true
        return fireWatchMinutesLeft(workStoppedAt, validTo, now) == 0L
    }

    /** Sorts what needs attention to the top: expiring soonest first. */
    fun urgency(status: String, validTo: Long?, now: Long): Long =
        if (status != Status.ISSUED || validTo == null) Long.MAX_VALUE else minutesLeft(validTo, now)
}
