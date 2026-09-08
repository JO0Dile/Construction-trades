package il.co.tradesmanager.ui.safety

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.safety.Incidents
import il.co.tradesmanager.data.local.entity.IncidentEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.di.AppContainer
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The site's incident register. */
class IncidentsViewModel(
    private val container: AppContainer,
    private val projectId: String?,
) : ViewModel() {

    val incidents: StateFlow<List<IncidentEntity>> = container.safety.observeIncidents()
        .map { all ->
            Incidents.order(
                items = all,
                occurredAt = { it.occurredAt },
                severity = { Incidents.parse(it.severity) },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        if (_draftId.value == null) _draftId.value = UUID.randomUUID().toString()
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
    fun report(severity: Incidents.Severity, description: String, cost: Double?) =
        viewModelScope.launch {
            val id = _draftId.value ?: return@launch
            val attached = container.photos.countFor(PhotoRepository.Owner.INCIDENT, id)
            val report = Incidents.Report(
                description = description.trim(),
                evidenceCount = attached,
                cost = cost,
            )
            if (!Incidents.canReport(report)) return@launch

            val actor = container.settings.settings.first().actorName
            val now = System.currentTimeMillis()
            container.safety.reportIncident(
                IncidentEntity(
                    id = id,
                    projectId = projectId,
                    severity = severity.name,
                    description = report.description,
                    occurredAt = now,
                    reportedByName = actor.ifBlank { "unknown" },
                    createdAt = now,
                    costAmount = cost,
                ),
            )
            // Cleared, not discarded: the pictures now belong to a filed
            // report and must stay exactly where they are.
            _draftId.value = null
        }
}
