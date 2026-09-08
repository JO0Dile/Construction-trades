package il.co.tradesmanager.ui.safety

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.local.entity.ChecklistRunEntity
import il.co.tradesmanager.data.local.entity.ChecklistRunItemEntity
import il.co.tradesmanager.data.local.entity.ChecklistTemplateEntity
import il.co.tradesmanager.data.local.entity.ChecklistTemplateItemEntity
import il.co.tradesmanager.data.repository.SafetyRepository
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChecklistRunViewModel(
    private val container: AppContainer,
    private val templateId: String,
) : ViewModel() {

    data class State(
        val template: ChecklistTemplateEntity? = null,
        val checks: List<ChecklistTemplateItemEntity> = emptyList(),
        val answers: Map<String, ChecklistRunItemEntity> = emptyMap(),
        val run: ChecklistRunEntity? = null,
        val signerName: String = "",
    ) {
        val blocked: Boolean get() = run?.blocked ?: true
        val signed: Boolean get() = run?.completedAt != null
        val answeredCount: Int get() = answers.size
    }

    private val runId = MutableStateFlow<String?>(null)
    private val loaded = MutableStateFlow(State())

    // flowOf(...) rather than emptyFlow() on the null branch: combine waits for
    // every source to emit at least once, so an empty flow here would leave the
    // screen frozen on its initial state until the run id arrived.
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<State> = combine(
        loaded,
        runId.flatMapLatest { id -> if (id == null) flowOf(null) else container.safety.observeRun(id) },
        runId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else container.safety.observeRunItems(id)
        },
    ) { base, run, items ->
        base.copy(run = run, answers = items.associateBy { it.templateItemId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    init {
        viewModelScope.launch {
            val settings = container.settings.settings.first()
            val template = container.catalogDao.checklistTemplate(templateId)
            val checks = container.safety.templateItems(templateId)
            loaded.value = State(template = template, checks = checks, signerName = settings.actorName)
            runId.value = container.safety.startRun(templateId, null, settings.actorName).id
        }
    }

    fun answer(templateItemId: String, state: String) {
        val id = runId.value ?: return
        viewModelScope.launch { container.safety.answer(id, templateItemId, state, null) }
    }

    fun setSignerName(name: String) { loaded.value = loaded.value.copy(signerName = name) }

    /**
     * Signs the run if the repository allows it. [onSigned] is called with
     * false when a critical check is outstanding, so the screen can say why
     * instead of silently doing nothing.
     */
    fun signOff(signature: String?, onSigned: (Boolean) -> Unit) {
        val id = runId.value ?: return onSigned(false)
        viewModelScope.launch {
            // Who is signed in, not only what was typed into the box. The
            // typed name stays -- a register has to read years later without
            // the account table beside it -- but the id is what ties the
            // signature to a person the app can actually check.
            val signedIn = container.session.state.first()
                as? SessionRepository.State.SignedIn
            val name = loaded.value.signerName
                .ifBlank { signedIn?.account?.displayName.orEmpty() }
                .ifBlank { "unknown" }
            onSigned(
                container.safety.signOff(
                    runId = id,
                    signerName = name,
                    signatureStrokes = signature,
                    signedById = signedIn?.account?.id,
                ),
            )
        }
    }

    companion object {
        val PASS = SafetyRepository.State.PASS
        val FAIL = SafetyRepository.State.FAIL
        val NOT_APPLICABLE = SafetyRepository.State.NOT_APPLICABLE
    }
}
