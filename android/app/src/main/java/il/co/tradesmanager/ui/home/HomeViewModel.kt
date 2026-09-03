package il.co.tradesmanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.TaskBlockEntity
import il.co.tradesmanager.di.AppContainer
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(container: AppContainer) : ViewModel() {

    data class State(
        val today: List<TaskBlockEntity> = emptyList(),
        val lowStock: List<InventoryItemEntity> = emptyList(),
        val activeProjects: List<ProjectEntity> = emptyList(),
        val openChecklists: Int = 0,
    )

    val state: StateFlow<State> = combine(
        container.schedule.observeDay(LocalDate.now()),
        container.inventory.observeLowStock(),
        container.projects.observeActive(),
        container.safety.observeRuns(),
    ) { today, lowStock, projects, runs ->
        State(
            today = today,
            lowStock = lowStock,
            activeProjects = projects,
            openChecklists = runs.count { it.completedAt == null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())
}
