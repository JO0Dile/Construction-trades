package il.co.tradesmanager.ui.money

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.money.JobFinancials
import il.co.tradesmanager.data.local.entity.CostEntryEntity
import il.co.tradesmanager.data.local.entity.InvoiceEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.VariationEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

    val state: StateFlow<State> = combine(
        container.projects.observeProject(projectId),
        container.money.observeFinancials(projectId),
        container.money.observeCosts(projectId),
        container.money.observeVariations(projectId),
        container.money.observeInvoices(projectId),
    ) { project, financials, costs, variations, invoices ->
        State(project, financials, costs, variations, invoices)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    private suspend fun actor(): String = container.settings.settings.first().actorName

    fun setBudget(contractValue: Double, vatRate: Double) = viewModelScope.launch {
        container.money.setBudget(projectId, contractValue, vatRate, null, actor())
    }

    fun addCost(category: String, description: String, amount: Double, supplierRef: String?) =
        viewModelScope.launch {
            container.money.addCost(
                projectId = projectId,
                category = category,
                description = description,
                amount = amount,
                incurredOn = System.currentTimeMillis(),
                supplierInvoiceRef = supplierRef,
                actorName = actor(),
            )
        }

    fun removeCost(cost: CostEntryEntity) = viewModelScope.launch {
        container.money.removeCost(cost, actor())
    }

    fun raiseVariation(title: String, amount: Double) = viewModelScope.launch {
        container.money.raiseVariation(projectId, title, amount, null, actor())
    }

    fun decideVariation(variation: VariationEntity, approved: Boolean) = viewModelScope.launch {
        container.money.decideVariation(variation, approved, actor())
    }

    fun removeVariation(variation: VariationEntity) = viewModelScope.launch {
        container.money.removeVariation(variation, actor())
    }

    fun addInvoice(number: String, amount: Double) = viewModelScope.launch {
        container.money.addInvoice(
            projectId = projectId,
            number = number,
            amount = amount,
            // Snapshotted from the job now, so the invoice keeps this rate
            // even if the job's rate is changed later.
            vatRate = state.value.financials.vatRate,
            issuedOn = System.currentTimeMillis(),
            dueOn = null,
            actorName = actor(),
        )
    }

    fun markPaid(invoice: InvoiceEntity) = viewModelScope.launch {
        container.money.markInvoicePaid(invoice, actor())
    }

    fun removeInvoice(invoice: InvoiceEntity) = viewModelScope.launch {
        container.money.removeInvoice(invoice, actor())
    }
}
