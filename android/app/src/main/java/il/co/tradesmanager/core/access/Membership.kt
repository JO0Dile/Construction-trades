package il.co.tradesmanager.core.access

/**
 * One person's place in one company.
 *
 * A tradesperson is on this site today and another tomorrow, and next month
 * for a firm that did not exist when they signed up. So identity and place are
 * two different things: who somebody *is* — their ID number, their photograph,
 * the induction they signed — belongs to the person and follows them
 * everywhere. What they may *do* belongs to the membership, because the same
 * person can be a site manager for one firm and on the tools for another, and
 * neither answer is wrong.
 *
 * A null [companyId] is the membership somebody has to themselves: their own
 * tools, their own jobs, nobody else's permissions to think about.
 */
data class Membership(
    val id: String,
    val companyId: String?,
    val role: Role,
    val joinedAt: Long,
    /** When they came off the books. Null while they are still on them. */
    val leftAt: Long? = null,
) {
    val isCurrent: Boolean get() = leftAt == null
}

/**
 * Which membership is in force, and what it allows.
 *
 * Every permission question in the app ends up here. There is exactly one
 * definition of "which company am I working in right now", and everything
 * else — the lenses, the jobs on screen, whose stock it is — reads it rather
 * than working it out again.
 */
object Memberships {

    /**
     * The membership in force.
     *
     * [preferredCompanyId] is what the person last chose. It only wins if it
     * is still current: being taken off a firm's books has to stop that firm's
     * work appearing, and a stale preference in a settings file is not a
     * reason to keep showing it.
     *
     * Otherwise the most recently joined current membership, which is almost
     * always the one somebody means — you join a site because you are about to
     * work on it.
     */
    fun active(memberships: List<Membership>, preferredCompanyId: String?): Membership? {
        val current = memberships.filter { it.isCurrent }
        if (current.isEmpty()) return null
        return current.firstOrNull { it.companyId == preferredCompanyId }
            ?: current.maxByOrNull { it.joinedAt }
    }

    /**
     * What the person may do right now.
     *
     * Least privilege when there is no current membership anywhere: somebody
     * taken off every firm's books keeps their account and their induction,
     * and sees an app with nothing in it — which is the correct amount.
     */
    fun roleFor(memberships: List<Membership>, preferredCompanyId: String?): Role =
        active(memberships, preferredCompanyId)?.role ?: Role.WORKER

    /** The companies somebody can switch between, newest first. */
    fun switchable(memberships: List<Membership>): List<Membership> =
        memberships.filter { it.isCurrent }.sortedByDescending { it.joinedAt }

    /**
     * True when removing or demoting this membership would leave a company
     * with nobody able to administer it.
     *
     * The check is per company, not per person: being the last owner of one
     * firm says nothing about your standing in another, and the old
     * account-wide version could not tell the difference. A personal
     * membership cannot strand anybody, so it is always free to change.
     */
    fun wouldStrandCompany(
        memberships: List<Membership>,
        target: Membership,
        newRole: Role?,
    ): Boolean {
        if (target.companyId == null) return false
        if (target.role != Role.OWNER || !target.isCurrent) return false
        if (newRole == Role.OWNER) return false
        return memberships.count {
            it.isCurrent && it.companyId == target.companyId && it.role == Role.OWNER
        } <= 1
    }
}
