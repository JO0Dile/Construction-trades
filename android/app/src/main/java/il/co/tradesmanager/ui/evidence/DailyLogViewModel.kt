package il.co.tradesmanager.ui.evidence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.evidence.DailyLog
import il.co.tradesmanager.data.local.entity.DailyLogEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One day's log.
 *
 * The summary is recounted whenever the log changes rather than stored on it,
 * so a delivery booked in at four o'clock turns up in a log opened at three,
 * and correcting a mistyped entry corrects the log with it.
 */
class DailyLogViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val _logId = MutableStateFlow<String?>(null)

    private val _summary = MutableStateFlow(DailyLog.Summary())
    val summary: StateFlow<DailyLog.Summary> = _summary.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val log: StateFlow<DailyLogEntity?> = _logId
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.dailyLogs.observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val history: StateFlow<List<DailyLogEntity>> =
        container.dailyLogs.observeForProject(projectId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    init {
        open(DailyLog.today(zone))
    }

    /** Opens a day, creating its log if nobody has yet. */
    fun open(logDate: Long) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        _logId.value = container.dailyLogs.openFor(projectId, logDate, actor).id
        _summary.value = container.dailyLogs.summarise(projectId, logDate, zone)
    }

    fun save(weather: String?, workforceCount: Int?, notes: String?) = viewModelScope.launch {
        val id = _logId.value ?: return@launch
        container.dailyLogs.update(id, weather, workforceCount, notes)
    }

    fun sign(signature: String) = viewModelScope.launch {
        val id = _logId.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.dailyLogs.sign(id, actor, signature)
    }
}
