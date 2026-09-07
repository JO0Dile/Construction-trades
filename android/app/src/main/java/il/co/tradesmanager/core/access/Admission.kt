package il.co.tradesmanager.core.access

/**
 * Getting through the gate, which is not the same as being given a rank.
 *
 * On a site you meet the person who signs you in before you meet anything
 * else. They look you up by ID number, see your face come back, add you, and
 * you sign there and then for safety. That is the whole of what they do.
 *
 * They do not make you a boss. Rank comes from the firm that engaged you —
 * the one you took the work from puts you under them as a crew leader or a
 * foreman or whatever it is — and that is a separate act by a separate person
 * who has a commercial relationship with you. Conflating the two would let
 * whoever happens to be on the gate promote people, which is not what the gate
 * is for and not what anybody would expect.
 *
 * So admission always grants [Role.onAdmission] and this file exists to say
 * that in one place, with a test, rather than as a value typed at a call site
 * where a later edit could quietly change it.
 */
object Admission {

    /** What a person must have produced before the gate can admit them. */
    data class AtTheGate(
        /** The account found by ID number. Blank means nobody was found. */
        val foundAccountId: String,
        /** They have signed, on this device, now. */
        val signed: Boolean,
        /** Already a current member of this company. */
        val alreadyIn: Boolean,
    )

    /** Null when they may be admitted; otherwise why not. */
    fun blocksAdmission(gate: AtTheGate): Blocker? = when {
        gate.foundAccountId.isBlank() -> Blocker.NOT_FOUND
        gate.alreadyIn -> Blocker.ALREADY_IN
        !gate.signed -> Blocker.NOT_SIGNED
        else -> null
    }

    enum class Blocker {
        /**
         * No account for that ID number.
         *
         * The gate cannot invent one. Somebody who has never used the app has
         * to sign up first — the alternative is a stranger's ID number
         * creating an account that person never agreed to.
         */
        NOT_FOUND,

        /** Already a member. Admitting twice would give them two memberships. */
        ALREADY_IN,

        /**
         * They have not signed.
         *
         * The signature is the point of standing at the gate rather than doing
         * this from an office: it is the person in front of you accepting the
         * safety terms, and it is what the record rests on afterwards.
         */
        NOT_SIGNED,
    }

    fun canAdmit(gate: AtTheGate): Boolean = blocksAdmission(gate) == null

    /**
     * The role the gate grants. Always the same one.
     *
     * Takes no arguments deliberately. Every version of this that accepted a
     * role would be one edit away from letting the gate hand out any of them.
     */
    fun grantedRole(): Role = Role.onAdmission

    /**
     * Whether [role] is something the gate may hand out. Only ever the one.
     *
     * Here so a screen can assert it rather than trust itself, and so the
     * rule fails a test rather than a review if anybody widens it.
     */
    fun gateMayGrant(role: Role): Boolean = role == Role.onAdmission
}
