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
 * The conflict rules live in [il.co.tradesmanager.core.sync.SyncPolicy] and are
 * written and tested already, because they are the part of sync that has
 * nothing to do with networks and everything to do with not destroying a signed
 * permit. What is missing is the half that cannot be written without somewhere
 * to sync to: the transport, authentication, and tombstones so a delete on one
 * phone is a delete everywhere rather than a row that comes back.
 *
 * Anyone implementing this against a real server: the local database is the
 * source of truth for anything not yet pushed, the server is the arbiter of
 * time — a phone with its clock set wrong is the failure mode that quietly
 * ruins last-writer-wins, and only the server knows what time it really is.
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
