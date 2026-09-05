package il.co.tradesmanager.ui.evidence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.local.entity.AccountEntity
import il.co.tradesmanager.data.local.entity.ToolboxTalkAttendeeEntity
import il.co.tradesmanager.data.local.entity.ToolboxTalkEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TalkDetailViewModel(
    private val container: AppContainer,
    private val talkId: String,
) : ViewModel() {

    val talk: StateFlow<ToolboxTalkEntity?> = container.evidence.observeTalk(talkId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val attendees: StateFlow<List<ToolboxTalkAttendeeEntity>> =
        container.evidence.observeAttendees(talkId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The crew of the company this talk belongs to, so the common case is two
     * taps rather than typing a name.
     *
     * Scoped like every other list of people: somebody who works for two firms
     * on one phone must not be offered the other firm's crew on a register.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val crew: StateFlow<List<AccountEntity>> = container.session.state
        .flatMapLatest { state ->
            val signedIn = state as? SessionRepository.State.SignedIn
                ?: return@flatMapLatest flowOf(emptyList())
            combine(
                container.memberships.observeForCompany(signedIn.active?.companyId),
                container.accounts.observeAccounts(),
            ) { rows, accounts ->
                rows.mapNotNull { row -> accounts.firstOrNull { it.id == row.accountId } }
                    .sortedBy { it.displayName }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    fun signIn(accountId: String?, name: String) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.evidence.signIn(
            talkId = talkId,
            accountId = accountId,
            name = name,
            signatureStrokes = null,
            actorName = actor,
        )
    }

    fun remove(attendee: ToolboxTalkAttendeeEntity) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.evidence.removeAttendee(attendee, actor)
    }
}
