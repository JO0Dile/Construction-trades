package il.co.tradesmanager.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Changes
import il.co.tradesmanager.core.money.JobFinancials
import il.co.tradesmanager.core.people.Expiry
import il.co.tradesmanager.data.local.entity.AuditLogEntity
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

    /**
     * What has changed lately, as this person is entitled to hear it.
     *
     * Built off the audit trail rather than a second table of notices. Every
     * change is already recorded there, with who did it and what it was, and
     * a parallel feed written by hand would be a feed that drifts from the
     * record it is supposed to be describing.
     *
     * Filtered by [Changes.visibleTo], which asks the same lens grid the
     * navigation bar is built from. A labourer sees the programme move and the
     * drawing replaced; they do not see a cost line or another person's row.
     *
     * Seeded and system rows are dropped. "Loaded catalogue v3: 527 items" is
     * a true entry and nobody wants to be told it every time they open the
     * app; a feed people scroll past is a feed that hides the one line that
     * mattered.
     */
    val changes: StateFlow<List<AuditLogEntity>> = combine(
        container.auditTrail.recent(limit = FEED_DEPTH),
        session,
    ) { entries, state ->
        val role = (state as? SessionRepository.State.SignedIn)?.role ?: return@combine emptyList()
        Changes.visibleTo(role, entries.filterNot { it.actorName == SYSTEM_ACTOR }) {
            it.entityType
        }.take(FEED_SHOWN)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Expired tickets outrank ones merely expiring, for the tile's colour. */
    fun anyExpired(tickets: List<CertificationEntity>, now: Long = System.currentTimeMillis()) =
        tickets.any { Expiry.state(it.expiresOn, now) == Expiry.State.EXPIRED }

    private companion object {
        /**
         * How far back to read, and how much of it to show.
         *
         * Read wide and show narrow, because the filter runs after the query:
         * a labourer whose last twenty changes were all cost lines would
         * otherwise see an empty feed and conclude nothing had happened.
         */
        const val FEED_DEPTH = 200
        const val FEED_SHOWN = 12

        /** The seeder's actor name. Its rows are true and nobody wants them. */
        const val SYSTEM_ACTOR = "system"
    }
}
