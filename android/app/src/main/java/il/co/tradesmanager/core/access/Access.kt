package il.co.tradesmanager.core.access

/**
 * The five questions this app answers about a job.
 *
 * Every feature is one of these, or it is a setting, a report, or a different
 * app — see docs/ROADMAP.md. Permissions are expressed in these terms rather
 * than per screen, which is why a role is five values instead of a list of
 * every button in the product.
 */
enum class Lens {
    /** What is supposed to happen: tasks, schedule, milestones, permits. */
    PLAN,

    /** What the job needs: materials, tools, deliveries, procurement. */
    STUFF,

    /** Who is on it: members, certifications, timesheets, subcontractors. */
    PEOPLE,

    /** What happened and whether it can be proved: photos, checklists, sign-offs. */
    EVIDENCE,

    /** What it cost and who owes what: budget, variations, invoices. */
    MONEY,
}

/**
 * Ordered on purpose: `access >= Access.READ` is the whole visibility test, and
 * `access == Access.WRITE` the whole editing test.
 */
enum class Access {
    NONE,
    READ,
    WRITE,
    ;

    val canRead: Boolean get() = this >= READ
    val canWrite: Boolean get() = this == WRITE
}

/**
 * What someone is on a site, in the terms a site actually uses.
 *
 * The grid below is the entire permission model. It is deliberately small
 * enough to read in one go, because a permission system nobody can hold in
 * their head is one that gets worked around.
 *
 * Read it down a column rather than across a row: [Lens.MONEY] has exactly one
 * writer and two readers, which is the property an auditor asks about.
 */
enum class Role(
    private val plan: Access,
    private val stuff: Access,
    private val people: Access,
    private val evidence: Access,
    private val money: Access,
    /** Can add, re-role and remove members. */
    val canManageMembers: Boolean = false,
    /** Can create and delete jobs — not the same as working on one. */
    val canManageJobs: Boolean = false,
) {
    /** The person whose company it is. Also what a personal account gets. */
    OWNER(
        plan = Access.WRITE,
        stuff = Access.WRITE,
        people = Access.WRITE,
        evidence = Access.WRITE,
        money = Access.WRITE,
        canManageMembers = true,
        canManageJobs = true,
    ),

    /** Runs the jobs. Sees what things cost; does not sign the cheques. */
    MANAGER(
        plan = Access.WRITE,
        stuff = Access.WRITE,
        people = Access.READ,
        evidence = Access.WRITE,
        money = Access.READ,
        canManageJobs = true,
    ),

    /** The money work. Everywhere in Money, nowhere in Plan. */
    FINANCE(
        plan = Access.NONE,
        stuff = Access.READ,
        people = Access.NONE,
        evidence = Access.READ,
        money = Access.WRITE,
    ),

    /** People and their tickets. Reads Evidence because sign-offs name people. */
    HR(
        plan = Access.NONE,
        stuff = Access.NONE,
        people = Access.WRITE,
        evidence = Access.READ,
        money = Access.NONE,
        canManageMembers = true,
    ),

    /** On the tools. Does the work and records it; cannot delete the job. */
    WORKER(
        plan = Access.WRITE,
        stuff = Access.WRITE,
        people = Access.NONE,
        evidence = Access.WRITE,
        money = Access.NONE,
    ),
    ;

    fun access(lens: Lens): Access = when (lens) {
        Lens.PLAN -> plan
        Lens.STUFF -> stuff
        Lens.PEOPLE -> people
        Lens.EVIDENCE -> evidence
        Lens.MONEY -> money
    }

    fun canRead(lens: Lens): Boolean = access(lens).canRead

    fun canWrite(lens: Lens): Boolean = access(lens).canWrite

    /** Every lens this role may open, for building a navigation bar. */
    fun readableLenses(): Set<Lens> = Lens.entries.filter { canRead(it) }.toSet()

    companion object {
        /**
         * Roles are stored as strings so a future role costs no migration. An
         * unrecognised one falls back to the least privilege there is, never to
         * the most — a corrupted row must not become an administrator.
         */
        fun parse(value: String?): Role =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: WORKER

        /** Roles an owner can hand out. Nobody assigns a second owner by accident. */
        val assignable: List<Role> = listOf(MANAGER, FINANCE, HR, WORKER)
    }
}
