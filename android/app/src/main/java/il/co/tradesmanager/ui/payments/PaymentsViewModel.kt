package il.co.tradesmanager.ui.payments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.money.Payments
import il.co.tradesmanager.data.local.entity.PaymentApplicationEntity
import il.co.tradesmanager.data.local.entity.PaymentApplicationLineEntity
import il.co.tradesmanager.data.repository.PaymentsRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    /**
     * Set when a write was refused. See NotSavedDialog: the answer used to be
     * thrown away, and a refused write looked like a button that did nothing.
     */
    private val _notSaved = MutableStateFlow(false)
    val notSaved: StateFlow<Boolean> = _notSaved.asStateFlow()

    fun clearNotSaved() {
        _notSaved.value = false
    }

    private val _openId = MutableStateFlow<String?>(null)
    val openId: StateFlow<String?> = _openId.asStateFlow()

    /** Whether the signed-in role reads the money lens. Nothing below is sent to anybody else. */
    private val readsMoney = container.session.state
        .map { (it as? SessionRepository.State.SignedIn)?.role?.canRead(Lens.MONEY) == true }

    @OptIn(ExperimentalCoroutinesApi::class)
    val applications: StateFlow<List<PaymentApplicationEntity>> = readsMoney
        .flatMapLatest { reads -> if (reads) container.payments.observeForProject(projectId) else flowOf(emptyList<PaymentApplicationEntity>()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val open: StateFlow<PaymentApplicationEntity?> = combine(_openId, readsMoney) { id, reads -> id.takeIf { reads } }
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.payments.observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * What the open application is made of, package by package.
     *
     * Empty for an application somebody typed a figure into, which is a real
     * and allowed way to raise one — the screen says so rather than showing an
     * empty list and letting it read as a claim for nothing.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val lines: StateFlow<List<PaymentApplicationLineEntity>> = combine(_openId, readsMoney) { id, reads -> id.takeIf { reads } }
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.payments.observeLines(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The revised contract, which is what the retention limit is a share of.
     *
     * Retention is held against the work being done rather than against what
     * was first agreed, so variations count.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val contractSum: StateFlow<Double> = readsMoney
        .flatMapLatest { reads -> if (reads) container.money.observeFinancials(projectId).map { it.revisedContract } else flowOf(0.0) }
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
        if (!writesMoney()) return@launch
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
        if (!container.payments.certify(application, certifiedGrossToDate, contractSum.value, actor)) {
            _notSaved.value = true
        }
    }

    fun markPaid() = withOpen { application, actor ->
        if (!container.payments.markPaid(application, contractSum.value, actor)) _notSaved.value = true
    }

    fun reject(notes: String?) = withOpen { application, actor ->
        container.payments.reject(application, notes, actor)
    }

    private fun withOpen(block: suspend (PaymentApplicationEntity, String) -> Unit) =
        viewModelScope.launch {
            if (!writesMoney()) return@launch
            val application = open.value ?: return@launch
            block(application, container.settings.settings.first().actorName)
        }

    /** Only a role that writes the money lens raises, certifies or pays; anybody else changes nothing. */
    private suspend fun writesMoney(): Boolean =
        (container.session.state.first() as? SessionRepository.State.SignedIn)?.role?.canWrite(Lens.MONEY) == true

    companion object {

        /**
         * What an application is worth as it currently stands.
         *
         * Assessed on the certified figure once there is one, and on the claim
         * until then — so a draft shows what it would be worth if it were
         * agreed in full, which is the number somebody raising it wants.
         *
         * [applications] is the whole sequence on this job, because what is due
         * this time cannot be read off one application on its own. See
         * [previouslyPaidNet].
         */
        fun assess(
            application: PaymentApplicationEntity,
            applications: List<PaymentApplicationEntity>,
            contractSum: Double,
        ): Payments.Assessment = Payments.assess(
            grossToDate = application.certifiedGrossToDate ?: application.claimedGrossToDate,
            previouslyPaidNet = previouslyPaidNet(application, applications, contractSum),
            contractSum = contractSum,
            retentionRate = application.retentionRate,
            retentionLimit = application.retentionLimit,
        )

        /**
         * What had been paid before this application, as things stand now.
         *
         * Read off the sequence on screen rather than out of the application's
         * own row. The row carries a copy, written when it was raised, and by
         * the time it is certified a month later that copy is usually a payment
         * out of date — which would put the previous application's work back
         * into this one's cheque.
         */
        fun previouslyPaidNet(
            application: PaymentApplicationEntity,
            applications: List<PaymentApplicationEntity>,
            contractSum: Double,
        ): Double = Payments.previouslyPaidNet(
            number = application.applicationNumber,
            direction = application.direction,
            paid = applications.mapNotNull { it.settled() },
            contractSum = contractSum,
        )

        /** This application as money that has gone out, or null if it has not. */
        private fun PaymentApplicationEntity.settled(): Payments.Settled? {
            if (paidAt == null) return null
            val certified = certifiedGrossToDate ?: return null
            return Payments.Settled(
                number = applicationNumber,
                direction = direction,
                certifiedGrossToDate = certified,
                retentionRate = retentionRate,
                retentionLimit = retentionLimit,
            )
        }

        fun termsOf(application: PaymentApplicationEntity): Payments.Terms =
            runCatching { Payments.Terms.valueOf(application.terms) }
                .getOrDefault(Payments.Terms.SHOTEF_30)

        fun directionOf(application: PaymentApplicationEntity): PaymentsRepository.Direction =
            runCatching { PaymentsRepository.Direction.valueOf(application.direction) }
                .getOrDefault(PaymentsRepository.Direction.RECEIVABLE)
    }
}
