package il.co.tradesmanager.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.TaskBlockEntity
import il.co.tradesmanager.data.local.entity.TimeEntryEntity
import il.co.tradesmanager.core.time.TimeOfDay
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduleViewModel(private val container: AppContainer) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val blocks: StateFlow<List<TaskBlockEntity>> = _date
        .flatMapLatest { container.schedule.observeDay(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val openTimeEntry: StateFlow<TimeEntryEntity?> = container.schedule.observeOpenTimeEntry()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Who the work can be handed to: the people in the firm you are signed in
     * to, and nobody else.
     *
     * A personal account has no company, so the list is empty and the screen
     * does not offer to hand anything to anybody -- which is right, since
     * there is nobody to hand it to.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val crew: StateFlow<List<AccountEntity>> = container.session.state
        .flatMapLatest { state ->
            val signedIn = state as? SessionRepository.State.SignedIn
            if (signedIn?.active?.companyId == null) {
                kotlinx.coroutines.flow.flowOf(emptyList())
            } else {
                combine(
                    container.memberships.observeForCompany(signedIn.active?.companyId),
                    container.accounts.observeAccounts(),
                ) { rows, accounts ->
                    rows.mapNotNull { row -> accounts.firstOrNull { it.id == row.accountId } }
                        .sortedBy { it.displayName }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun shiftDay(days: Long) { _date.value = _date.value.plusDays(days) }

    /**
     * Hands a block to somebody, or takes it back when [accountId] is null.
     *
     * The column has existed since the schedule was built and nothing has ever
     * written to it, so every block on every phone has been nobody's. A day
     * plan where each line is nobody's is a list of what ought to happen
     * rather than an answer to who is doing it, which is the question asked
     * at seven in the morning.
     */
    fun setAssignee(blockId: String, accountId: String?) = viewModelScope.launch {
        val block = blocks.value.firstOrNull { it.id == blockId } ?: return@launch
        container.schedule.save(
            block.copy(assigneeId = accountId),
            actorName = container.settings.settings.first().actorName,
        )
    }

    fun addBlock(title: String, startMinute: Int, endMinute: Int) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        val actor = container.settings.settings.first().actorName
        val (start, end) = TimeOfDay.sanitiseRange(startMinute, endMinute)
        container.schedule.save(
            TaskBlockEntity(
                id = UUID.randomUUID().toString(),
                epochDay = _date.value.toEpochDay(),
                startMinute = start,
                endMinute = end,
                title = title,
                createdAt = 0,
                updatedAt = 0,
            ),
            actorName = actor,
        )
    }

    fun setDone(id: String, done: Boolean) = viewModelScope.launch {
        container.schedule.setDone(id, done, container.settings.settings.first().actorName)
    }

    fun delete(id: String) = viewModelScope.launch {
        container.schedule.delete(id, container.settings.settings.first().actorName)
    }

    fun copyToTomorrow() = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.schedule.copyDay(blocks.value, _date.value.plusDays(1), actor)
    }

    /**
     * Every job, including the parts of one, because a man works on the
     * twelfth floor rather than on the tower.
     */
    val jobs: StateFlow<List<ProjectEntity>> = container.projects.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Starts or ends a shift, against the job it was worked on.
     *
     * [projectId] is the whole of this. Every check-in the app has ever
     * recorded passed null, and the timesheet reads
     * `WHERE projectId = :projectId` -- null matches nothing, so every job's
     * timesheet has been empty and the labour figures built on top of it have
     * been arithmetic over an empty list. Hours were being collected and
     * could not reach the money they were the largest part of.
     *
     * The two coordinate parameters that used to be here are gone. The screen
     * passed null for both, always; the comment claimed a refused permission
     * cost the stamp, when there was no stamp to cost.
     */
    fun toggleCheckIn(projectId: String?) = viewModelScope.launch {
        val open = openTimeEntry.value
        if (open != null) {
            container.schedule.checkOut(open)
            return@launch
        }
        // Who is signed in, not what somebody typed into settings. The name is
        // still recorded beside the ids so the register reads years later, but
        // it is no longer the only thing identifying the shift.
        val signedIn = container.session.state.first() as? SessionRepository.State.SignedIn
        val actor = container.settings.settings.first().actorName
        container.schedule.checkIn(
            workerName = signedIn?.account?.displayName?.ifBlank { null }
                ?: actor.ifBlank { "worker" },
            projectId = projectId,
            latitude = null,
            longitude = null,
            workerAccountId = signedIn?.account?.id,
            workerMembershipId = signedIn?.active?.id,
        )
    }
}
