package il.co.tradesmanager.ui.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.money.JobFinancials
import il.co.tradesmanager.data.local.entity.CostEntryEntity
import il.co.tradesmanager.data.local.entity.InvoiceEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.VariationEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MoneyViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    data class State(
        val project: ProjectEntity? = null,
        val financials: JobFinancials = JobFinancials(),
        val costs: List<CostEntryEntity> = emptyList(),
        val variations: List<VariationEntity> = emptyList(),
        val invoices: List<InvoiceEntity> = emptyList(),
    )

    /**
     * The job's money, only for somebody whose role reads the money lens.
     * The screen is not reached otherwise; this does not rely on that.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<State> = container.session.state
        .flatMapLatest { session ->
            val role = (session as? SessionRepository.State.SignedIn)?.role
            if (role == null || !role.canRead(Lens.MONEY)) {
                flowOf(State())
            } else {
                combine(
                    container.projects.observeProject(projectId),
                    container.money.observeFinancials(projectId),
                    container.money.observeCosts(projectId),
                    container.money.observeVariations(projectId),
                    container.money.observeInvoices(projectId),
                ) { project, financials, costs, variations, invoices ->
                    State(project, financials, costs, variations, invoices)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    private suspend fun actor(): String = container.settings.settings.first().actorName

    /** Runs [block] only for somebody whose role writes the money lens; anybody else changes nothing. */
    private fun write(block: suspend (actor: String) -> Unit) = viewModelScope.launch {
        val role = (container.session.state.first() as? SessionRepository.State.SignedIn)?.role
        if (role == null || !role.canWrite(Lens.MONEY)) return@launch
        block(actor())
    }

    fun setBudget(contractValue: Double, vatRate: Double) = write { actor ->
        container.money.setBudget(projectId, contractValue, vatRate, null, actor)
    }

    fun addCost(category: String, description: String, amount: Double, supplierRef: String?) =
        write { actor ->
            container.money.addCost(
                projectId = projectId,
                category = category,
                description = description,
                amount = amount,
                incurredOn = System.currentTimeMillis(),
                supplierInvoiceRef = supplierRef,
                actorName = actor,
            )
        }

    fun removeCost(cost: CostEntryEntity) = write { actor -> container.money.removeCost(cost, actor) }

    fun raiseVariation(title: String, amount: Double) = write { actor ->
        container.money.raiseVariation(projectId, title, amount, null, actor)
    }

    fun decideVariation(variation: VariationEntity, approved: Boolean) = write { actor ->
        container.money.decideVariation(variation, approved, actor)
    }

    fun removeVariation(variation: VariationEntity) = write { actor -> container.money.removeVariation(variation, actor) }

    fun addInvoice(number: String, amount: Double) = write { actor ->
        container.money.addInvoice(
            projectId = projectId,
            number = number,
            amount = amount,
            // Snapshotted from the job now, so the invoice keeps this rate
            // even if the job's rate is changed later.
            vatRate = state.value.financials.vatRate,
            issuedOn = System.currentTimeMillis(),
            dueOn = null,
            actorName = actor,
        )
    }

    fun markPaid(invoice: InvoiceEntity) = write { actor -> container.money.markInvoicePaid(invoice, actor) }

    fun removeInvoice(invoice: InvoiceEntity) = write { actor -> container.money.removeInvoice(invoice, actor) }
}
