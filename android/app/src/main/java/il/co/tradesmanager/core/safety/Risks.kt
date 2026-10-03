package il.co.tradesmanager.core.safety

import java.time.LocalDate

/**
 * A job's risk assessment: what could hurt somebody, how badly and how likely,
 * what is being done about it, and what is left once it has been done.
 *
 * The register a safety officer keeps, and the first document anybody asks to
 * see after something goes wrong. Each row is an activity and a hazard in it,
 * who is exposed, the risk before and after the controls on the usual five by
 * five scale, who owns the controls, and when it is looked at again.
 *
 * The bands below are a common scheme, not a rule: a firm with its own matrix
 * uses its own words for them. What the app does insist on is that a risk
 * cannot come down with nothing done about it, and that the controls cannot
 * make it worse.
 */
object Risks {

    /** One to five, rare to almost certain. */
    val LIKELIHOODS: IntRange = 1..5

    /** One to five, negligible to catastrophic. */
    val SEVERITIES: IntRange = 1..5

    enum class Band { LOW, MEDIUM, HIGH, EXTREME }

    enum class Refusal {
        BLANK_ACTIVITY,
        BLANK_HAZARD,
        OUT_OF_RANGE,

        /** The risk comes down, and nothing is written about how. */
        NO_CONTROLS,

        /** The controls leave it worse than before them, which is a mistake in the scoring. */
        RESIDUAL_ABOVE_INITIAL,
        REVIEW_IN_PAST,
        ALREADY_CLOSED,
    }

    enum class State {
        /** Still extreme with the controls in place. */
        EXTREME,

        /** Its review date has passed. */
        REVIEW_OVERDUE,
        OPEN,

        /** The activity is finished, and the risk with it. */
        CLOSED,
    }

    fun score(likelihood: Int, severity: Int): Int = likelihood * severity

    /** Four and under low, to nine medium, to fourteen high, fifteen and over extreme. */
    fun band(score: Int): Band = when {
        score >= 15 -> Band.EXTREME
        score >= 10 -> Band.HIGH
        score >= 5 -> Band.MEDIUM
        else -> Band.LOW
    }

    fun refusal(
        activity: String,
        hazard: String,
        likelihoodBefore: Int,
        severityBefore: Int,
        controls: String,
        likelihoodAfter: Int,
        severityAfter: Int,
        reviewOn: LocalDate?,
        today: LocalDate,
    ): Refusal? = when {
        activity.isBlank() -> Refusal.BLANK_ACTIVITY
        hazard.isBlank() -> Refusal.BLANK_HAZARD
        listOf(likelihoodBefore, likelihoodAfter).any { it !in LIKELIHOODS } ||
            listOf(severityBefore, severityAfter).any { it !in SEVERITIES } -> Refusal.OUT_OF_RANGE
        score(likelihoodAfter, severityAfter) > score(likelihoodBefore, severityBefore) -> Refusal.RESIDUAL_ABOVE_INITIAL
        controls.isBlank() && score(likelihoodAfter, severityAfter) < score(likelihoodBefore, severityBefore) -> Refusal.NO_CONTROLS
        reviewOn != null && reviewOn.isBefore(today) -> Refusal.REVIEW_IN_PAST
        else -> null
    }

    fun state(closed: Boolean, residualScore: Int, reviewOn: LocalDate?, today: LocalDate): State = when {
        closed -> State.CLOSED
        band(residualScore) == Band.EXTREME -> State.EXTREME
        reviewOn != null && today.isAfter(reviewOn) -> State.REVIEW_OVERDUE
        else -> State.OPEN
    }

    /** Extreme first, then overdue for review, then by residual score, highest first; closed last. */
    fun order(rows: List<Pair<State, Int>>): List<Int> =
        rows.indices.sortedWith(compareBy({ rows[it].first.ordinal }, { -rows[it].second }))

    /** "RA-001": risk assessments, numbered per job. */
    fun reference(countOnJob: Int): String = "RA-" + (countOnJob + 1).toString().padStart(3, '0')
}
