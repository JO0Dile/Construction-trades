package il.co.tradesmanager.ui.concrete

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.evidence.CubeTests
import il.co.tradesmanager.core.evidence.Inspections
import il.co.tradesmanager.data.local.entity.ConcreteCubeSetEntity
import il.co.tradesmanager.data.local.entity.ConcretePourEntity
import il.co.tradesmanager.data.local.entity.ConcreteTicketEntity
import il.co.tradesmanager.data.local.entity.InspectionEntity
import il.co.tradesmanager.data.repository.ConcreteRepository
import il.co.tradesmanager.data.repository.InspectionRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

    /** The open pour's cube results, seven days before twenty-eight. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val cubeSets: StateFlow<List<ConcreteCubeSetEntity>> = _openPourId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else container.concrete.observeCubeSets(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The pours on this job with a result the engineer has to see, so the
     * list says so without anybody opening each one.
     */
    val needsEngineer: StateFlow<Set<String>> = combine(
        pours,
        container.concrete.observeCubeSetsForProject(projectId),
    ) { rows, sets ->
        val byId = rows.associateBy { it.id }
        sets.filter { set ->
            val pour = byId[set.pourId] ?: return@filter false
            CubeTests.judgeStored(set.ageDays, set.strengthsMpa, pour.mixDesign)?.needsEngineer == true
        }.map { it.pourId }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Whether this person sees the site's record, which is where inspections live. */
    val seesInspections: StateFlow<Boolean> = session
        .map { (it as? SessionRepository.State.SignedIn)?.role?.let { InspectionRepository.mayRead(it) } == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val mayClearPours: StateFlow<Boolean> = session
        .map { (it as? SessionRepository.State.SignedIn)?.role?.let { InspectionRepository.mayWrite(it) } == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The job's inspections, and none at all for somebody who may not read them. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val inspections = seesInspections.flatMapLatest { sees ->
        if (sees) container.inspections.observeForProject(projectId) else flowOf(emptyList())
    }

    /** The inspections that cleared each pour. */
    val inspectionsByPour: StateFlow<Map<String, List<InspectionEntity>>> = inspections
        .map { all -> all.mapNotNull { row -> row.clearedPourId?.let { it to row } }.groupBy({ it.first }, { it.second }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Pours with no inspection set against them, for the list. Empty for
     * somebody who cannot see inspections: to them the pour list says nothing
     * about inspections either way.
     */
    val uninspected: StateFlow<Set<String>> = combine(pours, inspectionsByPour, seesInspections) { rows, cleared, sees ->
        if (!sees) emptySet() else rows.map { it.id }.filterNot { it in cleared }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Passed inspections of the steel or the forms not yet set against a pour. */
    val clearable: StateFlow<List<InspectionEntity>> = inspections
        .map { all ->
            all.filter {
                it.clearedPourId == null &&
                    Inspections.kindOf(it.kind) in Inspections.POUR_KINDS &&
                    Inspections.passed(Inspections.resultOf(it.result))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _inspectionRefusal = MutableStateFlow<InspectionRepository.Refusal?>(null)
    val inspectionRefusal: StateFlow<InspectionRepository.Refusal?> = _inspectionRefusal.asStateFlow()

    fun clearInspectionRefusal() {
        _inspectionRefusal.value = null
    }

    /** Sets a passed inspection against the open pour. */
    fun clearOpenPour(inspectionId: String) = viewModelScope.launch {
        val pourId = _openPourId.value ?: return@launch
        val me = session.value as? SessionRepository.State.SignedIn
        if (me == null) {
            _inspectionRefusal.value = InspectionRepository.Refusal.NOT_ALLOWED
            return@launch
        }
        container.inspections.clearPour(me.role, inspectionId, pourId, me.account.displayName)
            .onFailure { failure ->
                _inspectionRefusal.value = (failure as? InspectionRepository.Refused)?.refusal
                    ?: InspectionRepository.Refusal.UNKNOWN
            }
    }

    private val _cubeRefusal = MutableStateFlow<ConcreteRepository.CubeRefusal?>(null)
    val cubeRefusal: StateFlow<ConcreteRepository.CubeRefusal?> = _cubeRefusal.asStateFlow()

    fun clearCubeRefusal() {
        _cubeRefusal.value = null
    }

    fun recordCubes(
        ageDays: Int,
        laboratory: String?,
        reportNumber: String?,
        strengthsMpa: List<Double>,
    ) = viewModelScope.launch {
        val id = _openPourId.value ?: return@launch
        val role = (session.value as? SessionRepository.State.SignedIn)?.role
        if (role == null) {
            _cubeRefusal.value = ConcreteRepository.CubeRefusal.NOT_ALLOWED
            return@launch
        }
        val actor = container.settings.settings.first().actorName
        container.concrete.recordCubes(
            role = role,
            pourId = id,
            ageDays = ageDays,
            testedAt = System.currentTimeMillis(),
            laboratory = laboratory,
            reportNumber = reportNumber,
            strengthsMpa = strengthsMpa,
            actorName = actor,
        ).onFailure { failure ->
            _cubeRefusal.value = (failure as? ConcreteRepository.CubesRefused)?.refusal
                ?: ConcreteRepository.CubeRefusal.UNKNOWN
        }
    }

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
