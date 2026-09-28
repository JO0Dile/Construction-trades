package il.co.tradesmanager.ui.firepoints

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.safety.FirePoints
import il.co.tradesmanager.data.local.entity.FirePointCheckEntity
import il.co.tradesmanager.data.local.entity.FirePointEntity
import il.co.tradesmanager.data.repository.FirePointRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

/** One job's fire points: a fault first, then a service passed, then a look overdue. */
class FirePointsViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    data class Row(
        val point: FirePointEntity,
        val kind: FirePoints.Kind,
        val serviceDueOn: LocalDate?,
        /** Every look at this point, newest first. */
        val looks: List<FirePointCheckEntity>,
        val state: FirePoints.State,
    ) {
        val latest: FirePointCheckEntity? get() = looks.firstOrNull()
    }

    private val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionRepository.State.Loading)

    val mayWrite: StateFlow<Boolean> = session
        .map { (it as? SessionRepository.State.SignedIn)?.role?.let { role -> FirePointRepository.mayWrite(role) } == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Nothing for somebody who may not read the site's record. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<Row>> = session
        .flatMapLatest { state ->
            val role = (state as? SessionRepository.State.SignedIn)?.role
            if (role == null || !FirePointRepository.mayRead(role)) {
                flowOf(emptyList<Row>())
            } else {
                combine(
                    container.firePoints.observeForProject(projectId),
                    container.firePoints.observeChecksForProject(projectId),
                ) { points, checks -> rowsOf(points, checks) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The job's name, for the title of an export. */
    val jobName: StateFlow<String> = container.projects.observeProjects()
        .map { jobs -> jobs.firstOrNull { it.id == projectId }?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _openId = MutableStateFlow<String?>(null)

    val open: StateFlow<Row?> = combine(rows, _openId) { list, id -> list.firstOrNull { it.point.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun openPoint(id: String?) {
        _openId.value = id
    }

    private val _refusal = MutableStateFlow<FirePointRepository.Refusal?>(null)
    val refusal: StateFlow<FirePointRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    private fun signedIn(): SessionRepository.State.SignedIn? =
        (session.value as? SessionRepository.State.SignedIn).also {
            if (it == null) _refusal.value = FirePointRepository.Refusal.NOT_ALLOWED
        }

    private fun report(failure: Throwable) {
        _refusal.value = (failure as? FirePointRepository.Refused)?.refusal ?: FirePointRepository.Refusal.UNKNOWN
    }

    fun add(kind: FirePoints.Kind, location: String, tagNumber: String, serviceDueOn: LocalDate?) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.firePoints.add(me.role, projectId, kind, location, tagNumber, serviceDueOn, me.account.displayName)
            .onFailure { report(it) }
    }

    fun check(pointId: String, ok: Boolean, note: String) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.firePoints.check(me.role, pointId, ok, note, me.account.displayName).onFailure { report(it) }
    }

    fun serviced(pointId: String, nextDueOn: LocalDate) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.firePoints.serviced(me.role, pointId, nextDueOn, me.account.displayName).onFailure { report(it) }
    }

    fun remove(pointId: String) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.firePoints.remove(me.role, pointId, me.account.displayName).onFailure { report(it) }
    }

    companion object {
        /** Each point with its looks and its state, in the order the register shows them. */
        fun rowsOf(
            points: List<FirePointEntity>,
            checks: List<FirePointCheckEntity>,
            now: Long = System.currentTimeMillis(),
            zone: ZoneId = ZoneId.systemDefault(),
        ): List<Row> {
            val looksByPoint = checks.groupBy { it.firePointId }.mapValues { (_, looks) -> looks.sortedByDescending { it.checkedAt } }
            val rows = points.map { point ->
                val looks = looksByPoint[point.id].orEmpty()
                val serviceDueOn = point.serviceDueOnDay?.let { LocalDate.ofEpochDay(it) }
                Row(
                    point = point,
                    kind = FirePoints.kindOf(point.kind),
                    serviceDueOn = serviceDueOn,
                    looks = looks,
                    state = FirePoints.state(
                        serviceDueOn = serviceDueOn,
                        lastCheckAt = looks.firstOrNull()?.checkedAt,
                        lastCheckOk = looks.firstOrNull()?.ok,
                        removed = point.removedAt != null,
                        now = now,
                        zone = zone,
                    ),
                )
            }
            return FirePoints.order(rows.map { it.state to it.point.reference }).map { rows[it] }
        }

        /** The date picker's millis are midnight UTC; read in UTC so the day does not slip east of Greenwich. */
        fun dayOf(pickerMillis: Long): LocalDate = Instant.ofEpochMilli(pickerMillis).atZone(ZoneId.of("UTC")).toLocalDate()
    }
}
