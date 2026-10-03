package il.co.tradesmanager.ui.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.safety.Incidents
import il.co.tradesmanager.core.safety.Investigations
import il.co.tradesmanager.data.local.entity.IncidentActionEntity
import il.co.tradesmanager.data.local.entity.IncidentEntity
import il.co.tradesmanager.data.local.entity.IncidentInvestigationEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.repository.InvestigationRepository
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
 * One incident and its investigation: what happened, what caused it, what
 * lay behind it, and the actions taken because of it.
 *
 * Shown only to a role that reads the site's record, and only when the
 * report is the company's -- or was filed before reports had a company, as
 * in the register. For anybody else the report is not loaded at all.
 */
class IncidentDetailViewModel(
    private val container: AppContainer,
    private val incidentId: String,
) : ViewModel() {

    private val signedIn: Flow<SessionRepository.State.SignedIn?> = container.session.state
        .map { it as? SessionRepository.State.SignedIn }

    @OptIn(ExperimentalCoroutinesApi::class)
    val incident: StateFlow<IncidentEntity?> = signedIn
        .flatMapLatest { session ->
            if (session == null || !InvestigationRepository.mayRead(session.role)) {
                flowOf(null)
            } else {
                container.safety.observeIncident(incidentId).map { incident ->
                    incident?.takeIf { Incidents.visibleTo(it.companyId, session.active?.companyId) }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val investigation: StateFlow<IncidentInvestigationEntity?> = incident
        .flatMapLatest { if (it == null) flowOf(null) else container.investigations.observe(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The actions, overdue first, then the soonest due, then done. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val actions: StateFlow<List<IncidentActionEntity>> = incident
        .flatMapLatest { if (it == null) flowOf(emptyList()) else container.investigations.observeActions(it.id) }
        .map { rows -> orderOf(rows, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The photographs filed with the report: evidence, so looked at and never taken off here. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val evidence: StateFlow<List<PhotoEntity>> = incident
        .flatMapLatest { if (it == null) flowOf(emptyList()) else container.photos.observeFor(PhotoRepository.Owner.INCIDENT, it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val jobName: StateFlow<String?> = combine(incident, container.projects.observeProjects()) { incident, jobs ->
        incident?.projectId?.let { id -> jobs.firstOrNull { it.id == id }?.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val canEdit: StateFlow<Boolean> = signedIn
        .map { it != null && InvestigationRepository.mayWrite(it.role) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _refusal = MutableStateFlow<InvestigationRepository.Refusal?>(null)

    /** Why the last write did not happen, said on the page. */
    val refusal: StateFlow<InvestigationRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    fun save(immediateCause: String, causes: Set<Investigations.Cause>, findings: String) =
        write { role, actor -> container.investigations.save(role, incidentId, immediateCause, causes, findings, actor) }

    fun raise(text: String, owner: String, dueOn: LocalDate?) =
        write { role, actor -> container.investigations.raise(role, incidentId, text, owner, dueOn, actor) }

    fun closeAction(actionId: String, note: String) =
        write { role, actor -> container.investigations.closeAction(role, actionId, note, actor) }

    fun close() = write { role, actor -> container.investigations.close(role, incidentId, actor) }

    private fun write(block: suspend (Role, String) -> Result<*>) = viewModelScope.launch {
        val session = container.session.state.first() as? SessionRepository.State.SignedIn
        if (session == null || incident.value == null) {
            _refusal.value = InvestigationRepository.Refusal.NOT_ALLOWED
            return@launch
        }
        val actor = container.settings.settings.first().actorName.ifBlank { session.account.displayName }
        block(session.role, actor)
            .onSuccess { _refusal.value = null }
            .onFailure { failure ->
                _refusal.value = (failure as? InvestigationRepository.Refused)?.refusal ?: InvestigationRepository.Refusal.UNKNOWN
            }
    }

    companion object {
        fun orderOf(rows: List<IncidentActionEntity>, today: LocalDate): List<IncidentActionEntity> {
            val sorts = rows.map {
                Investigations.ActionSort(
                    Investigations.actionState(it.dueOnDay?.let(LocalDate::ofEpochDay), it.closedAt != null, today),
                    it.dueOnDay,
                    it.number,
                )
            }
            return Investigations.actionOrder(sorts).map { rows[it] }
        }
    }
}
