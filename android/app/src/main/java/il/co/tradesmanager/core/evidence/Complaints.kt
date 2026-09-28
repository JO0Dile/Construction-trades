package il.co.tradesmanager.core.evidence

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Complaints about the site from outside it: the neighbour woken at six by
 * the pump, the dust on the cars in the street, the lorry parked across a
 * drive, the municipality's inspector who came because somebody phoned.
 *
 * On a job in a town these are what the municipality and the neighbours
 * remember the firm by, and what somebody is asked about months later: when
 * was it raised, what was done, did anybody answer. So each complaint gets a
 * number, who made it and how, what it was about, and the answer -- written
 * once, by whoever gave it -- and one left unanswered for a week is shown
 * first.
 */
object Complaints {

    enum class Subject { NOISE, DUST, HOURS, BLOCKING, DAMAGE, DIRT, SAFETY, OTHER }

    enum class Channel { IN_PERSON, PHONE, WRITING, MUNICIPALITY }

    enum class Refusal {
        BLANK_FROM,
        BLANK_DESCRIPTION,
        RECEIVED_IN_FUTURE,
        BLANK_RESPONSE,
        ALREADY_ANSWERED,
    }

    enum class State {
        /** Unanswered for longer than [WAITING_DAYS]. */
        WAITING_LONG,
        OPEN,
        ANSWERED,
    }

    /** A week: long enough to look into it, short enough that the neighbour has not given up on an answer. */
    const val WAITING_DAYS = 7L

    fun receiveRefusal(from: String, description: String, receivedAt: Long, now: Long): Refusal? = when {
        from.isBlank() -> Refusal.BLANK_FROM
        description.isBlank() -> Refusal.BLANK_DESCRIPTION
        receivedAt > now -> Refusal.RECEIVED_IN_FUTURE
        else -> null
    }

    fun answerRefusal(response: String, answeredAt: Long?): Refusal? = when {
        answeredAt != null -> Refusal.ALREADY_ANSWERED
        response.isBlank() -> Refusal.BLANK_RESPONSE
        else -> null
    }

    /** Waiting long from the eighth calendar day on the site's clock. */
    fun state(receivedAt: Long, answeredAt: Long?, now: Long, zone: ZoneId): State {
        if (answeredAt != null) return State.ANSWERED
        val received = Instant.ofEpochMilli(receivedAt).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return if (ChronoUnit.DAYS.between(received, today) > WAITING_DAYS) State.WAITING_LONG else State.OPEN
    }

    /** Waiting longest first, then open, then answered; within each, the oldest first. */
    fun order(rows: List<Pair<State, Long>>): List<Int> =
        rows.indices.sortedWith(compareBy({ rows[it].first.ordinal }, { rows[it].second }))

    /** "CP-001": complaints, numbered per job. */
    fun reference(countOnJob: Int): String = "CP-" + (countOnJob + 1).toString().padStart(3, '0')

    fun subjectOf(stored: String): Subject = Subject.entries.firstOrNull { it.name == stored } ?: Subject.OTHER

    fun channelOf(stored: String): Channel = Channel.entries.firstOrNull { it.name == stored } ?: Channel.IN_PERSON
}
