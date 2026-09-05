package il.co.tradesmanager.ui.evidence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.evidence.Briefings
import il.co.tradesmanager.data.local.dao.BriefingRecord
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.local.entity.ToolboxTalkEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TalksViewModel(private val container: AppContainer) : ViewModel() {

    val talks: StateFlow<List<ToolboxTalkEntity>> = container.evidence.observeTalks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val projects: StateFlow<List<ProjectEntity>> = container.projects.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Only the people who need briefing.
     *
     * The filtering happens here rather than in SQL because "due" is ninety
     * days measured against the clock, and there is one tested definition of
     * that in [Briefings]. A second one written into a query would be the copy
     * that quietly disagreed.
     */
    val needBriefing: StateFlow<List<BriefingRecord>> = container.evidence.observeBriefingRecords()
        .map { records ->
            val now = System.currentTimeMillis()
            records.filter { Briefings.state(it.lastAttendedAt, now) != Briefings.State.RECENT }
                .sortedBy { Briefings.urgency(it.lastAttendedAt, now) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    fun record(
        projectId: String?,
        topic: String,
        notes: String?,
        onRecorded: (String) -> Unit,
    ) = viewModelScope.launch {
        val settings = container.settings.settings.first()
        val talk = container.evidence.recordTalk(
            projectId = projectId,
            topic = topic,
            notes = notes,
            // Talks are written up as they happen or minutes afterwards, so
            // now is right and a date picker would only be a step to skip.
            heldAt = System.currentTimeMillis(),
            presenterName = settings.actorName,
            presenterId = (session.value as? SessionRepository.State.SignedIn)?.account?.id,
            actorName = settings.actorName,
        )
        onRecorded(talk.id)
    }
}
