package il.co.tradesmanager.ui.lifting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.people.Expiry
import il.co.tradesmanager.core.safety.Lifting
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.local.entity.CertificationEntity
import il.co.tradesmanager.data.local.entity.LiftCrewEntity
import il.co.tradesmanager.data.local.entity.LiftPlanEntity
import il.co.tradesmanager.data.repository.LiftingRepository
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lift plans on a job, and whichever one is open. */
class LiftingViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    /** Somebody who could be put on a lift, and the tickets they hold. */
    data class Candidate(
        val account: AccountEntity,
        val certifications: List<CertificationEntity>,
    )

    private val _openId = MutableStateFlow<String?>(null)
    val openId: StateFlow<String?> = _openId.asStateFlow()

    val plans: StateFlow<List<LiftPlanEntity>> = container.lifting.observePlans(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val open: StateFlow<LiftPlanEntity?> = _openId
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.lifting.observePlan(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val crew: StateFlow<List<LiftCrewEntity>> = _openId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.lifting.observeCrew(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The people who could be put on a lift: the crew of the company somebody
     * is signed in to, each with their tickets.
     *
     * Scoped the same way the People screen is. A device that has been used by
     * two firms must not offer one firm's slingers to the other.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val candidates: StateFlow<List<Candidate>> = container.session.state
        .flatMapLatest { state ->
            val signedIn = state as? SessionRepository.State.SignedIn
            if (signedIn == null) {
                flowOf(emptyList())
            } else {
                combine(
                    container.memberships.observeForCompany(signedIn.active?.companyId),
                    container.accounts.observeAccounts(),
                    container.certifications.observeByAccount(),
                ) { rows, accounts, tickets ->
                    rows.mapNotNull { row ->
                        accounts.firstOrNull { it.id == row.accountId }
                            ?.let { Candidate(it, tickets[it.id].orEmpty()) }
                    }.sortedBy { it.account.displayName }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    fun openPlan(id: String?) { _openId.value = id }

    fun createPlan(description: String) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        val plan = container.lifting.createPlan(projectId, description, actor)
        _openId.value = plan.id
    }

    fun save(plan: LiftPlanEntity) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.lifting.updatePlan(plan, actor)
    }

    fun approve() = viewModelScope.launch {
        val plan = open.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.lifting.approve(plan, actor)
    }

    fun complete() = viewModelScope.launch {
        val plan = open.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.lifting.complete(plan, actor)
    }

    fun setCrew(
        role: LiftingRepository.Role,
        accountId: String?,
        name: String,
        certification: CertificationEntity?,
    ) = viewModelScope.launch {
        val plan = open.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.lifting.setCrew(
            plan = plan,
            role = role,
            accountId = accountId,
            name = name,
            certificationId = certification?.id,
            certificateReference = certification?.reference,
            certificateExpiresOn = certification?.expiresOn,
            actorName = actor,
        )
    }

    fun clearCrew(role: LiftingRepository.Role) = viewModelScope.launch {
        val plan = open.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.lifting.clearCrew(plan, role, actor)
    }

    companion object {

        /**
         * Turns the crew rows into what the gate reads.
         *
         * A role nobody is in is null, which the gate reports as a missing
         * role. A row with no expiry date is [Expiry.State.NO_EXPIRY] — a
         * licence that does not run out is still a licence.
         */
        fun crewStates(crew: List<LiftCrewEntity>, now: Long): Lifting.Crew {
            fun stateFor(role: LiftingRepository.Role): Expiry.State? =
                crew.firstOrNull { it.role == role.name }
                    ?.let { Expiry.state(it.certificateExpiresOn, now) }
            return Lifting.Crew(
                operator = stateFor(LiftingRepository.Role.OPERATOR),
                slinger = stateFor(LiftingRepository.Role.SLINGER),
                banksman = stateFor(LiftingRepository.Role.BANKSMAN),
            )
        }

        /** Everything wrong with a plan as it currently stands. */
        fun blockersFor(
            plan: LiftPlanEntity,
            crew: List<LiftCrewEntity>,
            now: Long,
        ): Set<Lifting.Blocker> = Lifting.blockers(
            loadWeightKg = plan.loadWeightKg,
            riggingWeightKg = plan.riggingWeightKg,
            capacityAtRadiusKg = plan.capacityAtRadiusKg,
            crew = crewStates(crew, now),
            applianceCertificateExpiresOn = plan.applianceCertificateExpiresOn,
            applianceCertificateRequired = plan.applianceCertificateRequired,
            windSpeedKmh = plan.windSpeedKmh,
            windLimitKmh = plan.windLimitKmh,
            approvedAt = plan.approvedAt,
            now = now,
        )
    }
}
