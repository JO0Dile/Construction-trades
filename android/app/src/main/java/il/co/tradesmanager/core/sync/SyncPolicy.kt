package il.co.tradesmanager.core.sync

/**
 * Who wins when two devices disagree about the same row.
 *
 * This is the part of sync worth getting right, and the part that has nothing
 * to do with networks — which is why it is here, tested, before there is
 * anything to sync with. Getting the transport wrong costs a retry. Getting
 * this wrong silently destroys a signed record, and nobody finds out until
 * somebody goes looking for it.
 *
 * Three kinds of row, three answers:
 *
 * **Sealed.** Somebody signed it: a permit that was issued, a daily log that
 * was signed off, an induction. A signature is a person putting their name to a
 * state of the world at a moment, and no automatic rule may overwrite one. A
 * sealed row beats an unsealed one whatever the timestamps say — an edit made
 * on another phone was made in ignorance of the signature.
 *
 * **Append-only.** Rows that record that something happened: stock movements,
 * audit entries, incidents. Two devices can only ever add different ones, so
 * there is nothing to resolve — keep both. This is why stock levels are
 * derived from movements rather than stored as a number: a number needs
 * merging and can be lost, a list of movements cannot.
 *
 * **Mutable.** Everything else — a job's name, a task, a material line. Latest
 * write wins, ordered by [Revision] so every device agrees which that is.
 */
object SyncPolicy {

    enum class Kind {
        /** Only ever inserted. Union, never a conflict. */
        APPEND_ONLY,

        /** Latest write wins. */
        MUTABLE,

        /** Signed. Never overwritten automatically. */
        SEALED,
    }

    /**
     * Tables whose rows are written once and never edited.
     *
     * Deliberately a short, explicit list rather than a guess. A row wrongly
     * called append-only stops receiving corrections; a row wrongly called
     * mutable is merely resolved more carefully than it needed to be. The
     * costs are not symmetrical, so anything not named here is mutable.
     */
    private val APPEND_ONLY_TABLES = setOf(
        "audit_log",
        "stock_movements",
        "incidents",
        "photos",
    )

    fun kindOf(entityType: String, isSealed: Boolean): Kind = when {
        isSealed -> Kind.SEALED
        entityType in APPEND_ONLY_TABLES -> Kind.APPEND_ONLY
        else -> Kind.MUTABLE
    }

    /** One side's view of a row. */
    data class Version(
        val entityType: String,
        val revision: Revision,
        val isSealed: Boolean = false,
    ) {
        val kind: Kind get() = kindOf(entityType, isSealed)
    }

    sealed interface Resolution {
        /** The local row stands; nothing to write. */
        data object KeepLocal : Resolution

        /** Take the other side's row. */
        data object TakeRemote : Resolution

        /** Both rows are real and both are kept. */
        data object KeepBoth : Resolution

        /**
         * Two signatures on the same row, and no rule may pick between them.
         * The local one stands until a person decides, and the disagreement is
         * reported rather than quietly resolved.
         */
        data object NeedsAttention : Resolution
    }

    fun resolve(local: Version, remote: Version): Resolution {
        if (local.kind == Kind.APPEND_ONLY && remote.kind == Kind.APPEND_ONLY) {
            return Resolution.KeepBoth
        }
        if (local.isSealed && remote.isSealed) {
            // Identical signatures are the ordinary case: the same row, synced
            // twice. Different ones mean two people signed the same thing.
            return if (local.revision == remote.revision) {
                Resolution.KeepLocal
            } else {
                Resolution.NeedsAttention
            }
        }
        if (local.isSealed) return Resolution.KeepLocal
        if (remote.isSealed) return Resolution.TakeRemote
        return if (local.revision >= remote.revision) {
            Resolution.KeepLocal
        } else {
            Resolution.TakeRemote
        }
    }
}
