package il.co.tradesmanager.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.data.catalog.ProjectKind
import il.co.tradesmanager.data.local.entity.CertificationEntity
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.repository.AccountRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PeopleViewModel(private val container: AppContainer) : ViewModel() {

    val members: StateFlow<List<AccountEntity>> = container.accounts.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    /** Account id -> that person's tickets. One query for the whole list. */
    val certifications: StateFlow<Map<String, List<CertificationEntity>>> =
        container.certifications.observeByAccount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _kinds = MutableStateFlow<List<ProjectKind>>(emptyList())
    val kinds: StateFlow<List<ProjectKind>> = _kinds.asStateFlow()

    init {
        viewModelScope.launch { _kinds.value = container.certifications.suggestedKinds() }
    }

    fun addCertification(accountId: String, title: String, reference: String?, expiresOn: Long?) =
        viewModelScope.launch {
            val actor = container.settings.settings.first().actorName
            container.certifications.add(accountId, title, reference, expiresOn, actor)
        }

    fun removeCertification(certification: CertificationEntity) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.certifications.remove(certification, actor)
    }

    /** Set when the app refused a change, so the screen can say why. */
    private val _refusal = MutableStateFlow<AccountRepository.Refusal?>(null)
    val refusal: StateFlow<AccountRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    fun addMember(
        name: String,
        username: String?,
        idNumber: String?,
        role: Role,
        passcode: String?,
    ) = viewModelScope.launch {
        val actor = container.session.current() ?: return@launch
        container.accounts
            .addMember(actor, name, role, passcode, username, idNumber)
            .recordRefusal()
    }

    fun setRole(target: AccountEntity, role: Role) = viewModelScope.launch {
        val actor = container.session.current() ?: return@launch
        container.accounts.setRole(actor, target, role).recordRefusal()
    }

    fun remove(target: AccountEntity) = viewModelScope.launch {
        val actor = container.session.current() ?: return@launch
        container.accounts.remove(actor, target).recordRefusal()
    }

    private fun Result<*>.recordRefusal() {
        _refusal.value = (exceptionOrNull() as? AccountRepository.RefusedException)?.refusal
    }
}
