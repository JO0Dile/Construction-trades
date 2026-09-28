package il.co.tradesmanager.core.work

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Days the work could not go ahead, and why.
 *
 * Rain on the slab, an answer from the engineer that came three weeks late,
 * the electricity company that did not connect, the client who stopped a
 * floor to change it. Months later every one of them is an argument about
 * time and money, and the side with a record made on the day -- what
 * stopped, from when to when, and that the other side was told -- is the
 * side that has one. So each event gets a number, a cause, the days it ran,
 * the work it held, and whether notice was given and to whom.
 *
 * Nothing here says how much time a contract allows for a cause, or how soon
 * notice has to be given. That is the contract's, and the lawyers'; this only
 * keeps the record, and says plainly which events have no notice against them.
 */
object Delays {

    enum class Cause {
        WEATHER,

        /** A drawing, an answer or an instruction the site was waiting for. */
        LATE_INFORMATION,
        CLIENT_CHANGE,

        /** The site, a floor or an area not handed over when it should have been. */
        NO_ACCESS,

        /** Electricity, water, the road: somebody else's connection. */
        UTILITIES,

        /** A permit, an inspection or a stop from an authority. */
        AUTHORITY,
        SUPPLY,
        LABOUR,
        OTHER,
    }

    enum class Refusal {
        BLANK_DESCRIPTION,

        /** A delay that has not begun yet is a warning, not an event. */
        STARTS_IN_FUTURE,
        ENDS_BEFORE_START,
        ALREADY_ENDED,

        /** Notice is given once, to somebody, on a day. */
        NOTICE_ALREADY_GIVEN,
        NOBODY_NOTIFIED,
        NOTICE_BEFORE_START,
    }

    fun recordRefusal(description: String, startedOn: LocalDate, today: LocalDate): Refusal? = when {
        description.isBlank() -> Refusal.BLANK_DESCRIPTION
        startedOn.isAfter(today) -> Refusal.STARTS_IN_FUTURE
        else -> null
    }

    fun endRefusal(startedOn: LocalDate, endedOn: LocalDate?, newEnd: LocalDate): Refusal? = when {
        endedOn != null -> Refusal.ALREADY_ENDED
        newEnd.isBefore(startedOn) -> Refusal.ENDS_BEFORE_START
        else -> null
    }

    fun noticeRefusal(startedOn: LocalDate, notifiedOn: LocalDate?, notifiedTo: String, noticeDay: LocalDate): Refusal? = when {
        notifiedOn != null -> Refusal.NOTICE_ALREADY_GIVEN
        notifiedTo.isBlank() -> Refusal.NOBODY_NOTIFIED
        noticeDay.isBefore(startedOn) -> Refusal.NOTICE_BEFORE_START
        else -> null
    }

    /**
     * Calendar days, counting the first and the last: rain on Sunday and
     * Monday is two days. One still going counts to [today].
     */
    fun days(startedOn: LocalDate, endedOn: LocalDate?, today: LocalDate): Long {
        val end = endedOn ?: today
        return if (end.isBefore(startedOn)) 0 else ChronoUnit.DAYS.between(startedOn, end) + 1
    }

    /**
     * Days per cause across a job. Events that overlap are each counted in
     * full, because they are separate claims; the screen says so rather than
     * pretending the total is the time lost.
     */
    fun daysByCause(events: List<Triple<Cause, LocalDate, LocalDate?>>, today: LocalDate): Map<Cause, Long> =
        events.groupBy({ it.first }, { days(it.second, it.third, today) }).mapValues { (_, spans) -> spans.sum() }

    /** Still going first, then the most recent start; within a day, the order they were recorded. */
    fun order(rows: List<Pair<Boolean, LocalDate>>): List<Int> =
        rows.indices.sortedWith(compareBy({ if (rows[it].first) 0 else 1 }, { -rows[it].second.toEpochDay() }))

    /** "DE-001": delay events, numbered per job. */
    fun reference(countOnJob: Int): String = "DE-" + (countOnJob + 1).toString().padStart(3, '0')

    fun causeOf(stored: String): Cause = Cause.entries.firstOrNull { it.name == stored } ?: Cause.OTHER

    /** Stored days are the epoch day of the site's own calendar; this is the day a timestamp falls on there. */
    fun dayOf(at: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
}
