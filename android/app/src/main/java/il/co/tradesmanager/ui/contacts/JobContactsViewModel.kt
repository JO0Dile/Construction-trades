package il.co.tradesmanager.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.work.Contacts
import il.co.tradesmanager.data.local.entity.JobContactEntity
import il.co.tradesmanager.data.repository.JobContactRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
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

/** One job's contacts, by what they are on the job; those who have left, last. */
class JobContactsViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    private val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionRepository.State.Loading)

    val mayWrite: StateFlow<Boolean> = session
        .map { (it as? SessionRepository.State.SignedIn)?.role?.let { role -> JobContactRepository.mayWrite(role) } == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Nothing for somebody who reads neither the plan nor the site's record. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val contacts: StateFlow<List<JobContactEntity>> = session
        .flatMapLatest { state ->
            val role = (state as? SessionRepository.State.SignedIn)?.role
            if (role == null || !JobContactRepository.mayRead(role)) {
                flowOf(emptyList<JobContactEntity>())
            } else {
                container.jobContacts.observeForProject(projectId)
            }
        }
        .map { all ->
            Contacts.order(all.map { Triple(Contacts.kindOf(it.kind), it.name, it.removedAt != null) }).map { all[it] }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The job's name, for the title of an export. */
    val jobName: StateFlow<String> = container.projects.observeProjects()
        .map { jobs -> jobs.firstOrNull { it.id == projectId }?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _refusal = MutableStateFlow<JobContactRepository.Refusal?>(null)
    val refusal: StateFlow<JobContactRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    private fun signedIn(): SessionRepository.State.SignedIn? =
        (session.value as? SessionRepository.State.SignedIn).also {
            if (it == null) _refusal.value = JobContactRepository.Refusal.NOT_ALLOWED
        }

    private fun report(failure: Throwable) {
        _refusal.value = (failure as? JobContactRepository.Refused)?.refusal ?: JobContactRepository.Refusal.UNKNOWN
    }

    fun add(entry: JobContactRepository.Entry) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.jobContacts.add(me.role, projectId, entry, me.account.displayName).onFailure { report(it) }
    }

    fun correct(contactId: String, entry: JobContactRepository.Entry) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.jobContacts.correct(me.role, contactId, entry, me.account.displayName).onFailure { report(it) }
    }

    fun remove(contactId: String) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.jobContacts.remove(me.role, contactId, me.account.displayName).onFailure { report(it) }
    }
}
