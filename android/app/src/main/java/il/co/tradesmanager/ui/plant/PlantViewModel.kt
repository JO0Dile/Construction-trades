package il.co.tradesmanager.ui.plant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.local.entity.EquipmentEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlantViewModel(private val container: AppContainer) : ViewModel() {

    val equipment: StateFlow<List<EquipmentEntity>> = container.equipment.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Jobs a machine can be sent to. Only live ones. */
    val projects: StateFlow<List<ProjectEntity>> = container.projects.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    private suspend fun actor(): String = container.settings.settings.first().actorName

    fun add(
        name: String,
        ownership: String,
        serialNumber: String?,
        hireRatePerDay: Double?,
        serviceDueOn: Long?,
    ) = viewModelScope.launch {
        container.equipment.add(name, ownership, serialNumber, hireRatePerDay, serviceDueOn, actor())
    }

    fun assign(equipment: EquipmentEntity, projectId: String?) = viewModelScope.launch {
        container.equipment.assign(equipment, projectId, actor())
    }

    fun setStatus(equipment: EquipmentEntity, status: String) = viewModelScope.launch {
        container.equipment.setStatus(equipment, status, actor())
    }

    fun recordService(equipment: EquipmentEntity, nextDueOn: Long?) = viewModelScope.launch {
        container.equipment.recordService(equipment, nextDueOn, actor())
    }

    fun remove(equipment: EquipmentEntity) = viewModelScope.launch {
        container.equipment.remove(equipment, actor())
    }
}
