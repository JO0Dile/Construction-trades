package il.co.tradesmanager.core.safety

import java.time.Instant
import java.time.ZoneId

/**
 * The walk-round an operator does before starting a machine.
 *
 * Excavators, telehandlers, forklifts, boom lifts, dumpers: each one is looked
 * over by whoever is about to drive it, before the shift, and the thing that
 * matters about the check is what happens when it finds something. A defect
 * written in a book while the machine carries on working is not a check. So a
 * defect here takes the machine out of service there and then, and only
 * somebody deciding it has been put right puts it back.
 *
 * Like an excavation inspection, a check is good for the **day** it was made.
 * A machine checked yesterday has been parked overnight, rained on, possibly
 * driven by somebody else, and yesterday's tick is not a statement about the
 * hydraulics this morning.
 *
 * The items are the ones common to mobile plant. A machine they do not all fit
 * answers "does not apply" for the rest -- but not for all of them: a check in
 * which nothing applied is a check in which nothing was looked at.
 *
 * None of this is offered as what any regulation requires of a particular
 * machine; the manufacturer's own pre-use list is the authority for that one.
 */
object PreUse {

    /** What gets looked at, in the order a walk-round goes. */
    enum class Item {
        VISIBLE_DAMAGE,
        TYRES_OR_TRACKS,
        LEAKS,
        FLUID_LEVELS,
        BRAKES,
        STEERING_AND_CONTROLS,
        LIGHTS_HORN_AND_ALARMS,
        SEATBELT_AND_CAB,
        GUARDS,
        FIRE_EXTINGUISHER,
    }

    enum class Answer {
        OK,
        DEFECT,
        NOT_APPLICABLE,
    }

    /** What the check concluded. */
    enum class Outcome {
        /** Nothing wrong. The machine may be used today. */
        FIT,

        /** Something wrong. The machine is out of service until put right. */
        UNFIT,
    }

    enum class Refusal {
        /** An item has no answer. A half-finished walk-round is not a check. */
        UNANSWERED,

        /** A defect with nothing said about what it is. */
        DEFECT_NOT_DESCRIBED,

        /** Every item marked as not applying: nothing was looked at. */
        NOTHING_CHECKED,
    }

    sealed interface Verdict {
        data class Judged(val outcome: Outcome, val defects: List<Item>) : Verdict
        data class Refused(val reason: Refusal) : Verdict
    }

    /**
     * What a completed walk-round concludes, or why it is not complete.
     *
     * Refusals are checked in the order a person would fix them: answer
     * everything, then describe what is wrong.
     */
    fun judge(answers: Map<Item, Answer>, defectNote: String?): Verdict {
        if (Item.entries.any { it !in answers }) return Verdict.Refused(Refusal.UNANSWERED)
        if (answers.values.all { it == Answer.NOT_APPLICABLE }) {
            return Verdict.Refused(Refusal.NOTHING_CHECKED)
        }
        val defects = Item.entries.filter { answers[it] == Answer.DEFECT }
        if (defects.isNotEmpty() && defectNote.isNullOrBlank()) {
            return Verdict.Refused(Refusal.DEFECT_NOT_DESCRIBED)
        }
        return Verdict.Judged(
            outcome = if (defects.isEmpty()) Outcome.FIT else Outcome.UNFIT,
            defects = defects,
        )
    }

    /** Where a machine stands for today. */
    enum class Today {
        /** Never been checked in this app. */
        NEVER_CHECKED,

        /** Checked, but not today. Needs checking before use. */
        NOT_CHECKED_TODAY,

        /** Checked today and found fit. */
        FIT_TODAY,

        /** Checked today and found a defect. Not to be used. */
        UNFIT_TODAY,
    }

    /**
     * Today's state from the latest check.
     *
     * "Today" is the calendar day where the phone is, so a check at 23:50 does
     * not carry a machine through the next morning's shift.
     */
    fun today(lastCheckedAt: Long?, lastOutcome: Outcome?, now: Long, zone: ZoneId): Today {
        if (lastCheckedAt == null || lastOutcome == null) return Today.NEVER_CHECKED
        val checkedDay = Instant.ofEpochMilli(lastCheckedAt).atZone(zone).toLocalDate()
        val thisDay = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        if (checkedDay != thisDay) return Today.NOT_CHECKED_TODAY
        return if (lastOutcome == Outcome.FIT) Today.FIT_TODAY else Today.UNFIT_TODAY
    }
}
