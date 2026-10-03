package il.co.tradesmanager.ui.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.security.AuditChain
import il.co.tradesmanager.core.security.Retention
import il.co.tradesmanager.data.local.entity.AuditLogEntity
import il.co.tradesmanager.data.repository.AuditTrail
import il.co.tradesmanager.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    /**
     * Set when a snapshot is ready to be written to a file.
     *
     * The view model does not touch the file system or the share sheet — both
     * need a Context, and a view model holding one outlives the screen that
     * gave it. So it prepares the snapshot and the screen writes it, then
     * calls [exportHandled] so a rotation does not export a second time.
     */
    private val _pendingExport = MutableStateFlow<AuditTrail.Snapshot?>(null)
    val pendingExport: StateFlow<AuditTrail.Snapshot?> = _pendingExport.asStateFlow()

    fun check() = viewModelScope.launch {
        _checking.value = true
        _verdict.value = container.auditTrail.verify()
        _checking.value = false
    }

    /**
     * Verifies, then hands the same rows and verdict to the screen.
     *
     * Exporting always checks first. A trail exported without one would go
     * into a tender folder saying nothing about whether it holds up, and the
     * person filing it would reasonably assume somebody had looked.
     */
    fun export() = viewModelScope.launch {
        _checking.value = true
        val snapshot = container.auditTrail.snapshot()
        _verdict.value = snapshot.verdict
        _checking.value = false
        _pendingExport.value = snapshot
    }

    fun exportHandled() {
        _pendingExport.value = null
    }

    /** How long the trail is kept. Zero means everything, and is the default. */
    val retentionDays: StateFlow<Int> = container.settings.settings
        .map { it.auditRetentionDays }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * How many entries the last purge removed, or null before one has run.
     *
     * Kept for as long as the screen is open rather than cleared on a timer:
     * it is a receipt for a deletion, and a receipt that vanishes while
     * somebody is reading it is worse than no receipt.
     */
    private val _purged = MutableStateFlow<Int?>(null)
    val purged: StateFlow<Int?> = _purged.asStateFlow()

    fun setRetention(days: Int) = viewModelScope.launch {
        container.settings.setAuditRetentionDays(days)
    }

    /**
     * Applies the period now, rather than on a schedule.
     *
     * Deleting records is not something to do quietly in the background while
     * somebody is looking at another screen. The period is a decision and so
     * is acting on it, and the purge writes its own entry saying what it cut.
     */
    fun applyRetention() = viewModelScope.launch {
        val days = container.settings.settings.first().auditRetentionDays
        val cutoff = Retention.cutoff(System.currentTimeMillis(), days) ?: return@launch
        val actor = container.settings.settings.first().actorName
        _purged.value = container.auditTrail.purgeOlderThan(cutoff, actor)
        _verdict.value = null
    }

}
