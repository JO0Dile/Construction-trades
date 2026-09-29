package il.co.tradesmanager.ui.projects

import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.evidence.Complaints
import il.co.tradesmanager.core.evidence.Inspections
import il.co.tradesmanager.core.safety.EmergencySheet
import il.co.tradesmanager.core.safety.FirePoints
import il.co.tradesmanager.core.safety.Risks
import il.co.tradesmanager.core.safety.Substances
import il.co.tradesmanager.core.work.Attention
import il.co.tradesmanager.core.work.Meetings
import il.co.tradesmanager.core.work.Queries
import il.co.tradesmanager.core.work.Submittals
import il.co.tradesmanager.data.repository.EmergencySheetRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.firepoints.FirePointsViewModel
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * What on one job is waiting on somebody, by each register's own rule.
 *
 * The plan's registers are counted only for [role]s that may read the plan,
 * the site record's only for those that may read that: for anybody else
 * those lines are absent, not zero. One function for the job page and the
 * home screen, so the two cannot disagree about a job.
 */
fun observeJobAttention(container: AppContainer, projectId: String, role: Role?): Flow<List<Attention.Line>> {
    val fromPlan = if (role != null && role.canRead(Lens.PLAN)) {
        combine(
            container.designQueries.observeForProject(projectId),
            container.submittals.observeForProject(projectId),
            container.delays.observeForProject(projectId),
            container.meetings.observeActionsForProject(projectId),
        ) { queries, submittals, delays, points ->
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val sentAgain = submittals.mapNotNull { it.resubmissionOf }.toSet()
            val materials = submittals.map {
                Submittals.state(Submittals.decisionOf(it.decision), it.neededBy, it.id in sentAgain, now, zone)
            }
            mapOf(
                Attention.Item.QUERIES_OVERDUE to queries.count {
                    Queries.state(it.neededBy, it.answeredAt, now, zone) == Queries.State.OVERDUE
                },
                Attention.Item.MATERIALS_REJECTED to materials.count { it == Submittals.State.REJECTED },
                Attention.Item.MATERIALS_OVERDUE to materials.count { it == Submittals.State.OVERDUE },
                Attention.Item.DELAYS_RUNNING to delays.count { it.endedOnDay == null },
                Attention.Item.DELAYS_WITHOUT_NOTICE to delays.count { it.notifiedOnDay == null },
                Attention.Item.MEETING_POINTS_OVERDUE to points.count {
                    Meetings.actionState(it.dueOnDay?.let(LocalDate::ofEpochDay), it.closedAt != null, LocalDate.now()) ==
                        Meetings.ActionState.OVERDUE
                },
            )
        }
    } else {
        flowOf(emptyMap<Attention.Item, Int>())
    }
    val fromRecord = if (role != null && role.canRead(Lens.EVIDENCE)) {
        combine(
            container.inspections.observeForProject(projectId),
            container.risks.observeForProject(projectId),
            container.complaints.observeForProject(projectId),
            container.substances.observeForProject(projectId),
            combine(
                container.firePoints.observeForProject(projectId),
                container.firePoints.observeChecksForProject(projectId),
                container.emergencySheets.observe(projectId),
            ) { points, checks, sheet -> FirePointsViewModel.rowsOf(points, checks) to sheet },
        ) { inspections, risks, complaints, substances, (firePoints, emergency) ->
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now()
            val askedAgain = inspections.mapNotNull { it.reinspectionOf }.toSet()
            val inspected = inspections.map {
                Inspections.state(Inspections.resultOf(it.result), it.wantedOn, it.id in askedAgain, now, zone)
            }
            val riskStates = risks.map {
                Risks.state(it.closed, Risks.score(it.likelihoodAfter, it.severityAfter), it.reviewOnDay?.let(LocalDate::ofEpochDay), today)
            }
            mapOf(
                Attention.Item.INSPECTIONS_FAILED to inspected.count { it == Inspections.State.FAILED },
                Attention.Item.INSPECTIONS_OVERDUE to inspected.count { it == Inspections.State.OVERDUE },
                Attention.Item.RISKS_EXTREME to riskStates.count { it == Risks.State.EXTREME },
                Attention.Item.RISK_REVIEWS_OVERDUE to riskStates.count { it == Risks.State.REVIEW_OVERDUE },
                Attention.Item.COMPLAINTS_WAITING to complaints.count {
                    Complaints.state(it.receivedAt, it.answeredAt, now, zone) == Complaints.State.WAITING_LONG
                },
                Attention.Item.FIRE_POINTS to firePoints.count { FirePoints.needsAttention(it.state) },
                Attention.Item.EMERGENCY_INFO_MISSING to EmergencySheet.missing(EmergencySheetRepository.sheetOf(emergency)).size,
                Attention.Item.SUBSTANCES_WITHOUT_SHEET to substances.count {
                    Substances.state(it.sheetOnDay?.let(LocalDate::ofEpochDay), it.removedAt != null, today) == Substances.State.NO_SHEET
                },
            )
        }
    } else {
        flowOf(emptyMap<Attention.Item, Int>())
    }
    return combine(fromPlan, fromRecord) { plan, record -> Attention.lines(plan + record) }
}
