package il.co.tradesmanager.data.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Cloud sync, kept behind an interface on purpose.
 *
 * The app is offline-first: every screen reads and writes the local database
 * and nothing waits on the network. Sync is an optional extra that a
 * deployment chooses — Firestore for a small firm, a self-hosted REST service
 * for a government installation that may not send site data to a third party
 * at all. Keeping it to this interface is what makes the on-premise build a
 * different implementation rather than a different app.
 *
 * The conflict rule for the first release is last-write-wins by
 * [SyncRecord.updatedAt], with one exception the field demands: stock
 * movements and audit entries are append-only and are merged, never
 * overwritten, so two people counting the same van do not erase each other.
 */
interface SyncEngine {

    enum class Status { DISABLED, IDLE, SYNCING, ERROR }

    val status: Flow<Status>

    suspend fun syncNow(): Result<SyncSummary>

    data class SyncSummary(val pushed: Int, val pulled: Int, val conflicts: Int)

    data class SyncRecord(val entityType: String, val entityId: String, val updatedAt: Long)
}

/** The default in a fresh install and in the offline-only build. */
class NoOpSyncEngine : SyncEngine {
    override val status = MutableStateFlow(SyncEngine.Status.DISABLED)
    override suspend fun syncNow(): Result<SyncEngine.SyncSummary> =
        Result.success(SyncEngine.SyncSummary(0, 0, 0))
}
