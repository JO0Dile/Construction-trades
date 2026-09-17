package il.co.tradesmanager.ui.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Admission
import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.repository.MembershipRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The person on the gate, admitting one worker at a time.
 *
 * The whole of it is: type the number off the card, see the face that comes
 * back, watch them sign, admit. Deliberately not a list, a filter or a report
 * — it is used standing up, one-handed, with somebody waiting.
 */
class GateViewModel(
    private val container: AppContainer,
) : ViewModel() {

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    private val signedIn: SessionRepository.State.SignedIn?
        get() = session.value as? SessionRepository.State.SignedIn

    private val companyId: String? get() = signedIn?.active?.companyId

    private val role: Role get() = signedIn?.role ?: Role.WORKER

    /* ------------------------------------------------------- who is at the gate */

    private val _found = MutableStateFlow<AccountEntity?>(null)
    val found: StateFlow<AccountEntity?> = _found.asStateFlow()

    /** Their photograph, so the number on the card can be checked against a face. */
    private val _foundFace = MutableStateFlow<String?>(null)
    val foundFace: StateFlow<String?> = _foundFace.asStateFlow()

    /** True once a search has run and come back with nobody. */
    private val _searchedInVain = MutableStateFlow(false)
    val searchedInVain: StateFlow<Boolean> = _searchedInVain.asStateFlow()

    /**
     * Whether the person found is already on these books.
     *
     * Worked out as soon as they are found rather than at the moment of
     * admitting, so somebody who is already in is told so before they are
     * asked to sign for nothing.
     */
    private val _alreadyIn = MutableStateFlow(false)
    val alreadyIn: StateFlow<Boolean> = _alreadyIn.asStateFlow()

    fun search(idNumber: String) = viewModelScope.launch {
        val match = container.accounts.findByIdNumber(idNumber)
        _found.value = match
        _foundFace.value = match?.let { container.photos.faceOf(it.id) }
        _searchedInVain.value = match == null && idNumber.isNotBlank()
        _alreadyIn.value = match != null && container.memberships.isCurrentMember(match.id, companyId)
        _signature.value = ""
        _admitted.value = null
    }

    fun clear() {
        _found.value = null
        _foundFace.value = null
        _searchedInVain.value = false
        _alreadyIn.value = false
        _signature.value = ""
    }

    /* ---------------------------------------------------------------- signing */

    private val _signature = MutableStateFlow("")
    val signature: StateFlow<String> = _signature.asStateFlow()

    fun signed(encoded: String) {
        _signature.value = encoded
    }

    /* --------------------------------------------------------------- admitting */

    /** The name of the last person admitted, so the screen can say it worked. */
    private val _admitted = MutableStateFlow<String?>(null)
    val admitted: StateFlow<String?> = _admitted.asStateFlow()

    private val _refusal = MutableStateFlow<Admission.Blocker?>(null)
    val refusal: StateFlow<Admission.Blocker?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    fun admit() = viewModelScope.launch {
        val person = _found.value ?: return@launch
        val me = signedIn?.account ?: return@launch
        container.memberships.admit(
            gateKeeperRole = role,
            gateKeeperName = me.displayName,
            gateKeeperAccountId = me.id,
            companyId = companyId,
            account = person,
            signature = _signature.value,
        ).onSuccess {
            _admitted.value = person.displayName
            _found.value = null
            _foundFace.value = null
            _alreadyIn.value = false
            _signature.value = ""
        }.onFailure { failure ->
            _refusal.value = (failure as? MembershipRepository.NotAdmitted)?.blocker
        }
    }
}
