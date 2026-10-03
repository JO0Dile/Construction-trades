package il.co.tradesmanager.ui.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.safety.Ppe
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.local.entity.InventoryItemEntity
import il.co.tradesmanager.data.local.entity.PpeIssueEntity
import il.co.tradesmanager.data.repository.PpeRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The protective equipment register: who holds what, and handing more out. */
class PpeViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    private val signedIn: SessionRepository.State.SignedIn?
        get() = session.value as? SessionRepository.State.SignedIn

    /** Whether this account may see the register at all. The screen says why not. */
    val mayRead: StateFlow<Boolean> = session
        .map { state ->
            (state as? SessionRepository.State.SignedIn)?.role?.let { PpeRepository.mayRead(it) } == true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Whether this account may hand equipment out. The repository checks again. */
    val mayIssue: StateFlow<Boolean> = session
        .map { state ->
            (state as? SessionRepository.State.SignedIn)?.role?.let { PpeRepository.mayIssue(it) } == true
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * This firm's issues, newest first -- and nothing at all for somebody who
     * may not read them, rather than a list the screen then hides.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val issues: StateFlow<List<PpeIssueEntity>> = session
        .flatMapLatest { state ->
            val me = state as? SessionRepository.State.SignedIn
            if (me == null || !PpeRepository.mayRead(me.role)) {
                flowOf(emptyList())
            } else {
                container.ppe.observeForCompany(me.active?.companyId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Everybody holding something, the worst first. See Ppe.holders. */
    val holders: StateFlow<List<Ppe.Holder>> = issues
        .map { rows -> Ppe.holders(rows.map { PpeRepository.asIssue(it) }, System.currentTimeMillis()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The rows behind [holders], by id, for what the rules do not carry. */
    val rows: StateFlow<Map<String, PpeIssueEntity>> = issues
        .map { all -> all.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** What has come back, most recent first. The last of it: this is a reminder, not the archive. */
    val handedBack: StateFlow<List<PpeIssueEntity>> = issues
        .map { all ->
            all.filter { it.handedBackAt != null }
                .sortedByDescending { it.handedBackAt }
                .take(HANDED_BACK_SHOWN)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Who it can go to: the firm's people. Only loaded for somebody who may
     * issue, which is somebody who may already read the list of who is in it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val people: StateFlow<List<AccountEntity>> = session
        .flatMapLatest { state ->
            val me = state as? SessionRepository.State.SignedIn
            val active = me?.active
            if (me == null || active == null || !PpeRepository.mayIssue(me.role)) {
                flowOf(emptyList())
            } else {
                combine(
                    container.memberships.observeForCompany(active.companyId),
                    container.accounts.observeAccounts(),
                ) { rows, accounts ->
                    val ids = rows.map { it.accountId }.toSet()
                    accounts.filter { it.id in ids }.sortedBy { it.displayName.lowercase() }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The safety kit on the stock list, which is what an issue can come off. */
    val stock: StateFlow<List<InventoryItemEntity>> =
        container.inventory.observe(query = "", kind = SAFETY_KIND, lowStockOnly = false)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refusal = MutableStateFlow<PpeRepository.Refusal?>(null)
    val refusal: StateFlow<PpeRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    /**
     * One event per issue that went through, carrying how far short the
     * stock count was, so the screen can clear its form and say so.
     */
    private val _issued = MutableSharedFlow<Double?>(extraBufferCapacity = 1)
    val issued: SharedFlow<Double?> = _issued.asSharedFlow()

    fun issue(
        account: AccountEntity?,
        typedName: String,
        item: InventoryItemEntity?,
        itemName: String,
        quantity: Int,
        size: String,
        replaceBy: Long?,
        signature: String,
    ) = viewModelScope.launch {
        val me = signedIn
        if (me == null) {
            _refusal.value = PpeRepository.Refusal.NOT_ALLOWED
            return@launch
        }
        container.ppe.issue(
            role = me.role,
            companyId = me.active?.companyId,
            accountId = account?.id,
            holderName = account?.displayName ?: typedName,
            inventoryItemId = item?.id,
            itemName = itemName,
            quantity = quantity,
            size = size,
            replaceBy = replaceBy,
            signature = signature,
            byAccountId = me.account.id,
            byName = me.account.displayName,
        ).onSuccess { done ->
            _issued.tryEmit(done.stockShortBy)
        }.onFailure { failure ->
            _refusal.value = (failure as? PpeRepository.Refused)?.refusal
                ?: PpeRepository.Refusal.UNKNOWN
        }
    }

    fun handBack(issueId: String) = viewModelScope.launch {
        val me = signedIn
        if (me == null) {
            _refusal.value = PpeRepository.Refusal.NOT_ALLOWED
            return@launch
        }
        container.ppe.handBack(me.role, issueId, me.account.displayName)
            .onFailure { failure ->
                _refusal.value = (failure as? PpeRepository.Refused)?.refusal
                    ?: PpeRepository.Refusal.UNKNOWN
            }
    }

    companion object {
        /** The stock kind protective equipment is filed under. See the catalogue's item kinds. */
        const val SAFETY_KIND = "SAFETY"

        private const val HANDED_BACK_SHOWN = 30
    }
}
