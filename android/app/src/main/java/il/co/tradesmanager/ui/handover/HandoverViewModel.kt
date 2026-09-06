package il.co.tradesmanager.ui.handover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.evidence.DailyLog
import il.co.tradesmanager.core.evidence.HandoverPack
import il.co.tradesmanager.core.evidence.Permits
import il.co.tradesmanager.core.evidence.Snags
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What is still open on a job, gathered from every register at once.
 *
 * The counting happens here, in Kotlin, from the same flows each register's own
 * screen reads. Eight `COUNT` queries would have been a second definition of
 * "still open" living in SQL, able to drift from the first — the same reason
 * the payments DAO returns a row rather than a computed figure.
 *
 * Those flows are capped (two to three hundred rows a job), so a site with more
 * open trenches than that would undercount. That is a limit worth naming and
 * not worth engineering around: three hundred open excavations on one job is
 * not a reporting problem.
 */
class HandoverViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    val project: StateFlow<ProjectEntity?> = container.projects.observeProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val fromSafety = combine(
        container.evidence.observeSnags(projectId),
        container.evidence.observePermits(projectId),
        container.scaffolds.observeForProject(projectId),
        container.temporaryWorks.observeForProject(projectId),
    ) { snags, permits, scaffolds, temporaryWorks ->
        mapOf(
            // Only the ones somebody said hold up handover. A scuff to touch up
            // next week is a real snag that should stay on the list without
            // pretending to stop a building being handed over.
            HandoverPack.Item.BLOCKING_SNAGS to snags.count {
                Snags.state(it.status).isOutstanding && it.blocksHandover
            },
            // Issued and never signed back. An expired permit still counts:
            // the work stopping is not the same event as the area being checked.
            HandoverPack.Item.OPEN_PERMITS to permits.count {
                it.status == Permits.Status.ISSUED
            },
            HandoverPack.Item.SCAFFOLDS_STANDING to scaffolds.count { it.dismantledAt == null },
            HandoverPack.Item.TEMPORARY_WORKS_STANDING to temporaryWorks.count {
                it.erectedAt != null && it.struckAt == null
            },
        )
    }

    private val fromWorks = combine(
        container.excavations.observeForProject(projectId),
        container.lifting.observePlans(projectId),
        container.concrete.observePours(projectId),
        container.dailyLogs.observeForProject(projectId),
    ) { excavations, lifts, pours, logs ->
        mapOf(
            HandoverPack.Item.EXCAVATIONS_OPEN to excavations.count { it.backfilledAt == null },
            HandoverPack.Item.LIFTS_INCOMPLETE to lifts.count { it.completedAt == null },
            HandoverPack.Item.POURS_UNFINISHED to pours.count { it.completedAt == null },
            HandoverPack.Item.UNSIGNED_DAILY_LOGS to logs.count {
                DailyLog.state(it.status) == DailyLog.State.DRAFT
            },
        )
    }

    val readiness: StateFlow<HandoverPack.Readiness> = combine(
        fromSafety,
        fromWorks,
    ) { safety, works -> HandoverPack.readiness(safety + works) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            HandoverPack.readiness(emptyMap()),
        )

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    /** Recorded on the pack so an interim one reads as interim. */
    val producedBy: StateFlow<String> = container.settings.settings
        .map { it.actorName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
}
