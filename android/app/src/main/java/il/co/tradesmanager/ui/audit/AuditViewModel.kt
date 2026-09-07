package il.co.tradesmanager.ui.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.security.AuditChain
import il.co.tradesmanager.data.local.entity.AuditLogEntity
import il.co.tradesmanager.data.repository.AuditTrail
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The trail, and whether it still adds up. */
class AuditViewModel(private val container: AppContainer) : ViewModel() {

    val entries: StateFlow<List<AuditLogEntity>> = container.auditTrail.recent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Null until somebody asks.
     *
     * Verification hashes every entry in the window, which is work; doing it
     * on every recomposition of a settings screen would make opening the
     * screen slow for a question most people are not asking. It is a button,
     * and the screen says so rather than showing a stale verdict.
     */
    private val _verdict = MutableStateFlow<AuditChain.Verdict?>(null)
    val verdict: StateFlow<AuditChain.Verdict?> = _verdict.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    val window: Int get() = AuditTrail.VERIFY_WINDOW

    fun check() = viewModelScope.launch {
        _checking.value = true
        _verdict.value = container.auditTrail.verify()
        _checking.value = false
    }
}
