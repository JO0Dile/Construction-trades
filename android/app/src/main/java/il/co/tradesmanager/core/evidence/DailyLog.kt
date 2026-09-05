package il.co.tradesmanager.core.evidence

import il.co.tradesmanager.core.security.Signature
import java.time.LocalDate
import java.time.ZoneId

/**
 * The daily site log — יומן עבודה.
 *
 * An Israeli site manager is required to keep one: who was on site, what was
 * built, what arrived, what went wrong, signed off each day. It is the document
 * an inspector asks for first and the one a court reads years later.
 *
 * Almost none of it should be typed. By the time somebody sits down at five
 * o'clock the app already knows which tasks closed, which permits were issued,
 * which briefings were given, what was delivered and what went wrong — it
 * watched all of it happen. The log gathers that and asks for the handful of
 * things only a person knows: the weather, the headcount, and what the day was
 * actually like. Then it is signed, and it stops being editable.
 */
object DailyLog {

    object Status {
        /** Still the day's working notes. */
        const val DRAFT = "DRAFT"

        /** Signed off. A record now, not a document. */
        const val SIGNED = "SIGNED"
    }

    enum class State { DRAFT, SIGNED }

    /** An unknown status is a draft: a record is never closed by accident. */
    fun state(status: String): State =
        if (status == Status.SIGNED) State.SIGNED else State.DRAFT

    /**
     * A signed log cannot be edited.
     *
     * This is the whole point of signing one. A daily log somebody can go back
     * and tidy up after an accident is not evidence of anything, and every
     * jurisdiction that asks for one asks for that reason.
     */
    fun canEdit(status: String): Boolean = state(status) == State.DRAFT

    /** Signing needs a real signature and a log nobody has signed yet. */
    fun canSign(status: String, signature: String?): Boolean =
        state(status) == State.DRAFT && Signature.isSigned(signature)

    /**
     * The moments a log covers: local midnight to local midnight.
     *
     * Deliberately not "the timestamp plus 24 hours". Israel puts the clocks
     * forward in March and back in October, so two days a year are 23 and 25
     * hours long — and on those two days a fixed 24-hour window either loses an
     * hour of work off the end or steals an hour from the next day. Both are
     * the kind of error nobody notices until somebody is looking for what
     * happened at a particular time.
     *
     * The end is exclusive: midnight belongs to the day that is starting.
     */
    fun windowFor(epochDay: Long, zone: ZoneId): LongRange {
        val date = LocalDate.ofEpochDay(epochDay)
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start until end
    }

    /** Whether something that happened at [at] belongs to this log. */
    fun covers(epochDay: Long, at: Long, zone: ZoneId): Boolean =
        at in windowFor(epochDay, zone)

    /**
     * Which log a moment belongs to.
     *
     * The date is stored as a day number rather than a timestamp, so a log for
     * the fifth of September stays the fifth of September when somebody opens
     * the record in another country years later. The window is worked out in
     * the local zone at the time of reading; the label never moves.
     */
    fun dayOf(at: Long, zone: ZoneId): Long =
        java.time.Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toEpochDay()

    fun today(zone: ZoneId): Long = LocalDate.now(zone).toEpochDay()

    /**
     * What the app already knows about a day, gathered rather than typed.
     *
     * Zero counts are not hidden. A day with no briefings and no permits is a
     * fact about that day, and a log that quietly omits its empty rows reads as
     * though nobody checked.
     */
    data class Summary(
        val tasksCompleted: Int = 0,
        val deliveries: Int = 0,
        val permitsIssued: Int = 0,
        val talksHeld: Int = 0,
        val snagsRaised: Int = 0,
        val incidents: Int = 0,
    ) {
        /** True when the app watched nothing happen — worth saying out loud. */
        val isQuiet: Boolean
            get() = tasksCompleted == 0 && deliveries == 0 && permitsIssued == 0 &&
                talksHeld == 0 && snagsRaised == 0 && incidents == 0
    }
}
