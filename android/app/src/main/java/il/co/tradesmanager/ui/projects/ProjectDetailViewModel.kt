package il.co.tradesmanager.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.ProjectMaterialEntity
import il.co.tradesmanager.data.local.entity.ProjectTaskEntity
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectDetailViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    data class State(
        val project: ProjectEntity? = null,
        val materials: List<ProjectMaterialEntity> = emptyList(),
        val tasks: List<ProjectTaskEntity> = emptyList(),
    ) {
        /** Task completion drives the progress bar; an empty list is 0, not NaN. */
        val progress: Double
            get() = if (tasks.isEmpty()) 0.0 else tasks.count { it.isDone }.toDouble() / tasks.size
    }

    val state: StateFlow<State> = combine(
        container.projects.observeProject(projectId),
        container.projects.observeMaterials(projectId),
        container.projects.observeTasks(projectId),
    ) { project, materials, tasks -> State(project, materials, tasks) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun setTaskDone(taskId: String, done: Boolean) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.projects.setTaskDone(taskId, done, actor)
    }
}
