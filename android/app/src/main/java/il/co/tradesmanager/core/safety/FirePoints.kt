package il.co.tradesmanager.core.safety

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The extinguishers, fire blankets and hose reels on a site, and whether
 * each one would work on the day it was needed.
 *
 * Two different things keep one ready. The technician services it and
 * writes the next date on its label, usually a year on; somebody on the site
 * walks round every month and looks -- still there, pin in, gauge in the
 * green, nothing hung in front of it. So each fire point gets a number,
 * where it is, what kind, its tag, the service date on its label, and a row
 * for every look: who, when, and whether it was fine. A fault is written
 * down with what was wrong, and stays the point's state until a later look
 * finds it fine.
 *
 * Shown first: a fault, then a service date passed, then a look overdue.
 * How many a site needs, and where, is the fire authority's and the plan's
 * to say.
 */
object FirePoints {

    enum class Kind { POWDER, CO2, FOAM, WATER, BLANKET, HOSE_REEL }

    enum class Refusal {
        BLANK_LOCATION,
        SERVICE_DUE_IN_PAST,
        FAULT_NEEDS_NOTE,
        ALREADY_REMOVED,
    }

    enum class State {
        /** The last look found something wrong. */
        FAULT,
        SERVICE_OVERDUE,

        /** Never looked at, or not in [CHECK_DAYS]. */
        CHECK_OVERDUE,
        SERVICE_DUE_SOON,
        READY,
        REMOVED,
    }

    /** A month, with a day's slack so the same date next month is not late. */
    const val CHECK_DAYS = 31L

    /** A month's warning before the service date: long enough to book the technician. */
    const val SERVICE_WARNING_DAYS = 30L

    fun addRefusal(location: String, serviceDueOn: LocalDate?, today: LocalDate): Refusal? = when {
        location.isBlank() -> Refusal.BLANK_LOCATION
        serviceDueOn != null && serviceDueOn.isBefore(today) -> Refusal.SERVICE_DUE_IN_PAST
        else -> null
    }

    /** A point just serviced carries a new date on its label, and it is ahead. */
    fun serviceRefusal(nextDueOn: LocalDate, removed: Boolean, today: LocalDate): Refusal? = when {
        removed -> Refusal.ALREADY_REMOVED
        !nextDueOn.isAfter(today) -> Refusal.SERVICE_DUE_IN_PAST
        else -> null
    }

    /** "Not fine" with nothing written says nothing to whoever has to put it right. */
    fun checkRefusal(ok: Boolean, note: String, removed: Boolean): Refusal? = when {
        removed -> Refusal.ALREADY_REMOVED
        !ok && note.isBlank() -> Refusal.FAULT_NEEDS_NOTE
        else -> null
    }

    fun removeRefusal(removed: Boolean): Refusal? = if (removed) Refusal.ALREADY_REMOVED else null

    /**
     * [lastCheckAt] and [lastCheckOk] are the newest look, if there was one.
     * A fault outranks everything: an extinguisher that is empty is not made
     * ready by a service date a year away.
     */
    fun state(
        serviceDueOn: LocalDate?,
        lastCheckAt: Long?,
        lastCheckOk: Boolean?,
        removed: Boolean,
        now: Long,
        zone: ZoneId,
    ): State {
        if (removed) return State.REMOVED
        if (lastCheckOk == false) return State.FAULT
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        if (serviceDueOn != null && serviceDueOn.isBefore(today)) return State.SERVICE_OVERDUE
        if (lastCheckAt == null) return State.CHECK_OVERDUE
        val checkedOn = Instant.ofEpochMilli(lastCheckAt).atZone(zone).toLocalDate()
        if (ChronoUnit.DAYS.between(checkedOn, today) > CHECK_DAYS) return State.CHECK_OVERDUE
        if (serviceDueOn != null && ChronoUnit.DAYS.between(today, serviceDueOn) <= SERVICE_WARNING_DAYS) return State.SERVICE_DUE_SOON
        return State.READY
    }

    /** The states somebody has to act on. */
    fun needsAttention(state: State): Boolean =
        state == State.FAULT || state == State.SERVICE_OVERDUE || state == State.CHECK_OVERDUE

    /** By state, then by reference, so the walk-round goes in the same order every month. */
    fun order(rows: List<Pair<State, String>>): List<Int> =
        rows.indices.sortedWith(compareBy({ rows[it].first.ordinal }, { rows[it].second }))

    /** "FP-001": fire points, numbered per job. */
    fun reference(countOnJob: Int): String = "FP-" + (countOnJob + 1).toString().padStart(3, '0')

    fun kindOf(stored: String): Kind = Kind.entries.firstOrNull { it.name == stored } ?: Kind.POWDER
}
