package il.co.tradesmanager.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.repository.AccountRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** Set when the app refused a change, so the screen can say why. */
    private val _refusal = MutableStateFlow<AccountRepository.Refusal?>(null)
    val refusal: StateFlow<AccountRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    fun addMember(name: String, role: Role, passcode: String?) = viewModelScope.launch {
        val actor = container.session.current() ?: return@launch
        container.accounts.addMember(actor, name, role, passcode).recordRefusal()
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
