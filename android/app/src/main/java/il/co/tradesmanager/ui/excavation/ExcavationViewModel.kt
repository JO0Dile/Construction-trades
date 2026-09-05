package il.co.tradesmanager.ui.excavation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.safety.Excavation
import il.co.tradesmanager.data.local.entity.ExcavationEntity
import il.co.tradesmanager.data.local.entity.ExcavationInspectionEntity
import il.co.tradesmanager.data.repository.SessionRepository
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

/** The trenches on one job, and whichever one is open. */
class ExcavationViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    private val _openId = MutableStateFlow<String?>(null)
    val openId: StateFlow<String?> = _openId.asStateFlow()

    val excavations: StateFlow<List<ExcavationEntity>> = container.excavations
        .observeForProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val open: StateFlow<ExcavationEntity?> = _openId
        .flatMapLatest { id -> if (id == null) flowOf(null) else container.excavations.observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val inspections: StateFlow<List<ExcavationInspectionEntity>> = _openId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.excavations.observeInspections(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    fun openExcavation(id: String?) { _openId.value = id }

    fun create(location: String, depthMetres: Double?, support: Excavation.Support) =
        viewModelScope.launch {
            val actor = container.settings.settings.first().actorName
            val created =
                container.excavations.create(projectId, location, depthMetres, support, actor)
            _openId.value = created.id
        }

    fun setDimensions(depthMetres: Double?, support: Excavation.Support) = withOpen { item, actor ->
        container.excavations.setDimensions(item, depthMetres, support, actor)
    }

    fun recordServicesLocated(notes: String?) = withOpen { item, actor ->
        container.excavations.recordServicesLocated(item, notes, actor)
    }

    fun inspect(passed: Boolean, defects: String?, actionTaken: String?, signature: String?) =
        withOpen { item, actor ->
            container.excavations.inspect(
                excavation = item,
                passed = passed,
                defects = defects,
                actionTaken = actionTaken,
                signature = signature,
                inspectorName = actor,
            )
        }

    fun markDisturbed(note: String?) = withOpen { item, actor ->
        container.excavations.markDisturbed(item, note, actor)
    }

    fun backfill() = withOpen { item, actor ->
        container.excavations.backfill(item, actor)
    }

    private fun withOpen(block: suspend (ExcavationEntity, String) -> Unit) =
        viewModelScope.launch {
            val item = open.value ?: return@launch
            block(item, container.settings.settings.first().actorName)
        }

    companion object {

        fun supportOf(item: ExcavationEntity): Excavation.Support =
            runCatching { Excavation.Support.valueOf(item.support) }
                .getOrDefault(Excavation.Support.NONE)

        fun blockersFor(
            item: ExcavationEntity,
            now: Long,
            zone: ZoneId,
        ): Set<Excavation.Blocker> = Excavation.blockers(
            servicesLocatedAt = item.servicesLocatedAt,
            depthMetres = item.depthMetres,
            support = supportOf(item),
            lastInspectedAt = item.lastInspectedAt,
            lastInspectionPassed = item.lastInspectionPassed,
            disturbedAt = item.disturbedAt,
            backfilledAt = item.backfilledAt,
            now = now,
            zone = zone,
        )
    }
}
