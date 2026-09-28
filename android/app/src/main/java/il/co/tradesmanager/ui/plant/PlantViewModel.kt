package il.co.tradesmanager.ui.plant

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.safety.Examinations
import il.co.tradesmanager.core.safety.PreUse
import il.co.tradesmanager.data.local.entity.EquipmentEntity
import il.co.tradesmanager.data.local.entity.PhotoEntity
import il.co.tradesmanager.data.local.entity.PlantExaminationEntity
import il.co.tradesmanager.data.local.entity.PlantCheckEntity
import il.co.tradesmanager.data.local.entity.ProjectEntity
import il.co.tradesmanager.data.repository.EquipmentRepository
import il.co.tradesmanager.data.repository.PhotoRepository
import il.co.tradesmanager.data.repository.PlantExaminationRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlantViewModel(private val container: AppContainer) : ViewModel() {

    val equipment: StateFlow<List<EquipmentEntity>> = container.equipment.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Jobs a machine can be sent to. Only live ones. */
    val projects: StateFlow<List<ProjectEntity>> = container.projects.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    private suspend fun actor(): String = container.settings.settings.first().actorName

    /** Every machine's newest pre-use check, by machine. */
    val latestChecks: StateFlow<Map<String, PlantCheckEntity>> = container.equipment.observeLatestChecks()
        .map { checks -> checks.associateBy { it.equipmentId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _notSaved = MutableStateFlow(false)
    val notSaved: StateFlow<Boolean> = _notSaved.asStateFlow()

    fun clearNotSaved() {
        _notSaved.value = false
    }

    /** Why the last check was not accepted, so the dialog can say. */
    private val _preUseRefused = MutableStateFlow<PreUse.Refusal?>(null)
    val preUseRefused: StateFlow<PreUse.Refusal?> = _preUseRefused.asStateFlow()

    fun clearPreUseRefused() {
        _preUseRefused.value = null
    }

    /**
     * Records a walk-round. On a refusal the dialog stays open with the
     * reason, rather than closing as though it had worked.
     */
    fun recordPreUse(
        machine: EquipmentEntity,
        answers: Map<PreUse.Item, PreUse.Answer>,
        defectNote: String,
        onRecorded: () -> Unit,
    ) = viewModelScope.launch {
        val signedIn = session.value as? SessionRepository.State.SignedIn
        container.equipment.recordPreUse(
            equipment = machine,
            answers = answers,
            defectNote = defectNote,
            byAccountId = signedIn?.account?.id,
            byName = signedIn?.account?.displayName ?: actor(),
        ).onSuccess {
            onRecorded()
        }.onFailure { failure ->
            val refusal = (failure as? EquipmentRepository.PreUseRefused)?.refusal
            // A rule has a sentence of its own; anything else is a write that
            // did not happen, and says so rather than blaming the answers.
            if (refusal != null) _preUseRefused.value = refusal else _notSaved.value = true
        }
    }

    fun add(
        name: String,
        ownership: String,
        serialNumber: String?,
        hireRatePerDay: Double?,
        serviceDueOn: Long?,
    ) = viewModelScope.launch {
        container.equipment.add(name, ownership, serialNumber, hireRatePerDay, serviceDueOn, actor())
    }

    fun assign(equipment: EquipmentEntity, projectId: String?) = viewModelScope.launch {
        container.equipment.assign(equipment, projectId, actor())
    }

    fun setStatus(equipment: EquipmentEntity, status: String) = viewModelScope.launch {
        container.equipment.setStatus(equipment, status, actor())
    }

    fun recordService(equipment: EquipmentEntity, nextDueOn: Long?) = viewModelScope.launch {
        container.equipment.recordService(equipment, nextDueOn, actor())
    }

    fun remove(equipment: EquipmentEntity) = viewModelScope.launch {
        container.equipment.remove(equipment, actor())
    }

    /* --------------------------------------------- examination certificates */

    /** Every machine's latest examination, by machine. */
    val latestExaminations: StateFlow<Map<String, PlantExaminationEntity>> = container.examinations.observeLatest()
        .map { rows -> rows.associateBy { it.equipmentId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val mayRecordExaminations: StateFlow<Boolean> = session
        .map { (it as? SessionRepository.State.SignedIn)?.role?.let { role -> PlantExaminationRepository.mayWrite(role) } == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _examiningId = MutableStateFlow<String?>(null)

    /** The open machine's certificates, newest first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val examinations: StateFlow<List<PlantExaminationEntity>> = _examiningId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else container.examinations.observeFor(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Photographs of the newest certificate, which is the one an inspector asks to see. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val certificatePhotos: StateFlow<List<PhotoEntity>> = examinations
        .map { it.firstOrNull()?.id }
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.photos.observeFor(PhotoRepository.Owner.PLANT_EXAMINATION, id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openExaminations(equipmentId: String?) {
        _examiningId.value = equipmentId
    }

    private val _examinationRefusal = MutableStateFlow<PlantExaminationRepository.Refusal?>(null)
    val examinationRefusal: StateFlow<PlantExaminationRepository.Refusal?> = _examinationRefusal.asStateFlow()

    fun clearExaminationRefusal() {
        _examinationRefusal.value = null
    }

    fun recordExamination(
        machine: EquipmentEntity,
        examinedDaysAgo: Int,
        examinerName: String,
        certificateNumber: String,
        result: Examinations.Result,
        nextDueInMonths: Int?,
        notes: String,
    ) = viewModelScope.launch {
        val me = session.value as? SessionRepository.State.SignedIn
        if (me == null) {
            _examinationRefusal.value = PlantExaminationRepository.Refusal.NOT_ALLOWED
            return@launch
        }
        val examinedOn = LocalDate.now().minusDays(examinedDaysAgo.toLong())
        container.examinations.record(
            role = me.role,
            equipmentId = machine.id,
            equipmentName = machine.name,
            examinedOn = examinedOn,
            examinerName = examinerName,
            certificateNumber = certificateNumber,
            result = result,
            nextDueOn = nextDueInMonths?.let { examinedOn.plusMonths(it.toLong()) },
            notes = notes,
            byName = me.account.displayName,
        ).onFailure { failure ->
            _examinationRefusal.value = (failure as? PlantExaminationRepository.Refused)?.refusal
                ?: PlantExaminationRepository.Refusal.UNKNOWN
        }
    }

    fun newCertificateTarget(): Pair<String, Uri> = container.photos.newCameraTarget()

    fun certificateCaptured(photoId: String) = viewModelScope.launch {
        val id = examinations.value.firstOrNull()?.id ?: return@launch
        container.photos.recordCameraPhoto(
            id = photoId,
            ownerType = PhotoRepository.Owner.PLANT_EXAMINATION,
            ownerId = id,
            actorName = actor(),
        )
    }

    fun certificatePicked(uri: Uri) = viewModelScope.launch {
        val id = examinations.value.firstOrNull()?.id ?: return@launch
        container.photos.importPhoto(
            source = uri,
            ownerType = PhotoRepository.Owner.PLANT_EXAMINATION,
            ownerId = id,
            actorName = actor(),
        )
    }

    fun deletePhoto(photo: PhotoEntity) = viewModelScope.launch {
        container.photos.delete(photo, actor())
    }
}
