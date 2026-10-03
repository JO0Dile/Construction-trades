package il.co.tradesmanager.core.work

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Questions put to the designers, and whether they have been answered.
 *
 * Every job runs on them -- "the drawing shows a beam where the duct goes",
 * "which tile, the sample or the schedule" -- and the ones that hurt are the
 * ones asked on the phone and never written down. By the time the answer
 * matters, nobody can say when it was asked, of whom, or whether anybody
 * replied. So each one gets a number, the date it was asked, who it was put
 * to, and when the site needs the answer by; and an unanswered one past that
 * date is shown as holding the work up.
 */
object Queries {

    enum class Refusal {
        BLANK_QUESTION,

        /** Asked of nobody. A question with no one to answer it is a note. */
        NOBODY_ASKED,

        /** A needed-by date before the day it was asked. */
        NEEDED_BY_TOO_EARLY,
        BLANK_ANSWER,
        ALREADY_ANSWERED,
    }

    enum class State {
        /** Past the date the site needed it by, and still no answer. */
        OVERDUE,
        OPEN,
        ANSWERED,
    }

    fun raiseRefusal(question: String, askedOf: String, askedAt: Long, neededBy: Long?, zone: ZoneId): Refusal? = when {
        question.isBlank() -> Refusal.BLANK_QUESTION
        askedOf.isBlank() -> Refusal.NOBODY_ASKED
        neededBy != null && day(neededBy, zone) < day(askedAt, zone) -> Refusal.NEEDED_BY_TOO_EARLY
        else -> null
    }

    /** An answer goes in once: what the designer said is not edited afterwards. */
    fun answerRefusal(answer: String, answeredAt: Long?): Refusal? = when {
        answeredAt != null -> Refusal.ALREADY_ANSWERED
        answer.isBlank() -> Refusal.BLANK_ANSWER
        else -> null
    }

    /**
     * Overdue from the day after the needed-by date: a question needed "by
     * Thursday" answered on Thursday afternoon was answered in time.
     */
    fun state(neededBy: Long?, answeredAt: Long?, now: Long, zone: ZoneId): State = when {
        answeredAt != null -> State.ANSWERED
        neededBy != null && day(now, zone) > day(neededBy, zone) -> State.OVERDUE
        else -> State.OPEN
    }

    /** Overdue first, then open, then answered; within each, the oldest question first. */
    fun order(rows: List<Pair<State, Long>>): List<Int> =
        rows.indices.sortedWith(compareBy({ rows[it].first.ordinal }, { rows[it].second }))

    /** "Q-001": the site's own numbering, in the order they were asked on this job. */
    fun reference(countOnJob: Int): String = "Q-" + (countOnJob + 1).toString().padStart(3, '0')

    /** The calendar day on the site's clock, which is the one a "needed by" means. */
    private fun day(at: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
}
