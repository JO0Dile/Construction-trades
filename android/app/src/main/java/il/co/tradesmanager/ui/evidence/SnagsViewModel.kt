package il.co.tradesmanager.ui.evidence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.SnagEntity
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SnagsViewModel(private val container: AppContainer) : ViewModel() {

    val snags: StateFlow<List<SnagEntity>> = container.evidence.observeSnags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val projects: StateFlow<List<ProjectEntity>> = container.projects.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * One thumbnail per snag: the picture that raised it.
     *
     * A snag list without pictures is a list of sentences somebody has to walk
     * the building to understand.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val thumbnails: StateFlow<Map<String, String>> = container.evidence.observeSnags()
        .flatMapLatest { rows ->
            container.photos.observeForOwners(
                PhotoRepository.Owner.SNAG_RAISED,
                rows.map { it.id },
            )
        }
        .map { photos: List<PhotoEntity> -> photos.associate { it.ownerId to it.uri } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    fun raise(
        projectId: String,
        title: String,
        location: String?,
        assignedToName: String?,
        dueOn: Long?,
        blocksHandover: Boolean,
        onRaised: (String) -> Unit,
    ) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        val snag = container.evidence.raiseSnag(
            projectId = projectId,
            title = title,
            location = location,
            assignedToName = assignedToName,
            tradeId = null,
            dueOn = dueOn,
            blocksHandover = blocksHandover,
            actorName = actor,
        )
        onRaised(snag.id)
    }
}
