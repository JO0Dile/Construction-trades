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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SnagsViewModel(private val container: AppContainer) : ViewModel() {

    val projects: StateFlow<List<ProjectEntity>> = container.projects.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The defects on the jobs somebody can actually see.
     *
     * A snag reaches its company through its job, so the filter is the job
     * list — which is already scoped. Deriving it that way rather than adding
     * a company column to snags means there is one definition of whose work
     * this is, and snags cannot drift out of step with the jobs they belong to.
     */
    val snags: StateFlow<List<SnagEntity>> = combine(
        container.evidence.observeSnags(),
        container.projects.observeProjects(),
    ) { rows, visible ->
        val ids = visible.map { it.id }.toSet()
        rows.filter { it.projectId in ids }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * One thumbnail per snag: the picture that raised it.
     *
     * A snag list without pictures is a list of sentences somebody has to walk
     * the building to understand.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val thumbnails: StateFlow<Map<String, String>> = snags
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
