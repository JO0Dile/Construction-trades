package il.co.tradesmanager.ui.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.catalog.ProjectTemplateDto
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsViewModel(
    private val container: AppContainer,
    private val languageTag: String,
) : ViewModel() {

    val projects: StateFlow<List<ProjectEntity>> = container.projects.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _templates = MutableStateFlow<List<Pair<String, ProjectTemplateDto>>>(emptyList())
    val templates: StateFlow<List<Pair<String, ProjectTemplateDto>>> = _templates.asStateFlow()

    init {
        viewModelScope.launch {
            val tradeIds = container.catalogDao.selectedTradeIds()
            _templates.value = container.projects.templatesForTrades(tradeIds)
        }
    }

    fun createFromTemplate(tradeId: String, template: ProjectTemplateDto, name: String, kindLabel: String) =
        viewModelScope.launch {
            val actor = container.settings.settings.first().actorName
            container.projects.createFromTemplate(
                tradeId = tradeId,
                template = template,
                name = name,
                kindLabel = kindLabel,
                languageTag = languageTag,
                actorName = actor,
            )
        }
}
