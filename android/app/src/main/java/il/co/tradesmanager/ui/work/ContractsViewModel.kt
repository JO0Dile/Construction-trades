package il.co.tradesmanager.ui.work

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Commercial
import il.co.tradesmanager.core.money.Payments
import il.co.tradesmanager.core.work.Amendment
import il.co.tradesmanager.data.local.entity.ContractAmendmentEntity
import il.co.tradesmanager.data.local.entity.ContractEntity
import il.co.tradesmanager.data.repository.EngagementRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.data.repository.asAgreement
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

/**
 * The contracts this firm is a party to on one job.
 *
 * The list is filtered in SQL, not here. A query that fetched every contract
 * and let the screen pick would put the general contractor's figures in memory
 * on a crew leader's phone, and memory is readable. See
 * `core.access.Commercial` and `EngagementDao.observeContractsFor`.
 */
class ContractsViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    val orgId: StateFlow<String> = session
        .map { state ->
            val signedIn = state as? SessionRepository.State.SignedIn ?: return@map ""
            signedIn.active?.companyId ?: signedIn.account.id
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    @OptIn(ExperimentalCoroutinesApi::class)
    val contracts: StateFlow<List<ContractEntity>> = orgId
        .flatMapLatest { org ->
            if (org.isBlank()) flowOf(emptyList())
            else container.engagements.observeContracts(projectId, org)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _openId = MutableStateFlow<String?>(null)
    val openId: StateFlow<String?> = _openId.asStateFlow()

    val open: StateFlow<ContractEntity?> = combine(contracts, _openId) { all, id ->
        all.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val amendments: StateFlow<List<ContractAmendmentEntity>> = _openId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.engagements.observeAmendments(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * What this firm keeps: what it is paid, less what it pays out.
     *
     * Null unless the viewer is the firm in the middle, and null rather than
     * zero — zero is an answer, and this must not be answerable from either
     * side. Computed from the contracts already on screen, all of which this
     * firm is a party to by the time they got here.
     */
    val margin: StateFlow<Double?> = combine(contracts, orgId) { all, org ->
        if (org.isBlank()) return@combine null
        val receivable = all.firstOrNull { it.payeeOrgId == org } ?: return@combine null
        val payables = all.filter { it.payerOrgId == org }
        Commercial.margin(
            receivable = receivable.asAgreement(),
            receivableMoney = receivable.asMoney(),
            payables = payables.map { it.asAgreement() to it.asMoney() },
            viewerOrgId = org,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _refusal = MutableStateFlow<EngagementRepository.Refusal?>(null)
    val refusal: StateFlow<EngagementRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() { _refusal.value = null }

    fun openContract(id: String?) { _openId.value = id }

    fun sign() = viewModelScope.launch {
        val contract = open.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.engagements.sign(contract, orgId.value, actor).onFailure(::report)
    }

    fun propose(newAmount: Double?, reason: String?) = viewModelScope.launch {
        val contract = open.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.engagements
            .proposeAmendment(contract, newAmount, reason, orgId.value, actor)
            .onFailure(::report)
    }

    fun decide(amendment: ContractAmendmentEntity, accept: Boolean) = viewModelScope.launch {
        val contract = open.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.engagements
            .decideAmendment(contract, amendment, accept, orgId.value, actor)
            .onFailure(::report)
    }

    private fun report(failure: Throwable) {
        _refusal.value = (failure as? EngagementRepository.Refused)?.refusal
    }

    companion object {

        /** The figure in force: the original, plus every accepted amendment. */
        fun currentAmount(
            contract: ContractEntity,
            amendments: List<ContractAmendmentEntity>,
        ): Double = Amendment.currentAmount(contract.amount, amendments.acceptedVersions())

        fun history(
            contract: ContractEntity,
            amendments: List<ContractAmendmentEntity>,
        ): List<Double> = Amendment.history(contract.amount, amendments.acceptedVersions())

        fun openAmendment(amendments: List<ContractAmendmentEntity>): ContractAmendmentEntity? =
            amendments.firstOrNull { it.status == Amendment.Status.PROPOSED }

        fun termsOf(contract: ContractEntity): Payments.Terms =
            runCatching { Payments.Terms.valueOf(contract.terms) }
                .getOrDefault(Payments.Terms.SHOTEF_30)

        private fun List<ContractAmendmentEntity>.acceptedVersions(): List<Amendment.Version> =
            filter { it.status == Amendment.Status.ACCEPTED }.map {
                Amendment.Version(
                    version = it.version,
                    previousAmount = it.previousAmount,
                    newAmount = it.newAmount,
                    reason = it.reason,
                    proposedByOrgId = it.proposedByOrgId,
                    acceptedByOrgId = it.decidedByOrgId.orEmpty(),
                    acceptedAt = it.decidedAt ?: it.proposedAt,
                )
            }
    }
}

/**
 * A contract's figures, as the confidentiality rule sees them.
 *
 * Certified and paid are not tracked on the contract row itself — payment
 * applications carry those, per job and per direction. The margin is
 * deliberately taken from the agreed figures rather than the certified ones
 * so that it means "what this job is worth to us if it runs to contract",
 * which is the question a firm asks before it takes the next one.
 */
private fun ContractEntity.asMoney(): Commercial.Money =
    Commercial.Money(contractSum = amount, certifiedToDate = amount, paidToDate = 0.0)
