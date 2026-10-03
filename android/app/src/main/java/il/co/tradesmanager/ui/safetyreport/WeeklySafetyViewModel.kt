package il.co.tradesmanager.ui.safetyreport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.safety.WeeklySafety
import il.co.tradesmanager.core.work.Attention
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import il.co.tradesmanager.ui.projects.observeJobAttention
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

/** One job's week of safety, for the week picked. */
class WeeklySafetyViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    private val _weeksBack = MutableStateFlow(0L)
    val weeksBack: StateFlow<Long> = _weeksBack.asStateFlow()

    fun pickWeek(weeksBack: Long) {
        _weeksBack.value = weeksBack
    }

    val weekStart: StateFlow<LocalDate> = _weeksBack
        .map { WeeklySafety.weekStarting(LocalDate.now()).minusWeeks(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeeklySafety.weekStarting(LocalDate.now()))

    /** Null while counting, and for a role that may not read the site's record. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val report: StateFlow<WeeklySafety.Report?> = combine(container.session.state, weekStart) { state, start -> state to start }
        .flatMapLatest { (state, start) ->
            val role = (state as? SessionRepository.State.SignedIn)?.role
            flow { emit(role?.let { container.safetyReports.week(it, projectId, start) }) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** What is open on the job now, of the lines that are about safety. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val openNow: StateFlow<List<Attention.Line>> = container.session.state
        .flatMapLatest { state -> observeJobAttention(container, projectId, (state as? SessionRepository.State.SignedIn)?.role) }
        .map { lines -> lines.filter { it.item in SAFETY_ITEMS } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The job's name, for the title of an export. */
    val jobName: StateFlow<String> = container.projects.observeProjects()
        .map { jobs -> jobs.firstOrNull { it.id == projectId }?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    companion object {
        /** The lines a safety report carries; the plan's delays and materials are for another page. */
        val SAFETY_ITEMS: Set<Attention.Item> = setOf(
            Attention.Item.EMERGENCY_INFO_MISSING,
            Attention.Item.INVESTIGATIONS_OUTSTANDING,
            Attention.Item.RISKS_EXTREME,
            Attention.Item.INCIDENT_ACTIONS_OVERDUE,
            Attention.Item.SUBSTANCES_WITHOUT_SHEET,
            Attention.Item.FIRE_POINTS,
            Attention.Item.INSPECTIONS_FAILED,
            Attention.Item.INSPECTIONS_OVERDUE,
            Attention.Item.COMPLAINTS_WAITING,
            Attention.Item.RISK_REVIEWS_OVERDUE,
        )
    }
}
