package il.co.tradesmanager.ui.delays

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.work.Delays
import il.co.tradesmanager.data.local.entity.DelayEventEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.repository.DelayRepository
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import java.time.LocalDate
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

/** One job's delay events: what is still holding the work, first. */
class DelaysViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    data class Row(
        val event: DelayEventEntity,
        val cause: Delays.Cause,
        val startedOn: LocalDate,
        val endedOn: LocalDate?,
        val days: Long,
    ) {
        val ongoing: Boolean get() = endedOn == null
        val noticeGiven: Boolean get() = event.notifiedOnDay != null
    }

    /** The job's total, the way somebody preparing a claim first asks for it. */
    data class Tally(
        val events: Int,
        val daysByCause: Map<Delays.Cause, Long>,
        val withoutNotice: Int,
    )

    private val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionRepository.State.Loading)

    val mayWrite: StateFlow<Boolean> = session
        .map { (it as? SessionRepository.State.SignedIn)?.role?.let { role -> DelayRepository.mayWrite(role) } == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Still going first, then the most recent -- and nothing for somebody who may not read the plan. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<Row>> = session
        .flatMapLatest { state ->
            val role = (state as? SessionRepository.State.SignedIn)?.role
            if (role == null || !DelayRepository.mayRead(role)) flowOf(emptyList()) else container.delays.observeForProject(projectId)
        }
        .map { all ->
            val today = LocalDate.now()
            val rows = all.map { event ->
                val started = LocalDate.ofEpochDay(event.startedOnDay)
                val ended = event.endedOnDay?.let(LocalDate::ofEpochDay)
                Row(event, Delays.causeOf(event.cause), started, ended, Delays.days(started, ended, today))
            }
            Delays.order(rows.map { it.ongoing to it.startedOn }).map { rows[it] }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tally: StateFlow<Tally> = rows
        .map { list ->
            Tally(
                events = list.size,
                daysByCause = Delays.daysByCause(list.map { Triple(it.cause, it.startedOn, it.endedOn) }, LocalDate.now()),
                withoutNotice = list.count { !it.noticeGiven },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Tally(0, emptyMap(), 0))

    /** The job's name, for the title of an export. */
    val jobName: StateFlow<String> = container.projects.observeProjects()
        .map { jobs -> jobs.firstOrNull { it.id == projectId }?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _openId = MutableStateFlow<String?>(null)

    val open: StateFlow<Row?> = combine(rows, _openId) { list, id -> list.firstOrNull { it.event.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val openPhotos: StateFlow<List<PhotoEntity>> = open
        .map { it?.event?.id }
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.photos.observeFor(PhotoRepository.Owner.DELAY, id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openEvent(id: String?) {
        _openId.value = id
    }

    private val _refusal = MutableStateFlow<DelayRepository.Refusal?>(null)
    val refusal: StateFlow<DelayRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    private fun signedIn(): SessionRepository.State.SignedIn? =
        (session.value as? SessionRepository.State.SignedIn).also {
            if (it == null) _refusal.value = DelayRepository.Refusal.NOT_ALLOWED
        }

    private fun report(failure: Throwable) {
        _refusal.value = (failure as? DelayRepository.Refused)?.refusal ?: DelayRepository.Refusal.UNKNOWN
    }

    fun record(cause: Delays.Cause, description: String, affectedWork: String, startedDaysAgo: Int, related: String) =
        viewModelScope.launch {
            val me = signedIn() ?: return@launch
            container.delays.record(
                role = me.role,
                projectId = projectId,
                cause = cause,
                description = description,
                affectedWork = affectedWork,
                startedOn = LocalDate.now().minusDays(startedDaysAgo.toLong()),
                relatedReference = related,
                byName = me.account.displayName,
            ).onFailure { report(it) }
        }

    fun end(eventId: String, endedDaysAgo: Int) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.delays.end(me.role, eventId, LocalDate.now().minusDays(endedDaysAgo.toLong()), me.account.displayName)
            .onFailure { report(it) }
    }

    fun notice(eventId: String, notifiedTo: String, noticeDaysAgo: Int) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.delays.notice(
            me.role, eventId, notifiedTo, LocalDate.now().minusDays(noticeDaysAgo.toLong()), me.account.displayName,
        ).onFailure { report(it) }
    }

    private suspend fun actor(): String = container.settings.settings.first().actorName

    fun newPhotoTarget(): Pair<String, Uri> = container.photos.newCameraTarget()

    fun photoCaptured(photoId: String) = viewModelScope.launch {
        val id = _openId.value ?: return@launch
        container.photos.recordCameraPhoto(
            id = photoId,
            ownerType = PhotoRepository.Owner.DELAY,
            ownerId = id,
            actorName = actor(),
        )
    }

    fun photoPicked(uri: Uri) = viewModelScope.launch {
        val id = _openId.value ?: return@launch
        container.photos.importPhoto(
            source = uri,
            ownerType = PhotoRepository.Owner.DELAY,
            ownerId = id,
            actorName = actor(),
        )
    }

    fun deletePhoto(photo: PhotoEntity) = viewModelScope.launch {
        container.photos.delete(photo, actor())
    }
}
