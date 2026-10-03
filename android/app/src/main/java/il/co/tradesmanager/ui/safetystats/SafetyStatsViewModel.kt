package il.co.tradesmanager.ui.safetystats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.safety.SafetyStats
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.repository.SafetyStatsRepository
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The company's safety statistics over the period picked, job by job. */
class SafetyStatsViewModel(private val container: AppContainer) : ViewModel() {

    private val _period = MutableStateFlow(SafetyStats.Period.THIS_YEAR)
    val period: StateFlow<SafetyStats.Period> = _period.asStateFlow()

    fun pickPeriod(period: SafetyStats.Period) {
        _period.value = period
    }

    val span: StateFlow<SafetyStats.Span> = _period
        .map { SafetyStats.span(it, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SafetyStats.span(SafetyStats.Period.THIS_YEAR, LocalDate.now()))

    /** The active company's jobs, parts included: what the figures are counted over, and the names on the rows. */
    val jobs: StateFlow<List<ProjectEntity>> = container.projects.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether the signed-in role reads the site's record at all; the page says so rather than counting forever. */
    val allowed: StateFlow<Boolean> = container.session.state
        .map { state -> (state as? SessionRepository.State.SignedIn)?.role?.let { SafetyStatsRepository.mayRead(it) } == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Null while counting, and for a role that may not read the site's record. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val sheet: StateFlow<SafetyStats.Sheet?> = combine(container.session.state, span, jobs) { state, span, jobs ->
        Triple(state as? SessionRepository.State.SignedIn, span, jobs)
    }
        .flatMapLatest { (signedIn, span, jobs) ->
            flow {
                emit(null)
                emit(
                    signedIn?.let {
                        container.safetyStats.sheet(
                            role = it.role,
                            companyId = it.active?.companyId,
                            jobs = jobs.map { job -> SafetyStats.Job(job.id, job.parentProjectId) },
                            span = span,
                        )
                    },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
