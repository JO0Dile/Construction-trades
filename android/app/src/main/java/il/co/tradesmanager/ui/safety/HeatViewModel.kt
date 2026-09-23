package il.co.tradesmanager.ui.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.safety.Heat
import il.co.tradesmanager.data.local.entity.HeatCheckEntity
import il.co.tradesmanager.data.repository.HeatRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The heat check screen: a reading, what it means, and the record of past ones. */
class HeatViewModel(
    private val container: AppContainer,
) : ViewModel() {

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    private val signedIn: SessionRepository.State.SignedIn?
        get() = session.value as? SessionRepository.State.SignedIn

    /** Whether this account may record one. The repository checks again. */
    val mayRecord: StateFlow<Boolean> = session
        .map { state ->
            (state as? SessionRepository.State.SignedIn)?.role?.canWrite(Lens.EVIDENCE) == true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** This firm's past checks, newest first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val history: StateFlow<List<HeatCheckEntity>> = session
        .flatMapLatest { state ->
            val active = (state as? SessionRepository.State.SignedIn)?.active
            if (active == null) {
                flowOf(emptyList())
            } else {
                container.heat.observeForCompany(active.companyId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Job names by id, so a record says which site rather than printing an id. */
    val jobNames: StateFlow<Map<String, String>> = container.projects.observeProjects()
        .map { jobs -> jobs.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _refusal = MutableStateFlow<HeatRepository.Refusal?>(null)
    val refusal: StateFlow<HeatRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    /** One event per record that went through, so the screen can say so and clear its form. */
    private val _recorded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val recorded: SharedFlow<Unit> = _recorded.asSharedFlow()

    fun record(
        temperatureC: Double,
        humidityPercent: Double,
        inSun: Boolean,
        measures: Set<Heat.Measure>,
        note: String,
    ) = viewModelScope.launch {
        val me = signedIn
        if (me == null) {
            _refusal.value = HeatRepository.Refusal.NOT_ALLOWED
            return@launch
        }
        container.heat.record(
            role = me.role,
            companyId = me.active?.companyId,
            temperatureC = temperatureC,
            humidityPercent = humidityPercent,
            inSun = inSun,
            measures = measures,
            note = note,
            byAccountId = me.account.id,
            byName = me.account.displayName,
        ).onSuccess {
            _recorded.tryEmit(Unit)
        }.onFailure { failure ->
            // Anything that is not a refusal still says something, and says
            // the right thing: a full disk is not a missing permission.
            _refusal.value = (failure as? HeatRepository.Refused)?.refusal
                ?: HeatRepository.Refusal.UNKNOWN
        }
    }
}
