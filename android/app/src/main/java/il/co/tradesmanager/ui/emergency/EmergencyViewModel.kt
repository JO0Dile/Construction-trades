package il.co.tradesmanager.ui.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.safety.EmergencySheet
import il.co.tradesmanager.data.local.entity.JobEmergencyEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.repository.EmergencySheetRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One job's emergency sheet, for everybody signed in on it. */
class EmergencyViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    private val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionRepository.State.Loading)

    val mayWrite: StateFlow<Boolean> = session
        .map { (it as? SessionRepository.State.SignedIn)?.role?.let { role -> EmergencySheetRepository.mayWrite(role) } == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The stored sheet; nothing for somebody not signed in. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val sheet: StateFlow<JobEmergencyEntity?> = session
        .flatMapLatest { state ->
            val role = (state as? SessionRepository.State.SignedIn)?.role
            if (role == null || !EmergencySheetRepository.mayRead(role)) {
                flowOf<JobEmergencyEntity?>(null)
            } else {
                container.emergencySheets.observe(projectId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The essentials still blank. Everything, before anything is saved. */
    val missing: StateFlow<List<EmergencySheet.Essential>> = sheet
        .map { EmergencySheet.missing(EmergencySheetRepository.sheetOf(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EmergencySheet.Essential.entries.toList())

    /** The job itself, for its name and the address to give the ambulance. */
    val job: StateFlow<ProjectEntity?> = container.projects.observeProjects()
        .map { jobs -> jobs.firstOrNull { it.id == projectId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _refusal = MutableStateFlow<EmergencySheetRepository.Refusal?>(null)
    val refusal: StateFlow<EmergencySheetRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    fun save(edited: EmergencySheet.Sheet) = viewModelScope.launch {
        val me = session.value as? SessionRepository.State.SignedIn
        if (me == null) {
            _refusal.value = EmergencySheetRepository.Refusal.NOT_ALLOWED
            return@launch
        }
        container.emergencySheets.save(me.role, projectId, edited, me.account.displayName).onFailure { failure ->
            _refusal.value = (failure as? EmergencySheetRepository.Refused)?.refusal ?: EmergencySheetRepository.Refusal.UNKNOWN
        }
    }
}
