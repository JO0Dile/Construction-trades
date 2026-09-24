package il.co.tradesmanager.core.access

/**
 * Who may be told what a person is paid.
 *
 * The rule is one line and everything else here follows from it:
 *
 * > **A person's own money is theirs, and otherwise may be shown only to
 * > somebody above them in the chain of command.**
 *
 * Not "a labourer must not see the foreman's rate" — that is a consequence,
 * not the rule. Written this way, a labourer cannot see a foreman's pay
 * because the foreman is not below them; a foreman cannot see the site
 * manager's for the same reason; two foremen on the same job see nothing of
 * each other because neither is above the other; and it keeps working when a
 * firm reorganises, without a new clause for each new shape.
 *
 * This is the person-level twin of [Commercial], which answers the same
 * question between firms. Both live in `core` so that the phone and the
 * server run the identical function rather than two that disagree, and it is
 * the server's answer that matters: hiding a field the API already sent is
 * not confidentiality, it is a stylesheet. See docs/SERVER.md.
 *
 * The chain is inside one company. Somebody who is a foreman for one firm and
 * on the tools for another has two memberships and two positions, and neither
 * says anything about the other.
 */
object Chain {

    /** One person's place: their membership, and whose crew they are on. */
    data class Link(
        val membershipId: String,
        /**
         * The membership they answer to, or null for somebody at the top.
         *
         * Null is common and not a gap. Everybody starts with it: the gate
         * admits people and does not rank them, and rank is set afterwards by
         * whoever engaged them. See [Admission].
         */
        val reportsTo: String?,
    )

    /**
     * One firm's chain of command.
     *
     * [owners] is not a shortcut around the rule, it is the top of it. A
     * membership with no [Link.reportsTo] answers to the people whose company
     * it is, which is what "until we reach the owner of the company" means
     * and what is true of a firm whether or not anybody has drawn the chart.
     * Without it, a company that has not filled in a single reporting line
     * would hide its own payroll from the person who signs it.
     */
    data class Company(
        val links: List<Link>,
        val owners: Set<String> = emptySet(),
    )

    /**
     * How far up this will walk before it decides the data is broken.
     *
     * A chain is a management structure, not a linked list: thirty deep is
     * already absurd for a construction firm. The cycle guard below is what
     * actually stops a loop; this is the second lock, because a rule that
     * decides who sees payroll must terminate even on data nobody predicted.
     */
    const val MAX_DEPTH = 32

    /**
     * Everybody above [membershipId], nearest first.
     *
     * Cycle-safe. Bad data can point A at B and B back at A — a reorganisation
     * applied twice, a restore from two devices — and a walk that trusted the
     * data would hang on the screen that decides whether to show somebody's
     * wages.
     */
    fun ancestors(company: Company, membershipId: String): List<String> {
        if (membershipId.isBlank()) return emptyList()
        val bossOf = company.links.associate { it.membershipId to it.reportsTo }
        val found = LinkedHashSet<String>()
        var at = membershipId
        var steps = 0
        while (steps < MAX_DEPTH) {
            steps++
            val next = bossOf[at]
            if (next == null) {
                // The top of a branch answers to whoever owns the firm.
                company.owners.filterNot { it == membershipId }.forEach(found::add)
                break
            }
            if (next == membershipId || !found.add(next)) break
            at = next
        }
        return found.toList()
    }

    /** Whether [viewer] stands above [subject]. Nobody is above themselves. */
    fun isAbove(company: Company, viewer: String, subject: String): Boolean {
        if (viewer.isBlank() || subject.isBlank() || viewer == subject) return false
        return viewer in ancestors(company, subject)
    }

