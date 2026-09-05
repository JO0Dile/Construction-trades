package il.co.tradesmanager.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.repository.AccountRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel(private val container: AppContainer) : ViewModel() {

    val accounts: StateFlow<List<AccountEntity>> = container.accounts.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Set when a passcode is refused; cleared as soon as they type again. */
    private val _wrongPasscode = MutableStateFlow(false)
    val wrongPasscode: StateFlow<Boolean> = _wrongPasscode.asStateFlow()

    fun clearError() {
        _wrongPasscode.value = false
    }

    fun createPersonal(name: String, passcode: String?) = viewModelScope.launch {
        container.session.adopt(container.accounts.createPersonalAccount(name, passcode))
    }

    fun createCompany(
        companyName: String,
        registrationNumber: String?,
        ownerName: String,
        passcode: String?,
    ) = viewModelScope.launch {
        container.session.adopt(
            container.accounts.createCompany(
                companyName = companyName,
                registrationNumber = registrationNumber,
                ownerName = ownerName,
                passcode = passcode,
            ),
        )
    }

    fun signIn(accountId: String, passcode: String) = viewModelScope.launch {
        _wrongPasscode.value =
            container.session.signIn(accountId, passcode) is AccountRepository.SignIn.WrongPasscode
    }

    fun signOut() = viewModelScope.launch { container.session.signOut() }
}
