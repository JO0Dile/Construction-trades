package il.co.tradesmanager.core.safety

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A machine's periodic examination by a qualified examiner, and whether its
 * certificate still runs.
 *
 * Distinct from the morning walk-round in [PreUse]. The walk-round is the
 * operator looking for a leak before starting; the examination is somebody
 * qualified signing that a crane, a hoist, a forklift or a pressure vessel
 * is fit, until a date. The certificate is what an inspector on site asks to
 * see, and the one that ran out last month is the one nobody noticed. So
 * each is recorded with who examined, the certificate number, the result
 * and the next date due; a failure takes the machine out of service.
 *
 * Which machines need examining, and how often, is the examiner's and the
 * regulations' to say. Nothing here decides it: a machine with no
 * certificate recorded is shown as having none, not as overdue.
 */
object Examinations {

    enum class Result { PASSED, FAILED }

    enum class Refusal {
        /** A certificate names who signed it. */
        NO_EXAMINER,
        EXAMINED_IN_FUTURE,
        DUE_BEFORE_EXAMINED,

        /** A failure with no reason gives the fitter nothing to fix. */
        FAILED_WITHOUT_REASON,
    }

    enum class State {
        /** No examination recorded for this machine. */
        NONE,
        CURRENT,

        /** Passed, with no next date written on it. */
        NO_NEXT_DATE,

        /** Runs out within the same thirty days a ticket warns on. */
        DUE_SOON,
        OVERDUE,

        /** Failed its latest examination. */
        FAILED,
    }

    /** The same window as a person's ticket and a machine's service. */
    const val WARNING_DAYS = 30L

    fun refusal(
        examinerName: String,
        examinedOn: LocalDate,
        nextDueOn: LocalDate?,
        result: Result,
        notes: String,
        today: LocalDate,
    ): Refusal? = when {
        examinerName.isBlank() -> Refusal.NO_EXAMINER
        examinedOn.isAfter(today) -> Refusal.EXAMINED_IN_FUTURE
        nextDueOn != null && nextDueOn.isBefore(examinedOn) -> Refusal.DUE_BEFORE_EXAMINED
        result == Result.FAILED && notes.isBlank() -> Refusal.FAILED_WITHOUT_REASON
        else -> null
    }

    /** Current through the due date itself: a certificate due today is due today, not overdue. */
    fun state(latestResult: Result?, nextDueOn: LocalDate?, today: LocalDate): State = when {
        latestResult == null -> State.NONE
        latestResult == Result.FAILED -> State.FAILED
        nextDueOn == null -> State.NO_NEXT_DATE
        today.isAfter(nextDueOn) -> State.OVERDUE
        ChronoUnit.DAYS.between(today, nextDueOn) <= WARNING_DAYS -> State.DUE_SOON
        else -> State.CURRENT
    }

    /** The two that mean the machine should not be relied on until somebody acts. */
    fun needsAttention(state: State): Boolean = state == State.OVERDUE || state == State.FAILED

    fun resultOf(stored: String?): Result? = stored?.let { name -> Result.entries.firstOrNull { it.name == name } }
}
