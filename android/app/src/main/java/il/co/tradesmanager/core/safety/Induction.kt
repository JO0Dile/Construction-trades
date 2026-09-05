package il.co.tradesmanager.core.safety

import il.co.tradesmanager.core.access.Role
import il.co.tradesmanager.core.security.Signature

/**
 * The safety induction somebody reads and signs before the app opens at all.
 *
 * Not a screen somebody can skip and come back to. A person who has not been
 * inducted has no business recording work on a site, and an induction that can
 * be dismissed is a tick-box that protects nobody — least of all the person
 * dismissing it.
 *
 * Two levels, because two different jobs are being done. Somebody on the tools
 * needs to know what to wear and who to tell; somebody running the job needs
 * that and the rules they are about to enforce on everybody else. Giving a
 * bricklayer six screens of permit-to-work procedure is how inductions get
 * scrolled past without being read.
 */
object Induction {

    enum class Level {
        /** One page: what to wear, and who to tell when something is wrong. */
        WORKER,

        /** Everything above, plus the rules this person has to enforce. */
        SUPERVISOR,
    }

    /**
     * Which induction a role gets.
     *
     * Derived from [Role.canManageJobs] rather than from a second list of
     * roles kept beside the first one. There is one grid that says what a role
     * is for, and a parallel list would be the copy that drifted.
     */
    fun levelFor(role: Role): Level =
        if (role.canManageJobs) Level.SUPERVISOR else Level.WORKER

    /** Nobody gets past the door until they have signed one. */
    fun isRequired(inductedAt: Long?): Boolean = inductedAt == null

    /**
     * Whether what was drawn counts as having signed it.
     *
     * A real stroke, not a tap: the induction is the one record that says a
     * person was told, and a thumb resting on the glass has not been told
     * anything.
     */
    fun isSignatureAcceptable(signature: String?): Boolean = Signature.isSigned(signature)
}
