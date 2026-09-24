package il.co.tradesmanager.ui.queries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.work.Queries
import il.co.tradesmanager.data.local.entity.DesignQueryEntity
import il.co.tradesmanager.data.repository.DesignQueryRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One job's questions to its designers: what is holding work up, first. */
class QueriesViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    data class Row(val query: DesignQueryEntity, val state: Queries.State)

    private val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionRepository.State.Loading)

    val mayWrite: StateFlow<Boolean> = session
        .map { (it as? SessionRepository.State.SignedIn)?.role?.let { role -> DesignQueryRepository.mayWrite(role) } == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Overdue first, then open, then answered -- and nothing for somebody who may not read the plan. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<Row>> = session
        .flatMapLatest { state ->
            val role = (state as? SessionRepository.State.SignedIn)?.role
            if (role == null || !role.canRead(Lens.PLAN)) flowOf(emptyList()) else container.designQueries.observeForProject(projectId)
        }
        .map { all ->
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val rows = all.map { Row(it, Queries.state(it.neededBy, it.answeredAt, now, zone)) }
            Queries.order(rows.map { it.state to it.query.askedAt }).map { rows[it] }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refusal = MutableStateFlow<DesignQueryRepository.Refusal?>(null)
    val refusal: StateFlow<DesignQueryRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    fun raise(question: String, askedOf: String, drawingNumber: String, neededBy: Long?) = viewModelScope.launch {
        val me = session.value as? SessionRepository.State.SignedIn
        if (me == null) {
            _refusal.value = DesignQueryRepository.Refusal.NOT_ALLOWED
            return@launch
        }
        container.designQueries.raise(
            role = me.role,
            projectId = projectId,
            question = question,
            askedOf = askedOf,
            drawingNumber = drawingNumber,
            neededBy = neededBy,
            byName = me.account.displayName,
        ).onFailure { failure ->
            _refusal.value = (failure as? DesignQueryRepository.Refused)?.refusal ?: DesignQueryRepository.Refusal.UNKNOWN
        }
    }

    fun answer(queryId: String, answer: String) = viewModelScope.launch {
        val me = session.value as? SessionRepository.State.SignedIn
        if (me == null) {
            _refusal.value = DesignQueryRepository.Refusal.NOT_ALLOWED
            return@launch
        }
        container.designQueries.answer(me.role, queryId, answer, me.account.displayName)
            .onFailure { failure ->
                _refusal.value = (failure as? DesignQueryRepository.Refused)?.refusal ?: DesignQueryRepository.Refusal.UNKNOWN
            }
    }
}
