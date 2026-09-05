package il.co.tradesmanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.money.JobFinancials
import il.co.tradesmanager.core.people.Expiry
import il.co.tradesmanager.data.local.entity.CertificationEntity
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.TaskBlockEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    data class State(
        val today: List<TaskBlockEntity> = emptyList(),
        val lowStock: List<InventoryItemEntity> = emptyList(),
        val activeProjects: List<ProjectEntity> = emptyList(),
        val openChecklists: Int = 0,
        val overdue: List<ProjectEntity> = emptyList(),
    )

    /**
     * The cross-job numbers. A second flow rather than more fields on [State]
     * because they are read by different people: a foreman never sees the
     * portfolio, and the finance clerk never sees today's blocks.
     */
    data class Portfolio(
        val money: JobFinancials = JobFinancials(),
        val ticketsNeedingAttention: List<CertificationEntity> = emptyList(),
    )

    val state: StateFlow<State> = combine(
        container.schedule.observeDay(LocalDate.now()),
        container.inventory.observeLowStock(),
        container.projects.observeActive(),
        container.safety.observeRuns(),
        container.projects.observeOverdue(),
    ) { today, lowStock, projects, runs, overdue ->
        State(
            today = today,
            lowStock = lowStock,
            activeProjects = projects,
            openChecklists = runs.count { it.completedAt == null },
            overdue = overdue,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    val portfolio: StateFlow<Portfolio> = combine(
        container.money.observePortfolio(),
        container.certifications.observeNeedingAttention(),
    ) { money, tickets ->
        Portfolio(money = money, ticketsNeedingAttention = tickets)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Portfolio())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    /** Expired tickets outrank ones merely expiring, for the tile's colour. */
    fun anyExpired(tickets: List<CertificationEntity>, now: Long = System.currentTimeMillis()) =
        tickets.any { Expiry.state(it.expiresOn, now) == Expiry.State.EXPIRED }
}
