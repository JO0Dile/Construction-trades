package il.co.tradesmanager.ui.account

import il.co.tradesmanager.R
import il.co.tradesmanager.core.safety.Induction

/** One thing somebody has to know, with the reason it matters underneath. */
data class InductionPoint(val titleRes: Int, val bodyRes: Int)

/** A headed group of them. */
data class InductionSection(val headingRes: Int, val points: List<InductionPoint>)

/**
 * What the induction actually says.
 *
 * The text lives here as string resources rather than in the catalogue JSON
 * because it has to be readable before the database has been opened — this is
 * the screen that stands between a person and the app, and it cannot depend on
 * the app having got going first.
 */
private val PPE = InductionSection(
    headingRes = R.string.ind_ppe_heading,
    points = listOf(
        InductionPoint(R.string.ind_hat, R.string.ind_hat_body),
        InductionPoint(R.string.ind_vis, R.string.ind_vis_body),
        InductionPoint(R.string.ind_boots, R.string.ind_boots_body),
        InductionPoint(R.string.ind_gloves, R.string.ind_gloves_body),
        InductionPoint(R.string.ind_eyes, R.string.ind_eyes_body),
        InductionPoint(R.string.ind_ears, R.string.ind_ears_body),
        InductionPoint(R.string.ind_stop, R.string.ind_stop_body),
        InductionPoint(R.string.ind_where, R.string.ind_where_body),
    ),
)

private val SUPERVISOR = InductionSection(
    headingRes = R.string.ind_sup_heading,
    points = listOf(
        InductionPoint(R.string.ind_sup_enforce, R.string.ind_sup_enforce_body),
        InductionPoint(R.string.ind_sup_permits, R.string.ind_sup_permits_body),
        InductionPoint(R.string.ind_sup_talks, R.string.ind_sup_talks_body),
        InductionPoint(R.string.ind_sup_tickets, R.string.ind_sup_tickets_body),
        InductionPoint(R.string.ind_sup_incidents, R.string.ind_sup_incidents_body),
        InductionPoint(R.string.ind_sup_emergency, R.string.ind_sup_emergency_body),
    ),
)

/**
 * A supervisor reads the PPE page too — they are on the same site, and they
 * cannot enforce a rule they were never shown.
 */
fun inductionSections(level: Induction.Level): List<InductionSection> = when (level) {
    Induction.Level.WORKER -> listOf(PPE)
    Induction.Level.SUPERVISOR -> listOf(PPE, SUPERVISOR)
}

fun inductionIntro(level: Induction.Level): Int = when (level) {
    Induction.Level.WORKER -> R.string.ind_intro_worker
    Induction.Level.SUPERVISOR -> R.string.ind_intro_supervisor
}
