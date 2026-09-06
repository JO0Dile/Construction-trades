package il.co.tradesmanager.ui.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.money.Payments
import il.co.tradesmanager.data.local.entity.PaymentApplicationEntity
import il.co.tradesmanager.data.repository.PaymentsRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Payment applications on one job, and whichever one is open. */
class PaymentsViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    private val _openId = MutableStateFlow<String?>(null)
    val openId: StateFlow<String?> = _openId.asStateFlow()

    val applications: StateFlow<List<PaymentApplicationEntity>> = container.payments
        .observeForProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val open: StateFlow<PaymentApplicationEntity?> = _openId
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.payments.observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The revised contract, which is what the retention limit is a share of.
     *
     * Retention is held against the work being done rather than against what
     * was first agreed, so variations count.
     */
    val contractSum: StateFlow<Double> = container.money.observeFinancials(projectId)
        .map { it.revisedContract }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    fun openApplication(id: String?) { _openId.value = id }

    fun raise(
        direction: PaymentsRepository.Direction,
        partyName: String,
        claimedGrossToDate: Double,
        retentionRate: Double,
        terms: Payments.Terms,
    ) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        val raised = container.payments.raise(
            projectId = projectId,
            direction = direction,
            partyName = partyName,
            claimedGrossToDate = claimedGrossToDate,
            contractSum = contractSum.value,
            retentionRate = retentionRate,
            terms = terms,
            actorName = actor,
        )
        _openId.value = raised.id
    }

    fun updateClaim(claimedGrossToDate: Double) = withOpen { application, actor ->
        container.payments.updateClaim(application, claimedGrossToDate, actor)
    }

    fun submit() = withOpen { application, actor ->
        container.payments.submit(application, actor)
    }

    fun certify(certifiedGrossToDate: Double) = withOpen { application, actor ->
        container.payments.certify(application, certifiedGrossToDate, actor)
    }

    fun markPaid() = withOpen { application, actor ->
        container.payments.markPaid(application, actor)
    }

    fun reject(notes: String?) = withOpen { application, actor ->
        container.payments.reject(application, notes, actor)
    }

    private fun withOpen(block: suspend (PaymentApplicationEntity, String) -> Unit) =
        viewModelScope.launch {
            val application = open.value ?: return@launch
            block(application, container.settings.settings.first().actorName)
        }

    companion object {

        /**
         * What an application is worth as it currently stands.
         *
         * Assessed on the certified figure once there is one, and on the claim
         * until then — so a draft shows what it would be worth if it were
         * agreed in full, which is the number somebody raising it wants.
         */
        fun assess(
            application: PaymentApplicationEntity,
            contractSum: Double,
        ): Payments.Assessment = Payments.assess(
            grossToDate = application.certifiedGrossToDate ?: application.claimedGrossToDate,
            previouslyPaidNet = application.previouslyPaidNet,
            contractSum = contractSum,
            retentionRate = application.retentionRate,
            retentionLimit = application.retentionLimit,
        )

        fun termsOf(application: PaymentApplicationEntity): Payments.Terms =
            runCatching { Payments.Terms.valueOf(application.terms) }
                .getOrDefault(Payments.Terms.SHOTEF_30)

        fun directionOf(application: PaymentApplicationEntity): PaymentsRepository.Direction =
            runCatching { PaymentsRepository.Direction.valueOf(application.direction) }
                .getOrDefault(PaymentsRepository.Direction.RECEIVABLE)
    }
}
