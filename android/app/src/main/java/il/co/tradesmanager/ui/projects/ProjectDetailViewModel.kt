package il.co.tradesmanager.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import il.co.tradesmanager.data.local.entity.CatalogItemEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.ProjectMaterialEntity
import il.co.tradesmanager.data.local.entity.ProjectTaskEntity
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.SessionRepository
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
        /** Catalogue id -> category, so a material line can pick its icon. */
        val categories: Map<String, String> = emptyMap(),
        val images: List<PhotoEntity> = emptyList(),
    ) {
        /** A job has exactly one plan; the rest are progress photos. */
        val plan: PhotoEntity?
            get() = images.firstOrNull { it.ownerType == PhotoRepository.Owner.PROJECT_PLAN }

        fun categoryOf(material: ProjectMaterialEntity): String =
            material.catalogItemId?.let { categories[it] }.orEmpty()

        /** Task completion drives the progress bar; an empty list is 0, not NaN. */
        val progress: Double
            get() = if (tasks.isEmpty()) 0.0 else tasks.count { it.isDone }.toDouble() / tasks.size
    }

    val state: StateFlow<State> = combine(
        container.projects.observeProject(projectId),
        container.projects.observeMaterials(projectId),
        container.projects.observeTasks(projectId),
        container.catalogDao.observeCategories(),
        container.photos.observeProjectImages(projectId),
    ) { project, materials, tasks, categories, images ->
        State(project, materials, tasks, categories.associate { it.id to it.category }, images)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    /** What the person looking at this job is allowed to see and change. */
    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    fun newCameraTarget(): Pair<String, Uri> = container.photos.newCameraTarget()

    /**
     * The very first image added to a job becomes its plan, because that is
     * almost always what it is — someone photographing the drawing before
     * they start.
     */
    fun onCaptured(photoId: String) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.photos.recordCameraPhoto(
            id = photoId,
            ownerType = ownerTypeForNewImage(),
            ownerId = projectId,
            actorName = actor,
        )
    }

    fun onPicked(uri: Uri) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.photos.importPhoto(
            source = uri,
            ownerType = ownerTypeForNewImage(),
            ownerId = projectId,
            actorName = actor,
        )
    }

    private fun ownerTypeForNewImage(): String =
        if (state.value.plan == null) {
            PhotoRepository.Owner.PROJECT_PLAN
        } else {
            PhotoRepository.Owner.PROJECT_PHOTO
        }

    fun setAsPlan(photo: PhotoEntity) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.photos.markAsPlan(photo, state.value.plan, actor)
    }

    fun deletePhoto(photo: PhotoEntity) = viewModelScope.launch {
        container.photos.delete(photo, container.settings.settings.first().actorName)
    }

    /**
     * Catalogue matches for the add-material dialog. Returns the raw rows so
     * the caller resolves names in whatever language is on screen — the same
     * search in Hebrew and in Arabic hits the same rows.
     */
    suspend fun searchCatalog(query: String): List<CatalogItemEntity> {
        if (query.isBlank()) return emptyList()
        val tradeIds = container.catalogDao.selectedTradeIds()
        return container.catalogDao.searchCatalogItems(tradeIds, query.trim())
    }

    fun addMaterial(label: String, unit: String, quantity: Double, catalogItemId: String?) =
        viewModelScope.launch {
            val actor = container.settings.settings.first().actorName
            container.projects.addMaterial(
                projectId = projectId,
                label = label,
                unit = unit,
                quantity = quantity,
                catalogItemId = catalogItemId,
                actorName = actor,
            )
        }

    fun removeMaterial(material: ProjectMaterialEntity) = viewModelScope.launch {
        container.projects.removeMaterial(material, container.settings.settings.first().actorName)
    }

    fun addTask(title: String) = viewModelScope.launch {
        container.projects.addTask(projectId, title, container.settings.settings.first().actorName)
    }

    fun removeTask(task: ProjectTaskEntity) = viewModelScope.launch {
        container.projects.removeTask(task, container.settings.settings.first().actorName)
    }

    fun setTaskDone(taskId: String, done: Boolean) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.projects.setTaskDone(taskId, done, actor)
    }
}
