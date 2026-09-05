package il.co.tradesmanager.ui.evidence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.local.entity.PermitEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PermitsViewModel(private val container: AppContainer) : ViewModel() {

    val permits: StateFlow<List<PermitEntity>> = container.evidence.observePermits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val projects: StateFlow<List<ProjectEntity>> = container.projects.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    /**
     * [precautions] arrives already resolved from the screen that displayed
     * it, so the permit records the wording the issuer actually read.
     */
    fun raise(
        projectId: String?,
        type: String,
        description: String,
        location: String?,
        issuedToName: String,
        precautions: List<String>,
        onRaised: (String) -> Unit,
    ) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        val permit = container.evidence.raisePermit(
            projectId = projectId,
            type = type,
            description = description,
            location = location,
            issuedToName = issuedToName,
            precautions = precautions,
            actorName = actor,
        )
        onRaised(permit.id)
    }
}
