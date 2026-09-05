package il.co.tradesmanager.ui.concrete

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.local.entity.ConcretePourEntity
import il.co.tradesmanager.data.local.entity.ConcreteTicketEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Pours on a job, and the trucks feeding whichever one is open.
 *
 * One view model for both because on a slab they are one screen's worth of
 * attention: somebody standing by the pump is watching trucks, not navigating.
 */
class ConcreteViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    private val _openPourId = MutableStateFlow<String?>(null)
    val openPourId: StateFlow<String?> = _openPourId.asStateFlow()

    val pours: StateFlow<List<ConcretePourEntity>> = container.concrete.observePours(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val openPour: StateFlow<ConcretePourEntity?> = _openPourId
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.concrete.observePour(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val tickets: StateFlow<List<ConcreteTicketEntity>> = _openPourId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.concrete.observeTickets(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val placedVolume: StateFlow<Double> = _openPourId
        .flatMapLatest { id ->
            if (id == null) flowOf(0.0) else container.concrete.observePlacedVolume(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    fun open(pourId: String?) { _openPourId.value = pourId }

    fun startPour(
        element: String,
        mixDesign: String?,
        orderedVolume: Double?,
        supplierName: String?,
        temperatureCelsius: Double?,
    ) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        val pour = container.concrete.startPour(
            projectId = projectId,
            element = element,
            mixDesign = mixDesign,
            orderedVolume = orderedVolume,
            supplierName = supplierName,
            temperatureCelsius = temperatureCelsius,
            actorName = actor,
        )
        _openPourId.value = pour.id
    }

    fun finishPour(notes: String?) = viewModelScope.launch {
        val pour = openPour.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.concrete.completePour(pour, notes, actor)
    }

    fun addTicket(
        ticketNumber: String?,
        truckNumber: String?,
        volume: Double,
        dispatchedAt: Long,
    ) = viewModelScope.launch {
        val id = _openPourId.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.concrete.addTicket(id, ticketNumber, truckNumber, volume, dispatchedAt, actor)
    }

    fun recordSlump(ticket: ConcreteTicketEntity, slumpCm: Double?) = viewModelScope.launch {
        container.concrete.recordSlump(ticket, slumpCm)
    }

    fun markPlaced(ticket: ConcreteTicketEntity) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.concrete.markPlaced(ticket, actor)
    }

    fun reject(ticket: ConcreteTicketEntity, reason: String?) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.concrete.reject(ticket, reason, actor)
    }
}
