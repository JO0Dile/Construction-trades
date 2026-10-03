package il.co.tradesmanager.core.safety

/**
 * A safety violation written against a named person.
 *
 * This is an accusation with a cost attached, kept under somebody's name. It
 * gets two states before it counts, on purpose: an officer walking a site
 * writes it down while it is in front of them, and confirms it afterwards
 * having checked they have the right person. Until they confirm, they can
 * throw it away and nobody is ever told.
 *
 * After confirmation it stops being editable. A record that could be revised
 * once the person has seen it is not a record of anything — the same reason
 * an approved work package cannot be re-priced and an audit entry cannot be
 * rewritten.
 */
object Violations {

    enum class Status {
        /** Written, not yet stood behind. Can be dropped without trace. */
        DRAFT,

        /** Stood behind. Immutable from here. */
        CONFIRMED,

        /** Dropped before confirming. Kept so a pattern of them is visible. */
        CANCELLED,
    }

    /** Everything a draft needs before anybody can be accused with it. */
    data class Draft(
        val status: Status,
        /** The account it is written against. Blank means nobody yet. */
        val againstAccountId: String,
        /** How many photographs or videos are attached. */
        val evidenceCount: Int,
        val description: String,
    )

    /**
     * Cancelling is free while it is a draft and impossible afterwards.
     *
     * The officer asked for exactly this: cancel any time before confirming.
     * The other half of that sentence is the part that matters — after
     * confirming, not at all.
     */
    fun canCancel(status: Status): Boolean = status == Status.DRAFT

    /**
     * What stops a violation being confirmed.
     *
     * Null when it may go ahead. A reason rather than a boolean because the
     * screen has to say which thing is missing; "Confirm" greyed out with no
     * explanation is how somebody decides the app is broken.
     */
    fun blocksConfirming(draft: Draft): Blocker? = when {
        draft.status != Status.DRAFT -> Blocker.NOT_A_DRAFT
        draft.againstAccountId.isBlank() -> Blocker.NOBODY_NAMED
        draft.evidenceCount <= 0 -> Blocker.NO_EVIDENCE
        draft.description.isBlank() -> Blocker.NOT_DESCRIBED
        else -> null
    }

    enum class Blocker {
        /** Already confirmed or already cancelled. */
        NOT_A_DRAFT,

        /** No person chosen. An unattributed violation accuses the whole site. */
        NOBODY_NAMED,

        /**
         * No photograph or video.
         *
         * Required, not encouraged. A violation with a cost against somebody's
         * name and nothing to show for it is one person's word, and the person
         * it is written against has no way to argue with it.
         */
        NO_EVIDENCE,

        /** Nothing written down. A photograph alone does not say what was wrong. */
        NOT_DESCRIBED,
    }

    fun canConfirm(draft: Draft): Boolean = blocksConfirming(draft) == null

    /**
     * A cost may be absent, and absent is not zero.
     *
     * Not every violation carries a charge. Zero says "this cost nothing",
     * null says "no charge was put on this", and a register that collapsed
     * the two would let somebody argue either way afterwards.
     */
    fun isChargeable(cost: Double?): Boolean = cost != null && cost > 0.0

    /** Rejects a negative charge, which is a credit and is not what this is. */
    fun acceptableCost(cost: Double?): Boolean = cost == null || cost >= 0.0
}
