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

    /** One admission: who is doing it, to whom, and what has been produced. */
    data class AtTheGate(
        /** What the person doing the admitting is in this company. */
        val gateKeeperRole: Role,
        /** The account found by ID number. Blank means nobody was found. */
        val foundAccountId: String,
        /** They have signed, on this device, now. */
        val signed: Boolean,
        /** Already a current member of this company. */
        val alreadyIn: Boolean,
    )

    /**
     * Null when they may be admitted; otherwise why not.
     *
     * Ordered so the message is the useful one. Standing comes first, because
     * telling somebody with no business on the gate which field they left
     * empty invites them to fill it in. Then whether there is anybody to
     * admit, then whether they are already in, then the signature — which is
     * last because it is the only one the person at the gate can fix by doing
     * something, and it should be what the screen is still asking for once
     * everything else is settled.
     */
    fun blocksAdmission(gate: AtTheGate): Blocker? = when {
        !mayWorkTheGate(gate.gateKeeperRole) -> Blocker.NOT_ON_THE_GATE
        gate.foundAccountId.isBlank() -> Blocker.NOT_FOUND
        gate.alreadyIn -> Blocker.ALREADY_IN
        !gate.signed -> Blocker.NOT_SIGNED
        else -> null
    }

    enum class Blocker {
        /**
         * The person doing the admitting is not one of the people who may.
         *
         * See [mayWorkTheGate]. This is checked in the repository as well as
         * the screen, because a screen that is never shown is not a rule.
         */
        NOT_ON_THE_GATE,

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

    /**
     * Whether somebody may stand on the gate, read out of the lens grid
     * rather than kept as a second list of roles that would drift from it.
     *
     * Two things are needed and both are needed. Reading People is how the ID
     * number comes back as a face, without which the gate is a text field that
     * admits whoever was typed. Writing Evidence is where the signature goes:
     * an induction nobody recorded is one nobody can produce afterwards, which
     * is the only time anybody asks for it.
     *
     * That is the owner, the manager and the safety officer. HR is not on the
     * list, and that is the rule working rather than an omission — HR moves
     * people between roles from an office, and the point of the gate is that
     * the person is standing in front of you signing. HR can still put
     * somebody on the books the ordinary way; what they cannot do is produce a
     * signature on that person's behalf.
     */
    fun mayWorkTheGate(role: Role): Boolean =
        role.canRead(Lens.PEOPLE) && role.canWrite(Lens.EVIDENCE)
}
