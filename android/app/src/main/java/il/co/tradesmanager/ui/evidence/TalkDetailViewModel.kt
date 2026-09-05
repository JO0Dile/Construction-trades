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
import kotlinx.coroutines.flow.first
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

    /** The crew, so the common case is two taps rather than typing a name. */
    val crew: StateFlow<List<AccountEntity>> = container.accounts.observeAccounts()
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
