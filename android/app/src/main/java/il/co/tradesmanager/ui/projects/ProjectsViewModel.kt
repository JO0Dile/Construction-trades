package il.co.tradesmanager.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.catalog.ProjectKind
import il.co.tradesmanager.data.catalog.ProjectTemplateDto
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsViewModel(
    private val container: AppContainer,
    private val languageTag: String,
) : ViewModel() {

    val projects: StateFlow<List<ProjectEntity>> = container.projects.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Project id -> how far through its task list it is. */
    val progress: StateFlow<Map<String, Double>> = container.projects.observeProgress()
        .map { rows -> rows.associate { it.projectId to it.fraction } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Project id -> the picture to show for it, if one has been taken. */
    val covers: StateFlow<Map<String, String>> = container.photos.observeProjectCovers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val asGrid: StateFlow<Boolean> = container.settings.settings
        .map { it.projectsAsGrid }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _templates = MutableStateFlow<List<Pair<String, ProjectTemplateDto>>>(emptyList())
    val templates: StateFlow<List<Pair<String, ProjectTemplateDto>>> = _templates.asStateFlow()

    private val _kinds = MutableStateFlow<List<ProjectKind>>(emptyList())
    val kinds: StateFlow<List<ProjectKind>> = _kinds.asStateFlow()

    init {
        viewModelScope.launch {
            val tradeIds = container.catalogDao.selectedTradeIds()
            _templates.value = container.projects.templatesForTrades(tradeIds)
            _kinds.value = container.projects.projectKinds()
        }
    }

    fun setAsGrid(value: Boolean) = viewModelScope.launch {
        container.settings.setProjectsAsGrid(value)
    }

    /**
     * Creates the job and hands its id back, so the caller can open it —
     * an empty project is only useful once you are inside it adding things.
     */
    fun create(
        name: String,
        kindLabel: String,
        template: Pair<String, ProjectTemplateDto>?,
        onCreated: (String) -> Unit,
    ) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        val project = if (template == null) {
            container.projects.createBlank(
                name = name,
                kindLabel = kindLabel,
                actorName = actor,
            )
        } else {
            container.projects.createFromTemplate(
                tradeId = template.first,
                template = template.second,
                name = name,
                kindLabel = kindLabel,
                languageTag = languageTag,
                actorName = actor,
            )
        }
        onCreated(project.id)
    }
}
