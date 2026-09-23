package il.co.tradesmanager.ui.waste

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.evidence.Waste
import il.co.tradesmanager.data.local.entity.WasteLoadEntity
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.data.repository.WasteRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One job's waste register: the loads, what they add up to, and which are unproven. */
class WasteViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    private val signedIn: SessionRepository.State.SignedIn?
        get() = session.value as? SessionRepository.State.SignedIn

    val mayRecord: StateFlow<Boolean> = session
        .map { (it as? SessionRepository.State.SignedIn)?.role?.canWrite(Lens.EVIDENCE) == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val loads: StateFlow<List<WasteLoadEntity>> = container.waste.observeForProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Ticket photographs per load. */
    val ticketPhotos: StateFlow<Map<String, Int>> =
        container.photos.observeCountsFor(PhotoRepository.Owner.WASTE_TICKET)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** The job's loads summed per unit, by the same rule the handover pack uses. */
    val totals: StateFlow<List<Waste.Totals>> = combine(loads, ticketPhotos) { rows, photos ->
        Waste.totals(rows.mapNotNull { WasteRepository.asLoad(it, photos[it.id] ?: 0) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refusal = MutableStateFlow<WasteRepository.Refusal?>(null)
    val refusal: StateFlow<WasteRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    private fun refuse(failure: Throwable) {
        _refusal.value = (failure as? WasteRepository.Refused)?.refusal ?: WasteRepository.Refusal.UNKNOWN
    }

    private suspend fun actor(): String =
        signedIn?.account?.displayName ?: container.settings.settings.first().actorName

    fun record(
        stream: Waste.Stream,
        quantity: Double?,
        unit: Waste.Measurement,
        destination: Waste.Destination,
        facility: String,
        hauler: String,
        ticketNumber: String,
        notes: String,
        onRecorded: () -> Unit,
    ) = viewModelScope.launch {
        val me = signedIn
        if (me == null) {
            _refusal.value = WasteRepository.Refusal.NOT_ALLOWED
            return@launch
        }
        container.waste.record(
            role = me.role,
            projectId = projectId,
            stream = stream,
            // Nothing typed is no quantity, which the rules refuse with a
            // sentence rather than this screen deciding a number.
            quantity = quantity ?: 0.0,
            unit = unit,
            destination = destination,
            facility = facility,
            hauler = hauler,
            ticketNumber = ticketNumber,
            notes = notes,
            byAccountId = me.account.id,
            byName = me.account.displayName,
        ).onSuccess { onRecorded() }.onFailure(::refuse)
    }

    fun addTicketNumber(loadId: String, number: String) = viewModelScope.launch {
        container.waste.addTicketNumber(loadId, number, actor()).onFailure(::refuse)
    }

    /* ---------------------------------------------- photographing a ticket */

    /** Which load the camera or gallery is about to be for. */
    private var photoFor: String? = null

    fun photographingFor(loadId: String) {
        photoFor = loadId
    }

    fun newTicketTarget(): Pair<String, Uri> = container.photos.newCameraTarget()

    fun ticketCaptured(photoId: String) = viewModelScope.launch {
        val loadId = photoFor ?: return@launch
        container.photos.recordCameraPhoto(
            id = photoId,
            ownerType = PhotoRepository.Owner.WASTE_TICKET,
            ownerId = loadId,
            actorName = actor(),
        )
    }

    fun ticketPicked(uri: Uri) = viewModelScope.launch {
        val loadId = photoFor ?: return@launch
        container.photos.importPhoto(
            source = uri,
            ownerType = PhotoRepository.Owner.WASTE_TICKET,
            ownerId = loadId,
            actorName = actor(),
        )
    }
}
