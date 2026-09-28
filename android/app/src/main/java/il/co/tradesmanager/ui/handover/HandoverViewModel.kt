package il.co.tradesmanager.ui.handover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.evidence.DailyLog
import il.co.tradesmanager.core.evidence.CubeTests
import il.co.tradesmanager.core.evidence.HandoverPack
import il.co.tradesmanager.core.evidence.Inspections
import il.co.tradesmanager.core.evidence.Permits
import il.co.tradesmanager.core.evidence.Snags
import il.co.tradesmanager.core.work.Submittals
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.data.repository.WasteRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.export.ExportDocument
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    /**
     * Loads nothing yet shows went anywhere. The ticket photographs are in
     * another table, so the two are read together and weighed by the same
     * rule the register uses -- see Waste.Load.proven.
     */
    private val fromWaste = combine(
        container.waste.observeForProject(projectId),
        container.photos.observeCountsFor(PhotoRepository.Owner.WASTE_TICKET),
    ) { loads, photographed ->
        mapOf(
            HandoverPack.Item.WASTE_WITHOUT_TICKET to loads.count { row ->
                WasteRepository.asLoad(row, photographed[row.id] ?: 0)?.proven == false
            },
        )
    }

    /**
     * The cube results, by the same rule the pour screen marks them with --
     * see CubeTests -- so the pack and the pour list cannot disagree about
     * which pours the engineer still has to see.
     */
    private val fromCubes = combine(
        container.concrete.observePours(projectId),
        container.concrete.observeCubeSetsForProject(projectId),
    ) { pours, sets ->
        val byPour = sets.groupBy { it.pourId }
        mapOf(
            HandoverPack.Item.CUBES_FOR_ENGINEER to pours.count { pour ->
                byPour[pour.id].orEmpty().any { set ->
                    CubeTests.judgeStored(set.ageDays, set.strengthsMpa, pour.mixDesign)?.needsEngineer == true
                }
            },
            HandoverPack.Item.POURS_WITHOUT_28_DAY_RESULT to pours.count { pour ->
                CubeTests.awaitingJudgedResult(
                    finished = pour.completedAt != null,
                    setAges = byPour[pour.id].orEmpty().map { it.ageDays },
                )
            },
        )
    }

    /**
     * Inspections still outstanding, and pours none was set against, by the
     * same rule the register lists them with -- see Inspections.state -- so
     * the two cannot disagree.
     */
    private val fromInspections = combine(
        container.inspections.observeForProject(projectId),
        container.concrete.observePours(projectId),
    ) { inspections, pours ->
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val askedAgain = inspections.mapNotNull { it.reinspectionOf }.toSet()
        val cleared = inspections.mapNotNull { it.clearedPourId }.toSet()
        mapOf(
            HandoverPack.Item.INSPECTIONS_OUTSTANDING to inspections.count {
                Inspections.outstanding(
                    Inspections.state(Inspections.resultOf(it.result), it.wantedOn, it.id in askedAgain, now, zone),
                )
            },
            HandoverPack.Item.POURS_WITHOUT_INSPECTION to pours.count { it.id !in cleared },
        )
    }

    /**
     * What was asked of the designers and has not come back: questions and
     * materials, by the rules their registers use. Both are the plan, and the
     * pack is open to anybody who reads the site's record, so these are
     * counted only for somebody who may read the plan -- for anybody else
     * they are absent, which the pack reads as nothing to show, rather than
     * sent and hidden.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val fromPlan = container.session.state.flatMapLatest { state ->
        val role = (state as? SessionRepository.State.SignedIn)?.role
        if (role == null || !role.canRead(Lens.PLAN)) {
            flowOf(emptyMap<HandoverPack.Item, Int>())
        } else {
            combine(
                container.designQueries.observeForProject(projectId),
                container.submittals.observeForProject(projectId),
            ) { queries, submittals ->
                val now = System.currentTimeMillis()
                val zone = ZoneId.systemDefault()
                val sentAgain = submittals.mapNotNull { it.resubmissionOf }.toSet()
                mapOf(
                    HandoverPack.Item.QUERIES_UNANSWERED to queries.count { it.answeredAt == null },
                    HandoverPack.Item.SUBMITTALS_OUTSTANDING to submittals.count {
                        Submittals.outstanding(
                            Submittals.state(Submittals.decisionOf(it.decision), it.neededBy, it.id in sentAgain, now, zone),
                        )
                    },
                )
            }
        }
    }

    private val fromAskedOf = combine(fromInspections, fromPlan) { inspections, plan -> inspections + plan }

    val readiness: StateFlow<HandoverPack.Readiness> = combine(
        fromSafety,
        fromWorks,
        fromWaste,
        fromCubes,
        fromAskedOf,
    ) { safety, works, waste, cubes, askedOf -> HandoverPack.readiness(safety + works + waste + cubes + askedOf) }
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

    /**
     * Every register of the job that the person asking may read, as
     * documents: the handover summary first, then the registers. Read once,
     * at the moment of export, from the same repositories the screens use --
     * and a register the role may not read is left out of the archive, not
     * put in empty.
     */
    suspend fun archive(): List<ExportDocument> {
        val job = project.value ?: return emptyList()
        val role = (container.session.state.first() as? SessionRepository.State.SignedIn)?.role
            ?: return emptyList()
        val documents = mutableListOf<ExportDocument>(
            ExportDocument.Handover(
                project = job,
                readiness = readiness.value,
                producedByName = producedBy.value,
                producedOn = LocalDate.now(),
            ),
        )
        if (role.canRead(Lens.EVIDENCE)) {
            documents += ExportDocument.InspectionRegister(job.name, container.inspections.observeForProject(projectId).first())
            documents += ExportDocument.RiskRegister(job.name, container.risks.observeForProject(projectId).first())
            documents += ExportDocument.VisitorLog(job.name, container.visits.observeForProject(projectId).first())
            documents += ExportDocument.ComplaintRegister(job.name, container.complaints.observeForProject(projectId).first())
            documents += ExportDocument.SubstanceRegister(job.name, container.substances.observeForProject(projectId).first(), LocalDate.now())
            documents += ExportDocument.FirePointRegister(
                job.name,
                container.firePoints.observeForProject(projectId).first(),
                container.firePoints.observeChecksForProject(projectId).first(),
            )
        }
        if (role.canRead(Lens.PLAN)) {
            documents += ExportDocument.QueryRegister(job.name, container.designQueries.observeForProject(projectId).first())
            documents += ExportDocument.SubmittalRegister(job.name, container.submittals.observeForProject(projectId).first())
            documents += ExportDocument.DelayRegister(job.name, container.delays.observeForProject(projectId).first(), LocalDate.now())
        }
        return documents
    }

    /** Recorded on the pack so an interim one reads as interim. */
    val producedBy: StateFlow<String> = container.settings.settings
        .map { it.actorName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
}
