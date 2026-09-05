package il.co.tradesmanager.ui.tempworks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.safety.TemporaryWorks
import il.co.tradesmanager.data.local.entity.ConcretePourEntity
import il.co.tradesmanager.data.local.entity.TemporaryWorksEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.data.repository.TemporaryWorksRepository
import il.co.tradesmanager.di.AppContainer
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The register for one job, and whichever item is open on it. */
class TemporaryWorksViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    private val _openId = MutableStateFlow<String?>(null)
    val openId: StateFlow<String?> = _openId.asStateFlow()

    val items: StateFlow<List<TemporaryWorksEntity>> = container.temporaryWorks
        .observeForProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val open: StateFlow<TemporaryWorksEntity?> = _openId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else container.temporaryWorks.observe(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The pours on this job, so propping can be tied to the one it holds up. */
    val pours: StateFlow<List<ConcretePourEntity>> = container.concrete.observePours(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    fun openItem(id: String?) { _openId.value = id }

    fun create(
        description: String,
        kind: TemporaryWorksRepository.Kind,
        category: TemporaryWorks.CheckCategory,
    ) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        val item = container.temporaryWorks.create(projectId, description, kind, category, actor)
        _openId.value = item.id
    }

    fun setDesign(
        designReference: String?,
        designerName: String?,
        minimumStrikingDays: Long,
        pour: ConcretePourEntity?,
    ) = viewModelScope.launch {
        val item = open.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.temporaryWorks.setDesign(
            item = item,
            designReference = designReference,
            designerName = designerName,
            minimumStrikingDays = minimumStrikingDays,
            supportsPourId = pour?.id,
            // The pour's own start, copied across. A pour recorded later, or by
            // somebody else, still leaves the link empty and the gate open —
            // which is why the engineer's day count is typed as well.
            supportsPourAt = pour?.startedAt,
            actorName = actor,
        )
    }

    fun markChecked(checkerName: String) = viewModelScope.launch {
        val item = open.value ?: return@launch
        val actor = container.settings.settings.first().actorName
        container.temporaryWorks.markChecked(item, checkerName, actor)
    }

    fun markErected() = withOpen { item, actor ->
        container.temporaryWorks.markErected(item, actor)
    }

    fun markInspected() = withOpen { item, actor ->
        container.temporaryWorks.markInspected(item, actor)
    }

    fun markLoaded() = withOpen { item, actor ->
        container.temporaryWorks.markLoaded(item, actor)
    }

    fun release() = withOpen { item, actor ->
        container.temporaryWorks.release(item, actor)
    }

    fun markStruck() = withOpen { item, actor ->
        container.temporaryWorks.markStruck(item, actor)
    }

    private fun withOpen(block: suspend (TemporaryWorksEntity, String) -> Unit) =
        viewModelScope.launch {
            val item = open.value ?: return@launch
            block(item, container.settings.settings.first().actorName)
        }

    companion object {

        fun categoryOf(item: TemporaryWorksEntity): TemporaryWorks.CheckCategory =
            runCatching { TemporaryWorks.CheckCategory.valueOf(item.checkCategory) }
                .getOrDefault(TemporaryWorks.CheckCategory.ZERO)

        fun kindOf(item: TemporaryWorksEntity): TemporaryWorksRepository.Kind =
            runCatching { TemporaryWorksRepository.Kind.valueOf(item.kind) }
                .getOrDefault(TemporaryWorksRepository.Kind.OTHER)

        fun loadBlockers(item: TemporaryWorksEntity): Set<TemporaryWorks.LoadBlocker> =
            TemporaryWorks.loadBlockers(
                designReference = item.designReference,
                category = categoryOf(item),
                checkedAt = item.checkedAt,
                checkerName = item.checkerName,
                designerName = item.designerName,
                erectedAt = item.erectedAt,
                inspectedAt = item.inspectedAt,
                struckAt = item.struckAt,
            )

        fun strikeBlockers(
            item: TemporaryWorksEntity,
            now: Long,
            zone: ZoneId,
        ): Set<TemporaryWorks.StrikeBlocker> = TemporaryWorks.strikeBlockers(
            erectedAt = item.erectedAt,
            struckAt = item.struckAt,
            supportsPourAt = item.supportsPourAt,
            minimumStrikingDays = item.minimumStrikingDays,
            releasedAt = item.releasedAt,
            now = now,
            zone = zone,
        )
    }
}
