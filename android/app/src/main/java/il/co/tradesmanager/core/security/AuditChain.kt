package il.co.tradesmanager.core.security

import java.security.MessageDigest

/**
 * Makes the audit trail tamper-evident.
 *
 * The DAO already refuses to update an audit row, but the database is a file
 * on a phone. Anybody who can reach it with a SQLite editor can change what a
 * row says or remove one, and nothing would ever notice. For a log whose whole
 * purpose is to be believed later — by an inspector, a court, a client
 * disputing what was signed off — "we did not write an update method" is not
 * an answer.
 *
 * So each entry carries the hash of everything it says, together with the hash
 * of the entry before it. Changing one word changes that entry's hash, which
 * breaks its successor's link, and so on to the end of the trail. Removing an
 * entry breaks the link across the gap and leaves a hole in the sequence.
 * Inserting one is worse still: it has to produce a hash the next entry
 * already committed to, which it cannot.
 *
 * **What this does not do.** It does not stop anyone altering the log — it
 * makes the alteration show. And it cannot detect the newest entries being
 * deleted, because nothing has yet committed to them: a trail truncated at the
 * end is indistinguishable from a trail that stopped. Detecting that needs the
 * head of the chain written somewhere the holder of the phone does not control,
 * which is a server, and is noted in `docs/SERVER.md` rather than pretended at
 * here.
 */
object AuditChain {

    /** What comes before the first entry: nothing, said in the right shape. */
    const val GENESIS = "0000000000000000000000000000000000000000000000000000000000000000"

    /**
     * One entry, as the chain sees it.
     *
     * A plain value rather than the Room entity so the rule can be tested
     * without a database, and so that adding a column to the table is a
     * decision about whether it belongs in the hash rather than a silent
     * change of what is signed.
     */
    data class Entry(
        val id: String,
        val sequence: Long,
        val entityType: String,
        val entityId: String,
        val action: String,
        val actorId: String?,
        val actorName: String,
        val summary: String,
        val payloadJson: String?,
        val occurredAt: Long,
        val previousHash: String,
        val hash: String,
    )

    /**
     * Every field written into a form that cannot be read two ways.
     *
     * Each field is prefixed with its length **in bytes**, so no value can
     * impersonate a field boundary: a summary containing `;` cannot be made to
     * look like the end of one field and the start of another, and moving a
     * character from one field to the next changes both lengths. A separator
     * alone would not do — free text is free, and a log is exactly where
     * somebody would put the separator on purpose.
     *
     * Bytes, not characters: "העקדה" is five characters and ten bytes, and a
     * length that disagreed with the encoding would be a boundary an attacker
     * could aim at.
     *
     * Null is `-;`, which no length-prefixed value can produce, so an absent
     * payload and an empty one are different things.
     */
    private fun field(value: String?): ByteArray {
        if (value == null) return "-;".toByteArray(Charsets.UTF_8)
        val bytes = value.toByteArray(Charsets.UTF_8)
        val prefix = "${bytes.size}:".toByteArray(Charsets.UTF_8)
        return prefix + bytes + SEMICOLON
    }

    private val SEMICOLON = ";".toByteArray(Charsets.UTF_8)

    /** The hash an entry with this content, following this one, must carry. */
    fun link(
        previousHash: String,
        sequence: Long,
        entityType: String,
        entityId: String,
        action: String,
        actorId: String?,
        actorName: String,
        summary: String,
        payloadJson: String?,
        occurredAt: Long,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(field(previousHash))
        digest.update(field(sequence.toString()))
        digest.update(field(entityType))
        digest.update(field(entityId))
        digest.update(field(action))
        digest.update(field(actorId))
        digest.update(field(actorName))
        digest.update(field(summary))
        digest.update(field(payloadJson))
        digest.update(field(occurredAt.toString()))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** The hash [entry] should be carrying, given what it says. */
    fun recompute(entry: Entry): String = link(
        previousHash = entry.previousHash,
        sequence = entry.sequence,
        entityType = entry.entityType,
        entityId = entry.entityId,
        action = entry.action,
        actorId = entry.actorId,
        actorName = entry.actorName,
        summary = entry.summary,
        payloadJson = entry.payloadJson,
        occurredAt = entry.occurredAt,
    )

    /** Why a trail failed to verify. */
    enum class Fault {
        /** The entry says something other than what its hash was taken over. */
        ALTERED,

        /** The entry does not follow the one before it. Something was removed. */
        BROKEN_LINK,

        /** Sequence numbers skip. An entry between these two is gone. */
        MISSING,
    }

    sealed interface Verdict {

        /** Nothing to check. A new install, not a suspicious one. */
        data object Empty : Verdict

        /**
         * Every chained entry verifies.
         *
         * [unchained] counts entries written before this app version, which
         * carry no hash and cannot be checked. They are reported rather than
         * ignored: a trail that is half unverifiable should say so, because
         * "intact" over a log that was never hashed is the more dangerous
         * answer.
         */
        data class Intact(val checked: Int, val unchained: Int) : Verdict

        /** The first place the trail stops adding up, and what is wrong there. */
        data class Failed(
            val fault: Fault,
            val sequence: Long,
            val id: String,
            val checked: Int,
            val unchained: Int,
        ) : Verdict
    }

    /**
     * Walks [entries] oldest first and reports the first thing that is wrong.
     *
     * The first surviving entry's `previousHash` is not checked against
     * anything, because there is nothing to check it against: it is either the
     * start of the trail or the edge of a retention purge. That boundary is
     * the one place a removal does not show, which is why the purge writes a
     * row of its own saying what it cut.
     *
     * Entries with no hash are skipped rather than failed. They predate the
     * chain; treating them as tampering would cry wolf on every install that
     * upgraded.
     */
    fun verify(entries: List<Entry>): Verdict {
        val chained = entries.filter { it.hash.isNotBlank() }
        val unchained = entries.size - chained.size
        if (chained.isEmpty()) {
            return if (unchained == 0) Verdict.Empty else Verdict.Intact(0, unchained)
        }

        var previous: Entry? = null
        chained.forEachIndexed { index, entry ->
            if (recompute(entry) != entry.hash) {
                return Verdict.Failed(Fault.ALTERED, entry.sequence, entry.id, index, unchained)
            }
            val before = previous
            if (before != null) {
                if (entry.sequence != before.sequence + 1) {
                    return Verdict.Failed(Fault.MISSING, entry.sequence, entry.id, index, unchained)
                }
                if (entry.previousHash != before.hash) {
                    return Verdict.Failed(
                        Fault.BROKEN_LINK, entry.sequence, entry.id, index, unchained,
                    )
                }
            }
            previous = entry
        }
        return Verdict.Intact(chained.size, unchained)
    }
}
