package il.co.tradesmanager.ui.scaffold

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.local.entity.ScaffoldEntity
import il.co.tradesmanager.data.local.entity.ScaffoldInspectionEntity
import il.co.tradesmanager.data.repository.ScaffoldRepository
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

/** The register for one job, and whichever scaffold is open on it. */
class ScaffoldViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    private val _openId = MutableStateFlow<String?>(null)
    val openId: StateFlow<String?> = _openId.asStateFlow()

    val scaffolds: StateFlow<List<ScaffoldEntity>> = container.scaffolds
        .observeForProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val open: StateFlow<ScaffoldEntity?> = _openId
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.scaffolds.observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val inspections: StateFlow<List<ScaffoldInspectionEntity>> = _openId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.scaffolds.observeInspections(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    fun openScaffold(id: String?) { _openId.value = id }

    fun erect(location: String, type: String?, erectedBy: String?) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        val scaffold = container.scaffolds.erect(projectId, location, type, erectedBy, actor)
        _openId.value = scaffold.id
    }

    fun inspect(
        passed: Boolean,
        reason: ScaffoldRepository.Reason,
        defects: String?,
        actionTaken: String?,
        signature: String?,
    ) = viewModelScope.launch {
        val scaffold = open.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.scaffolds.inspect(
            scaffold = scaffold,
            passed = passed,
            reason = reason,
            defects = defects,
            actionTaken = actionTaken,
            signature = signature,
            inspectorName = actor,
        )
    }

    fun markAltered(note: String?) = viewModelScope.launch {
        val scaffold = open.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.scaffolds.markAltered(scaffold, note, actor)
    }

    fun dismantle() = viewModelScope.launch {
        val scaffold = open.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.scaffolds.dismantle(scaffold, actor)
    }
}
