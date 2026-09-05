package il.co.tradesmanager.ui.evidence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.data.local.entity.PermitEntity
import il.co.tradesmanager.data.local.entity.PermitPrecautionEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PermitDetailViewModel(
    private val container: AppContainer,
    private val permitId: String,
) : ViewModel() {

    val permit: StateFlow<PermitEntity?> = container.evidence.observePermit(permitId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val precautions: StateFlow<List<PermitPrecautionEntity>> =
        container.evidence.observePrecautions(permitId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    fun tick(precaution: PermitPrecautionEntity, checked: Boolean) = viewModelScope.launch {
        container.evidence.tick(precaution, checked)
    }

    /**
     * The screen has already disabled the button when a precaution is
     * outstanding; the repository checks again and refuses. Nothing here reads
     * the result, because there is no sensible screen for "the rule you can
     * see on this page was broken" — its purpose is that no code path skips it.
     */
    fun issue(validFrom: Long, validTo: Long) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.evidence.issue(
            permitId = permitId,
            validFrom = validFrom,
            validTo = validTo,
            issuedByName = actor,
            signatureStrokes = null,
        )
    }

    fun close(notes: String?) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.evidence.close(permitId, actor, notes)
    }

    fun cancel() = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        permit.value?.let { container.evidence.cancel(it, actor) }
    }
}
