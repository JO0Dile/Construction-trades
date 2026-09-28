package il.co.tradesmanager.ui.substances

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.safety.Substances
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.local.entity.SubstanceEntity
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.data.repository.SubstanceRepository
import il.co.tradesmanager.di.AppContainer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One job's hazardous substances: the ones with no data sheet on file, first. */
class SubstancesViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    data class Row(
        val substance: SubstanceEntity,
        val hazards: Set<Substances.Hazard>,
        val sheetOn: LocalDate?,
        val state: Substances.State,
    )

    private val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionRepository.State.Loading)

    val mayWrite: StateFlow<Boolean> = session
        .map { (it as? SessionRepository.State.SignedIn)?.role?.let { role -> SubstanceRepository.mayWrite(role) } == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Nothing for somebody who may not read the site's record. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<Row>> = session
        .flatMapLatest { state ->
            val role = (state as? SessionRepository.State.SignedIn)?.role
            if (role == null || !SubstanceRepository.mayRead(role)) {
                flowOf(emptyList<SubstanceEntity>())
            } else {
                container.substances.observeForProject(projectId)
            }
        }
        .map { all ->
            val today = LocalDate.now()
            val rows = all.map {
                val sheetOn = it.sheetOnDay?.let(LocalDate::ofEpochDay)
                Row(it, Substances.decode(it.hazards), sheetOn, Substances.state(sheetOn, it.removedAt != null, today))
            }
            Substances.order(rows.map { it.state to it.substance.name }).map { rows[it] }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The job's name, for the title of an export. */
    val jobName: StateFlow<String> = container.projects.observeProjects()
        .map { jobs -> jobs.firstOrNull { it.id == projectId }?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _openId = MutableStateFlow<String?>(null)

    val open: StateFlow<Row?> = combine(rows, _openId) { list, id -> list.firstOrNull { it.substance.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The open substance's data sheet, as photographed. Only ever for a row [rows] already let through. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val openPhotos: StateFlow<List<PhotoEntity>> = open
        .map { it?.substance?.id }
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.photos.observeFor(PhotoRepository.Owner.SUBSTANCE_SHEET, id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openSubstance(id: String?) {
        _openId.value = id
    }

    private val _refusal = MutableStateFlow<SubstanceRepository.Refusal?>(null)
    val refusal: StateFlow<SubstanceRepository.Refusal?> = _refusal.asStateFlow()

    fun clearRefusal() {
        _refusal.value = null
    }

    private fun signedIn(): SessionRepository.State.SignedIn? =
        (session.value as? SessionRepository.State.SignedIn).also {
            if (it == null) _refusal.value = SubstanceRepository.Refusal.NOT_ALLOWED
        }

    private fun report(failure: Throwable) {
        _refusal.value = (failure as? SubstanceRepository.Refused)?.refusal ?: SubstanceRepository.Refusal.UNKNOWN
    }

    fun add(arrival: SubstanceRepository.Arrival) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.substances.add(me.role, projectId, arrival, me.account.displayName).onFailure { report(it) }
    }

    /** [sheetOnMillis] as the calendar gives it back: midnight UTC of the day picked. */
    fun newSheet(substanceId: String, sheetOnMillis: Long) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.substances.newSheet(me.role, substanceId, dayOf(sheetOnMillis), me.account.displayName).onFailure { report(it) }
    }

    fun remove(substanceId: String) = viewModelScope.launch {
        val me = signedIn() ?: return@launch
        container.substances.remove(me.role, substanceId, me.account.displayName).onFailure { report(it) }
    }

    private suspend fun actor(): String = container.settings.settings.first().actorName

    fun newSheetTarget(): Pair<String, Uri> = container.photos.newCameraTarget()

    fun sheetCaptured(photoId: String) = viewModelScope.launch {
        val id = _openId.value ?: return@launch
        container.photos.recordCameraPhoto(
            id = photoId,
            ownerType = PhotoRepository.Owner.SUBSTANCE_SHEET,
            ownerId = id,
            actorName = actor(),
        )
    }

    fun sheetPicked(uri: Uri) = viewModelScope.launch {
        val id = _openId.value ?: return@launch
        container.photos.importPhoto(
            source = uri,
            ownerType = PhotoRepository.Owner.SUBSTANCE_SHEET,
            ownerId = id,
            actorName = actor(),
        )
    }

    fun deletePhoto(photo: PhotoEntity) = viewModelScope.launch {
        container.photos.delete(photo, actor())
    }

    companion object {
        /** The date picker's millis are midnight UTC; the day is read in UTC so it does not slip by one east of Greenwich. */
        fun dayOf(pickerMillis: Long): LocalDate = Instant.ofEpochMilli(pickerMillis).atZone(ZoneId.of("UTC")).toLocalDate()
    }
}
