package il.co.tradesmanager.ui.complaints

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.evidence.Complaints
import il.co.tradesmanager.data.local.entity.ComplaintEntity
import il.co.tradesmanager.data.repository.ComplaintRepository
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

/** One job's complaints: the ones waiting longest for an answer, first. */
class ComplaintsViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    data class Row(val complaint: ComplaintEntity, val state: Complaints.State)

    private val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionRepository.State.Loading)

    val mayWrite: StateFlow<Boolean> = session
        .map { (it as? SessionRepository.State.SignedIn)?.role?.let { role -> ComplaintRepository.mayWrite(role) } == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Nothing for somebody who may not read the site's record, and the
     * complainant's phone or address only for somebody who may answer: the
     * others need to know a complaint was made, not how to reach the person.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<Row>> = session
        .flatMapLatest { state ->
            val role = (state as? SessionRepository.State.SignedIn)?.role
            when {
                role == null || !ComplaintRepository.mayRead(role) -> flowOf(emptyList<ComplaintEntity>())
                ComplaintRepository.mayWrite(role) -> container.complaints.observeForProject(projectId)
                else -> container.complaints.observeForProject(projectId).map { all -> all.map { it.copy(contact = null) } }
            }
        }
        .map { all ->
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            val rows = all.map { Row(it, Complaints.state(it.receivedAt, it.answeredAt, now, zone)) }
            Complaints.order(rows.map { it.state to it.complaint.receivedAt }).map { rows[it] }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The job's name, for the title of an export. */
    val jobName: StateFlow<String> = container.projects.observeProjects()
        .map { jobs -> jobs.firstOrNull { it.id == projectId }?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _refusal = MutableStateFlow<ComplaintRepository.Refusal?>(null)
    val refusal: StateFlow<ComplaintRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    private fun signedIn(): SessionRepository.State.SignedIn? =
        (session.value as? SessionRepository.State.SignedIn).also {
            if (it == null) _refusal.value = ComplaintRepository.Refusal.NOT_ALLOWED
        }

    private fun report(failure: Throwable) {
        _refusal.value = (failure as? ComplaintRepository.Refused)?.refusal ?: ComplaintRepository.Refusal.UNKNOWN
    }

    fun receive(
        fromWhom: String,
        contact: String,
        channel: Complaints.Channel,
        subject: Complaints.Subject,
        description: String,
        hoursAgo: Int,
    ) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        val now = System.currentTimeMillis()
        container.complaints.receive(
            role = me.role,
            projectId = projectId,
            fromWhom = fromWhom,
            contact = contact,
            channel = channel,
            subject = subject,
            description = description,
            receivedAt = now - hoursAgo * HOUR_MILLIS,
            byName = me.account.displayName,
            now = now,
        ).onFailure { report(it) }
    }

    fun answer(complaintId: String, response: String) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.complaints.answer(me.role, complaintId, response, me.account.displayName).onFailure { report(it) }
    }

    private companion object {
        const val HOUR_MILLIS = 60L * 60L * 1000L
    }
}
