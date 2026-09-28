package il.co.tradesmanager.ui.risks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.safety.Risks
import il.co.tradesmanager.data.local.entity.RiskAssessmentEntity
import il.co.tradesmanager.data.repository.RiskRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One job's risk assessment: what is still extreme, first. */
class RisksViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    data class Row(
        val risk: RiskAssessmentEntity,
        val state: Risks.State,
        val initial: Int,
        val residual: Int,
    )

    private val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionRepository.State.Loading)

    val mayWrite: StateFlow<Boolean> = session
        .map { (it as? SessionRepository.State.SignedIn)?.role?.let { role -> RiskRepository.mayWrite(role) } == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Extreme first -- and nothing for somebody who may not read the site's record. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<Row>> = session
        .flatMapLatest { state ->
            val role = (state as? SessionRepository.State.SignedIn)?.role
            if (role == null || !RiskRepository.mayRead(role)) flowOf(emptyList()) else container.risks.observeForProject(projectId)
        }
        .map { all ->
            val today = LocalDate.now()
            val rows = all.map { risk ->
                val residual = RiskRepository.residualOf(risk)
                Row(
                    risk = risk,
                    state = Risks.state(risk.closed, residual, risk.reviewOnDay?.let(LocalDate::ofEpochDay), today),
                    initial = RiskRepository.initialOf(risk),
                    residual = residual,
                )
            }
            Risks.order(rows.map { it.state to it.residual }).map { rows[it] }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The job's name, for the title of an export. */
    val jobName: StateFlow<String> = container.projects.observeProjects()
        .map { jobs -> jobs.firstOrNull { it.id == projectId }?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _openId = MutableStateFlow<String?>(null)

    val open: StateFlow<Row?> = combine(rows, _openId) { list, id -> list.firstOrNull { it.risk.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun openRisk(id: String?) {
        _openId.value = id
    }

    private val _refusal = MutableStateFlow<RiskRepository.Refusal?>(null)
    val refusal: StateFlow<RiskRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    private fun signedIn(): SessionRepository.State.SignedIn? =
        (session.value as? SessionRepository.State.SignedIn).also {
            if (it == null) _refusal.value = RiskRepository.Refusal.NOT_ALLOWED
        }

    private fun report(failure: Throwable) {
        _refusal.value = (failure as? RiskRepository.Refused)?.refusal ?: RiskRepository.Refusal.UNKNOWN
    }

    fun assess(assessment: RiskRepository.Assessment) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.risks.assess(me.role, projectId, assessment, me.account.displayName)
            .onSuccess { _openId.value = it.id }
            .onFailure { report(it) }
    }

    fun review(riskId: String, assessment: RiskRepository.Assessment) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.risks.review(me.role, riskId, assessment, me.account.displayName).onFailure { report(it) }
    }

    fun close(riskId: String) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.risks.close(me.role, riskId, me.account.displayName).onFailure { report(it) }
    }
}
