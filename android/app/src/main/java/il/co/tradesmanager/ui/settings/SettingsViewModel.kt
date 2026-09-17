package il.co.tradesmanager.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import il.co.tradesmanager.core.i18n.LocaleController
import il.co.tradesmanager.core.security.Backup
import il.co.tradesmanager.data.backup.BackupRepository
import il.co.tradesmanager.data.backup.StagedRestore
import il.co.tradesmanager.data.local.DATABASE_VERSION
import il.co.tradesmanager.data.local.entity.TradeEntity
import il.co.tradesmanager.data.repository.SessionRepository
import il.co.tradesmanager.data.repository.SettingsRepository
import il.co.tradesmanager.data.repository.TradeRepository
import il.co.tradesmanager.data.update.UpdateRepository
import il.co.tradesmanager.di.AppContainer
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    /* ------------------------------------------------------------- backups */

    /**
     * Where a backup or a restore has got to.
     *
     * One state for both, because they never run at once and a screen showing
     * two independent progress lines invites somebody to start the second
     * while the first is half way through a file.
     */
    sealed interface BackupState {
        data object Idle : BackupState
        data object Working : BackupState

        /** Written. [mediaCount] so the person can sanity-check the size. */
        data class BackedUp(val mediaCount: Int) : BackupState

        /**
         * Unpacked and waiting for the app to be closed and reopened.
         *
         * Nothing has been replaced yet, and [SettingsViewModel.cancelRestore]
         * still undoes it. The restart is where it actually happens.
         */
        data class Staged(val takenAt: Long, val needsMigrating: Boolean) : BackupState

        /**
         * What a backup holds, looked at without restoring it.
         *
         * The counts are there to be argued with: somebody who knows they run
         * six jobs and sees two has learned something, and learned it on a day
         * that costs them nothing.
         */
        data class Checked(val contents: BackupRepository.Contents) : BackupState

        /** The file is not one of ours, or is from a newer version. */
        data class Refused(val blocker: Backup.Blocker) : BackupState

        /**
         * It did not work.
         *
         * Deliberately does not distinguish a wrong passphrase from a damaged
         * file: the archive is authenticated, so both arrive as the same
         * failure to decrypt, and guessing which it was would be guessing.
         */
        data object Failed : BackupState
    }

    private val _backup = MutableStateFlow<BackupState>(BackupState.Idle)
    val backup: StateFlow<BackupState> = _backup.asStateFlow()

    /** What a restore staged in the last session did on the way into this one. */
    val restoreOutcome: StagedRestore.Outcome get() = container.restoreOutcome

    fun clearBackupState() {
        _backup.value = BackupState.Idle
    }

    /** The name to suggest in the file picker. */
    fun backupFileName(today: LocalDate = LocalDate.now()): String =
        Backup.fileName(today.year, today.monthValue, today.dayOfMonth)

    fun backUp(target: Uri, passphrase: String) = viewModelScope.launch {
        _backup.value = BackupState.Working
        val actor = container.settings.settings.first().actorName
        _backup.value = container.backups
            .backUp(target, passphrase.toCharArray(), actor.ifBlank { "unknown" })
            .fold(
                onSuccess = { BackupState.BackedUp(it) },
                onFailure = { BackupState.Failed },
            )
    }

    /**
     * Reads the header, then unpacks if the passphrase opens it.
     *
     * The two are one call from the screen's point of view but two answers:
     * a file that is not one of ours is named as that before the passphrase is
     * tried at all, so somebody who picked the wrong thing out of a file
     * browser is not told their passphrase is wrong.
     */
    fun restore(source: Uri, passphrase: String) = viewModelScope.launch {
        _backup.value = BackupState.Working
        val inspection = container.backups.inspect(source)
        val blocker = inspection.blocker
        if (blocker != null) {
            _backup.value = BackupState.Refused(blocker)
            return@launch
        }
        _backup.value = container.backups
            .stageRestore(source, passphrase.toCharArray())
            .fold(
                onSuccess = { header ->
                    BackupState.Staged(
                        takenAt = header.createdAt,
                        needsMigrating = Backup.needsMigrating(header, DATABASE_VERSION),
                    )
                },
                onFailure = { BackupState.Failed },
            )
    }

    /**
     * Opens a backup, counts what is in it, and throws the copy away.
     *
     * Nothing on the device is touched. A backup nobody has opened is a
     * promise rather than a record, and restoring in order to check would
     * mean replacing a working database to test the thing protecting it.
     */
    fun check(source: Uri, passphrase: String) = viewModelScope.launch {
        _backup.value = BackupState.Working
        val inspection = container.backups.inspect(source)
        val blocker = inspection.blocker
        if (blocker != null) {
            _backup.value = BackupState.Refused(blocker)
            return@launch
        }
        _backup.value = container.backups
            .check(source, passphrase.toCharArray())
            .fold(
                onSuccess = { BackupState.Checked(it) },
                onFailure = { BackupState.Failed },
            )
    }

    fun cancelRestore() {
        container.backups.cancelPendingRestore()
        _backup.value = BackupState.Idle
    }


    val settings: StateFlow<SettingsRepository.Settings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.Settings())

    val trades: StateFlow<List<TradeEntity>> = container.catalogDao.observeTrades()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val session: StateFlow<SessionRepository.State> = container.session.state
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SessionRepository.State.Loading,
        )

    /**
     * Adds an ID number to an account that has none. Refused if one is already
     * set — see [il.co.tradesmanager.data.repository.AccountRepository.setIdNumber].
     */
    fun setIdNumber(accountId: String, idNumber: String) = viewModelScope.launch {
        container.accounts.setIdNumber(accountId, idNumber)
    }

    /** Switching which company's work is on screen. */
    fun switchCompany(companyId: String?) = viewModelScope.launch {
        container.session.switchCompany(companyId)
    }

    fun signOut() = viewModelScope.launch { container.session.signOut() }

    private val _reseedResult = MutableStateFlow<Int?>(null)
    val reseedResult: StateFlow<Int?> = _reseedResult.asStateFlow()

    /** Reported in the security section when SQLCipher could not be loaded. */
    val databaseIsEncrypted: Boolean = container.databaseIsEncrypted

    fun setLanguage(tag: String) = LocaleController.apply(tag)

    fun setThemeMode(mode: SettingsRepository.ThemeMode) = viewModelScope.launch {
        container.settings.setThemeMode(mode)
    }

    fun setLargeText(value: Boolean) = viewModelScope.launch { container.settings.setLargeText(value) }

    fun toggleTrade(tradeId: String, selected: Boolean) = viewModelScope.launch {
        container.catalogDao.setTradeSelected(tradeId, selected)
        // Turning a trade on stocks its catalogue; turning one off never
        // removes stock, because the quantities are the user's own record.
        if (selected) container.seeder.stockTrades(listOf(tradeId))
    }

    /**
     * A trade the app ships nothing for. It arrives selected and empty, and
     * the user stocks it from their own items — which is the only honest
     * thing to do, since we have no catalogue to give them for it.
     */
    fun addTrade(name: String, languageTag: String) = viewModelScope.launch {
        val actor = container.settings.settings.first().actorName
        container.trades.createCustom(name, languageTag, actor)
    }

    fun deleteTrade(trade: TradeEntity) = viewModelScope.launch {
        container.trades.delete(trade, container.settings.settings.first().actorName)
    }

    fun isCustom(trade: TradeEntity): Boolean = TradeRepository.isCustom(trade)

    /**
     * The update check, as the screen sees it. One flow rather than four
     * booleans, so the button can never read "check" while a download is
     * running.
     */
    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data object UpToDate : UpdateState

        /** The feed answered and is empty — not the same as no signal. */
        data object NoReleases : UpdateState
        data object Failed : UpdateState
        data class Available(val release: UpdateRepository.Release) : UpdateState
        data class Downloading(val release: UpdateRepository.Release, val fraction: Float) : UpdateState
        data class Ready(val apk: File) : UpdateState
    }

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    fun checkForUpdate() = viewModelScope.launch {
        _update.value = UpdateState.Checking
        _update.value = when (val result = container.updates.check()) {
            is UpdateRepository.Result.Available -> UpdateState.Available(result.release)
            UpdateRepository.Result.UpToDate -> UpdateState.UpToDate
            UpdateRepository.Result.NoReleases -> UpdateState.NoReleases
            UpdateRepository.Result.Unavailable -> UpdateState.Failed
        }
    }

    fun downloadUpdate(release: UpdateRepository.Release) = viewModelScope.launch {
        _update.value = UpdateState.Downloading(release, 0f)
        val apk = container.updates.download(release) { fraction ->
            _update.value = UpdateState.Downloading(release, fraction)
        }
        _update.value = if (apk == null) UpdateState.Failed else UpdateState.Ready(apk)
    }

    fun installIntent(apk: File) = container.updates.installIntent(apk)

    fun reseed() = viewModelScope.launch {
        val ids = container.catalogDao.selectedTradeIds()
        container.seeder.loadReferenceData()
        _reseedResult.value = container.seeder.stockTrades(ids)
    }

    /**
     * Account and data deletion, required in-app by both stores for an account
     * that can be created in-app. Clears preferences and every local table;
     * the encryption key is dropped last so nothing readable is left behind.
     */
    fun deleteEverything(onDone: () -> Unit) = viewModelScope.launch {
        container.database.clearAllTables()
        container.settings.clearAll()
        onDone()
    }
}
