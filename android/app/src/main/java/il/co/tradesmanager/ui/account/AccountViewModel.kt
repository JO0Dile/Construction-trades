package il.co.tradesmanager.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.repository.AccountRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AccountViewModel(private val container: AppContainer) : ViewModel() {

    /** Set when a sign-in is refused; cleared as soon as they type again. */
    private val _wrongCredentials = MutableStateFlow(false)
    val wrongCredentials: StateFlow<Boolean> = _wrongCredentials.asStateFlow()

    fun clearError() {
        _wrongCredentials.value = false
    }

    fun createPersonal(
        name: String,
        username: String?,
        idNumber: String?,
        passcode: String?,
    ) = viewModelScope.launch {
        container.session.adopt(
            container.accounts.createPersonalAccount(
                name = name,
                passcode = passcode,
                username = username,
                idNumber = idNumber,
            ),
        )
    }

    fun createCompany(
        companyName: String,
        registrationNumber: String?,
        ownerName: String,
        username: String?,
        idNumber: String?,
        passcode: String?,
    ) = viewModelScope.launch {
        container.session.adopt(
            container.accounts.createCompany(
                companyName = companyName,
                registrationNumber = registrationNumber,
                ownerName = ownerName,
                passcode = passcode,
                ownerUsername = username,
                ownerIdNumber = idNumber,
            ),
        )
    }

    /**
     * Signs in with what a manager handed over: a username or an ID number,
     * and a password. Anything that is not a success is one error, on purpose
     * — see [AccountRepository.signInByIdentifier].
     */
    fun signIn(identifier: String, password: String) = viewModelScope.launch {
        val result = container.session.signInByIdentifier(identifier, password)
        _wrongCredentials.value = result !is AccountRepository.SignIn.Success
    }

    fun signOut() = viewModelScope.launch { container.session.signOut() }
}
