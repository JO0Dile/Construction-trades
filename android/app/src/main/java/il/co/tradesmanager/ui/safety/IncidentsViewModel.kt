package il.co.tradesmanager.ui.safety

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.safety.Incidents
import il.co.tradesmanager.data.local.entity.IncidentEntity
import il.co.tradesmanager.data.local.entity.IncidentInvestigationEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.projects.jobOnShift
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The site's incident register, for the company the reporter is working for.
 *
 * Every report used to be filed with no job and no company, so the weekly
 * report and the safety statistics -- which count incidents job by job --
 * never counted one, and a phone signed in to two firms showed each the
 * other's accidents. A report now says which job it happened on, offered
 * the job the reporter is checked in to, and carries the company with it.
 */
class IncidentsViewModel(
    private val container: AppContainer,
    private val projectId: String?,
) : ViewModel() {

    private val companyId = container.session.state
        .map { (it as? SessionRepository.State.SignedIn)?.active?.companyId }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val incidents: StateFlow<List<IncidentEntity>> = companyId
        .flatMapLatest { container.safety.observeIncidents(it) }
        .map { all ->
            Incidents.order(
                items = all,
                occurredAt = { it.occurredAt },
                severity = { Incidents.parse(it.severity) },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Each report's investigation, by incident, for the state shown beside it. */
    val investigations: StateFlow<Map<String, IncidentInvestigationEntity>> = container.investigations.observeAll()
        .map { all -> all.associateBy { it.incidentId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The company's jobs, parts included: where a report can say it happened, and the names in the register. */
    val jobs: StateFlow<List<ProjectEntity>> = container.projects.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** What an incident cost is money: shown, and asked for, only for a role that reads money. */
    val canSeeCost: StateFlow<Boolean> = container.session.state
        .map { (it as? SessionRepository.State.SignedIn)?.canRead(Lens.MONEY) == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether the signed-in role may say which job a report with none was on. */
    val canPlace: StateFlow<Boolean> = container.session.state
        .map { mayPlace(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _suggestedJob = MutableStateFlow<String?>(null)

    /**
     * The job a new report starts on: the one somebody on this phone is
     * checked in to right now, if it is one of the company's. Only offered;
     * the reporter can pick another, or none.
     */
    val suggestedJob: StateFlow<String?> = _suggestedJob.asStateFlow()

    private val _placeRefused = MutableStateFlow(false)

    /** The last placing did not happen: the report already had a job, or is gone. Said rather than swallowed. */
    val placeRefused: StateFlow<Boolean> = _placeRefused.asStateFlow()

    fun clearPlaceRefused() {
        _placeRefused.value = false
    }

    /** Says which job a report filed without one happened on. */
    fun place(incident: IncidentEntity, job: ProjectEntity) = viewModelScope.launch {
        if (!mayPlace(container.session.state.first())) return@launch
        if (jobs.value.none { it.id == job.id }) return@launch
        val actor = container.settings.settings.first().actorName
        val placed = container.safety.placeIncident(incident.id, job.id, job.companyId, job.name, actor.ifBlank { "unknown" })
        _placeRefused.value = !placed
    }

    private fun mayPlace(state: SessionRepository.State): Boolean =
        (state as? SessionRepository.State.SignedIn)?.canWrite(Lens.EVIDENCE) == true

    /* ------------------------------------------------------- the report being written */

    /**
     * The id the report will be filed under, allocated before it is filed.
     *
     * Photographs need something to belong to, and the alternative -- writing
     * the row first and attaching afterwards -- means a register that briefly
     * holds reports with no evidence, which is the state this screen exists to
     * make impossible. Held here rather than in the dialog so it survives a
     * rotation with the pictures still attached to it.
     */
    private val _draftId = MutableStateFlow<String?>(null)
    val draftId: StateFlow<String?> = _draftId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val evidence: StateFlow<List<PhotoEntity>> = _draftId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(emptyList())
            } else {
                container.photos.observeFor(PhotoRepository.Owner.INCIDENT, id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Opens a fresh report. Does nothing if one is already open. */
    fun startReport() {
        if (_draftId.value != null) return
        _draftId.value = UUID.randomUUID().toString()
        _suggestedJob.value = projectId
        viewModelScope.launch {
            val known = container.projects.observeProjects().first().map { it.id }.toSet()
            _suggestedJob.value = projectId?.takeIf { it in known } ?: jobOnShift(container)
        }
    }

    /**
     * Abandons the report and takes its pictures with it.
     *
     * Without this, backing out of the dialog would leave photographs in the
     * table owned by an incident that was never filed: invisible, undeletable
     * from any screen, and counted by nothing. They are deleted here because
     * this is the only moment anything still knows they exist.
     */
    fun discardReport() = viewModelScope.launch {
        val id = _draftId.value ?: return@launch
        _draftId.value = null
        val actor = container.settings.settings.first().actorName
        container.photos.observeFor(PhotoRepository.Owner.INCIDENT, id).first()
            .forEach { container.photos.delete(it, actor) }
    }

    /** Where the camera should write, when somebody photographs it there and then. */
    fun newEvidenceTarget(): Pair<String, Uri> = container.photos.newCameraTarget()

    fun evidenceCaptured(photoId: String) = viewModelScope.launch {
        val id = _draftId.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.photos.recordCameraPhoto(
            id = photoId,
            ownerType = PhotoRepository.Owner.INCIDENT,
            ownerId = id,
            actorName = actor,
        )
    }

    /**
     * Attaches a still or a video from the gallery.
     *
     * Which of the two it is comes from the content resolver inside the
     * repository, not from here. A screen guessing is a screen that can guess
     * wrong, and a video written to a .jpg and put through the watermarker is
     * destroyed evidence.
     */
    fun addEvidence(uri: Uri) = viewModelScope.launch {
        val id = _draftId.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.photos.importPhoto(
            source = uri,
            ownerType = PhotoRepository.Owner.INCIDENT,
            ownerId = id,
            actorName = actor,
        )
    }

    /**
     * Files it.
     *
     * No location is captured. An incident is reported by somebody standing
     * where it happened, and taking a coordinate off their phone to prove it
     * is surveillance the job does not need — the same rule the rest of the
     * app follows.
     *
     * The evidence is counted out of the photo table rather than taken from
     * the screen. A count passed in is a count that can be wrong, and the
     * whole point of the rule is that the row and the pictures agree.
     */
    fun report(severity: Incidents.Severity, description: String, cost: Double?, jobId: String?) =
        viewModelScope.launch {
            val id = _draftId.value ?: return@launch
            val session = container.session.state.first() as? SessionRepository.State.SignedIn
            // Somebody who may not see the money is not asked for it, and
            // what reaches here from them is not taken as a figure.
            val costTaken = cost.takeIf { session?.canRead(Lens.MONEY) == true }
            // Only one of the company's own jobs; anything else is no job.
            val job = jobId?.let { wanted -> container.projects.observeProjects().first().firstOrNull { it.id == wanted } }
            val attached = container.photos.countFor(PhotoRepository.Owner.INCIDENT, id)
            val report = Incidents.Report(
                description = description.trim(),
                evidenceCount = attached,
                cost = costTaken,
            )
            if (!Incidents.canReport(report)) return@launch

            val actor = container.settings.settings.first().actorName
            val now = System.currentTimeMillis()
            container.safety.reportIncident(
                IncidentEntity(
                    id = id,
                    projectId = job?.id,
                    severity = severity.name,
                    description = report.description,
                    occurredAt = now,
                    reportedByName = actor.ifBlank { "unknown" },
                    createdAt = now,
                    costAmount = costTaken,
                    // The job's company when there is a job, and the
                    // reporter's otherwise: never the unattributed mark,
                    // which is only for what was filed before this asked.
                    companyId = job?.companyId ?: session?.active?.companyId,
                ),
            )
            // Cleared, not discarded: the pictures now belong to a filed
            // report and must stay exactly where they are.
            _draftId.value = null
        }
}
