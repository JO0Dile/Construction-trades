package il.co.tradesmanager.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.access.Lens
import il.co.tradesmanager.core.people.TicketGaps
import il.co.tradesmanager.data.catalog.ProjectKind
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.local.entity.TradeEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Who in the firm lacks a ticket their trade usually needs. See TicketGaps. */
class TicketGapsViewModel(private val container: AppContainer) : ViewModel() {

    data class Row(val account: AccountEntity, val tradeId: String, val gaps: List<TicketGaps.Gap>)

    private val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionRepository.State.Loading)

    private val usual = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    private val _kinds = MutableStateFlow<List<ProjectKind>>(emptyList())

    /** The kinds, with every name each goes by, for matching and for showing. */
    val kinds: StateFlow<List<ProjectKind>> = _kinds

    init {
        viewModelScope.launch {
            usual.value = container.certifications.usualTicketsByTrade()
            _kinds.value = container.certifications.suggestedKinds()
        }
    }

    val trades: StateFlow<List<TradeEntity>> = container.trades.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The firm's people with a trade, only for somebody who may read the
     * People lens -- the same people, and the same tickets, the crew screen
     * shows them.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val people: StateFlow<List<Pair<AccountEntity, String>>> = session
        .flatMapLatest { state ->
            val me = state as? SessionRepository.State.SignedIn
            val active = me?.active
            if (me == null || active == null || !me.role.canRead(Lens.PEOPLE)) {
                flowOf(emptyList())
            } else {
                combine(
                    container.memberships.observeForCompany(active.companyId),
                    container.accounts.observeAccounts(),
                ) { rows, accounts ->
                    val byId = accounts.associateBy { it.id }
                    rows.mapNotNull { row ->
                        val account = byId[row.accountId] ?: return@mapNotNull null
                        val trade = row.tradeId ?: return@mapNotNull null
                        account to trade
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Everybody with a gap, grouped the way the screen reads: by trade, then by name. */
    val rows: StateFlow<List<Row>> = combine(
        people,
        container.certifications.observeByAccount(),
        usual,
        _kinds,
    ) { everyone, tickets, usualByTrade, allKinds ->
        val titles = allKinds.associate { it.id to it.names.values }
        val now = System.currentTimeMillis()
        everyone.mapNotNull { (account, trade) ->
            val gaps = TicketGaps.gapsFor(
                usual = usualByTrade[trade].orEmpty(),
                kindTitles = titles,
                held = tickets[account.id].orEmpty().map { TicketGaps.Held(it.title, it.expiresOn) },
                now = now,
            )
            if (gaps.isEmpty()) null else Row(account, trade, gaps)
        }.sortedWith(compareBy({ it.tradeId }, { it.account.displayName.lowercase() }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** How many people were looked at: a trade that usually needs something, and a person in it. */
    val checked: StateFlow<Int> = combine(people, usual) { everyone, usualByTrade ->
        everyone.count { (_, trade) -> usualByTrade[trade].orEmpty().isNotEmpty() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