    /**
     * Whether [viewer] may be sent what [subject] is paid.
     *
     * Two conditions and both are needed for anybody else's record. The lens
     * grid says whether this role touches money at all; the chain says whose.
     * A role with the Money lens still sees only downwards — a payroll clerk
     * who is meant to see the whole firm is placed above it in the chain, not
     * exempted from the rule, because an exemption is a thing somebody has to
     * remember and a position is a thing that shows on the chart.
     *
     * A person's own record is theirs whatever their role. A labourer has no
     * Money lens at all and must still be able to see their own hours and
     * what they came to; anything else is an app that will not tell somebody
     * what they earned.
     *
     * Blank ids are nobody, and nobody sees anything. That matters more than
     * it reads: an unauthenticated request arrives with no membership, and a
     * rule that treated blank as a wildcard would answer it in full.
     */
    fun maySeePay(
        company: Company,
        viewerMembershipId: String,
        viewerRole: Role,
        subjectMembershipId: String,
    ): Boolean {
        if (viewerMembershipId.isBlank() || subjectMembershipId.isBlank()) return false
        if (viewerMembershipId == subjectMembershipId) return true
        return viewerRole.canRead(Lens.MONEY) &&
            isAbove(company, viewerMembershipId, subjectMembershipId)
    }

    /** Everybody [viewer] may be shown the pay of, themselves included. */
    fun payVisibleTo(
        company: Company,
        viewerMembershipId: String,
        viewerRole: Role,
    ): Set<String> = company.links
        .map { it.membershipId }
        .filter { maySeePay(company, viewerMembershipId, viewerRole, it) }
        .toSet()

    /* ------------------------------------------------- changing the chain */

    /**
     * Null when [subject] may be placed under [newBoss]; otherwise why not.
     *
     * Takes no role, deliberately. Arranging the chain is a matter of position
     * and not of rank: you may arrange the part of it that is below you, and
     * that is the whole permission. A version of this that also asked
     * [Role.canManageMembers] read plausibly and was wrong in both directions
     * — it stopped a foreman putting their own labourer on their own crew,
     * which is the ordinary act this exists for, and it would have let an
     * office role rearrange a branch it has never been near.
     *
     * The owners are the exception only because they are the top: on the first
     * morning nobody is above anybody, and somebody has to be able to draw the
     * first line.
     */
    fun blocksReporting(
        company: Company,
        actorMembershipId: String,
        subjectMembershipId: String,
        newBossMembershipId: String?,
    ): Blocker? {
        val known = company.links.map { it.membershipId }.toSet()
        return when {
            actorMembershipId.isBlank() || subjectMembershipId.isBlank() -> Blocker.UNKNOWN
            subjectMembershipId !in known -> Blocker.UNKNOWN
            newBossMembershipId != null && newBossMembershipId !in known -> Blocker.UNKNOWN
            subjectMembershipId == actorMembershipId -> Blocker.YOURSELF
            newBossMembershipId == subjectMembershipId -> Blocker.THEMSELVES
            // Only over your own crew. Rearranging the branch you answer to is
            // how a person makes their own manager report to them and reads
            // their pay the next morning.
            !mayArrange(company, actorMembershipId, subjectMembershipId) -> Blocker.NOT_YOURS
            newBossMembershipId != null &&
                wouldLoop(company, subjectMembershipId, newBossMembershipId) -> Blocker.A_LOOP
            else -> null
        }
    }

    /**
     * Whether [actor] may move [subject] at all.
     *
     * Exposed so a screen can decide what to offer without re-deriving the
     * rule, and so that what it offers and what the repository allows come
     * from the same function rather than two that drift.
     */
    fun mayArrange(company: Company, actorMembershipId: String, subjectMembershipId: String):
        Boolean = actorMembershipId.isNotBlank() &&
        actorMembershipId != subjectMembershipId &&
        (
            isAbove(company, actorMembershipId, subjectMembershipId) ||
                actorMembershipId in company.owners
            )

    enum class Blocker {
        /** No such membership in this company. */
        UNKNOWN,

        /** Not your crew, or not yours to arrange at all. */
        NOT_YOURS,

        /** You cannot move yourself. Somebody above you does that. */
        YOURSELF,

        /** Nobody reports to themselves. */
        THEMSELVES,

        /**
         * The move would make a circle.
         *
         * Refused rather than tolerated. [ancestors] survives a loop, but a
         * chain with one in it has people who answer to nobody and a payroll
         * nobody above them can read.
         */
        A_LOOP,
    }

    /** Whether putting [subject] under [newBoss] would make a circle. */
    fun wouldLoop(company: Company, subject: String, newBoss: String): Boolean =
        newBoss == subject || subject in ancestors(company, newBoss)
}
