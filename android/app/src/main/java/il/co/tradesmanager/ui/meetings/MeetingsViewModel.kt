package il.co.tradesmanager.ui.meetings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.work.Meetings
import il.co.tradesmanager.data.local.entity.MeetingActionEntity
import il.co.tradesmanager.data.local.entity.MeetingEntity
import il.co.tradesmanager.data.repository.MeetingRepository
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

/** One job's meetings, and every point they raised with the overdue ones first. */
class MeetingsViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    data class Point(
        val action: MeetingActionEntity,
        val state: Meetings.ActionState,
        val dueOn: LocalDate?,
    )

    data class Minutes(
        val meeting: MeetingEntity,
        val kind: Meetings.Kind,
        val heldOn: LocalDate,
        /** The points it raised, in the order they were raised. */
        val points: List<Point>,
    ) {
        val stillOpen: Int get() = points.count { it.state != Meetings.ActionState.DONE }
    }

    data class Register(
        val meetings: List<Minutes> = emptyList(),
        /** Every point on the job, overdue first. */
        val points: List<Point> = emptyList(),
    )

    private val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionRepository.State.Loading)

    val mayWrite: StateFlow<Boolean> = session
        .map { (it as? SessionRepository.State.SignedIn)?.role?.let { role -> MeetingRepository.mayWrite(role) } == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Nothing for somebody who may not read the plan. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val register: StateFlow<Register> = session
        .flatMapLatest { state ->
            val role = (state as? SessionRepository.State.SignedIn)?.role
            if (role == null || !MeetingRepository.mayRead(role)) {
                flowOf(Register())
            } else {
                combine(
                    container.meetings.observeForProject(projectId),
                    container.meetings.observeActionsForProject(projectId),
                ) { meetings, actions -> registerOf(meetings, actions, LocalDate.now()) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Register())

    /** The job's name, for the title of an export. */
    val jobName: StateFlow<String> = container.projects.observeProjects()
        .map { jobs -> jobs.firstOrNull { it.id == projectId }?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _openMeetingId = MutableStateFlow<String?>(null)
    private val _openPointId = MutableStateFlow<String?>(null)

    val openMeeting: StateFlow<Minutes?> = combine(register, _openMeetingId) { r, id -> r.meetings.firstOrNull { it.meeting.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val openPoint: StateFlow<Point?> = combine(register, _openPointId) { r, id -> r.points.firstOrNull { it.action.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectMeeting(id: String?) {
        _openMeetingId.value = id
    }

    fun selectPoint(id: String?) {
        _openPointId.value = id
    }

    private val _refusal = MutableStateFlow<MeetingRepository.Refusal?>(null)
    val refusal: StateFlow<MeetingRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    private fun signedIn(): SessionRepository.State.SignedIn? =
        (session.value as? SessionRepository.State.SignedIn).also {
            if (it == null) _refusal.value = MeetingRepository.Refusal.NOT_ALLOWED
        }

    private fun report(failure: Throwable) {
        _refusal.value = (failure as? MeetingRepository.Refused)?.refusal ?: MeetingRepository.Refusal.UNKNOWN
    }

    /** The new meeting opens, since the next thing to do is write down what it agreed. */
    fun record(kind: Meetings.Kind, heldOn: LocalDate, attendees: String, notes: String) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.meetings.record(me.role, projectId, kind, heldOn, attendees, notes, me.account.displayName)
            .onSuccess { _openMeetingId.value = it.id }
            .onFailure { report(it) }
    }

    fun raise(meetingId: String, text: String, ownerName: String, dueOn: LocalDate?) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.meetings.raise(me.role, meetingId, text, ownerName, dueOn, me.account.displayName).onFailure { report(it) }
    }

    fun close(actionId: String, note: String) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.meetings.close(me.role, actionId, note, me.account.displayName).onFailure { report(it) }
    }

    companion object {
        fun registerOf(meetings: List<MeetingEntity>, actions: List<MeetingActionEntity>, today: LocalDate): Register {
            val points = actions.map { action ->
                val dueOn = action.dueOnDay?.let { LocalDate.ofEpochDay(it) }
                Point(action, Meetings.actionState(dueOn, action.closedAt != null, today), dueOn)
            }
            val byMeeting = points.groupBy { it.action.meetingId }
            val minutes = meetings
                .sortedWith(compareByDescending<MeetingEntity> { it.heldOnDay }.thenByDescending { it.recordedAt })
                .map { meeting ->
                    Minutes(
                        meeting = meeting,
                        kind = Meetings.kindOf(meeting.kind),
                        heldOn = LocalDate.ofEpochDay(meeting.heldOnDay),
                        points = byMeeting[meeting.id].orEmpty().sortedBy { it.action.raisedAt },
                    )
                }
            val ordered = Meetings.actionOrder(
                points.map { Meetings.ActionSort(it.state, it.action.dueOnDay, it.action.closedAt, it.action.reference) },
            ).map { points[it] }
            return Register(minutes, ordered)
        }
    }
}
