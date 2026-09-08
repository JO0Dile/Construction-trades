package il.co.tradesmanager.core.access

/**
 * What changed, and who is entitled to hear about it.
 *
 * The audit trail already records every change: what kind of thing, which one,
 * what happened to it, who did it. What it could not do is be read by anybody
 * except an administrator, because the log is one undifferentiated list and
 * some of it is nobody's business.
 *
 * The rule is one line:
 *
 * > **A change is shown to somebody only if they may read the lens it belongs
 * > to.**
 *
 * So a labourer sees the programme move and the drawing replaced, and does not
 * see a cost line, a contract or another person's record. It is the same lens
 * grid the navigation bar is built from, asked about a change instead of a
 * screen, which is why there is no second list of permissions to keep in step.
 *
 * ## What this is not
 *
 * It is not delivery. On one device, a change is visible the moment it is
 * written, and this decides what a person is shown when they look. Reaching
 * somebody else's phone needs the server — see `docs/SERVER.md` — and when
 * that exists this same function is what decides whose feed a change goes
 * into, rather than a second rule written server-side that drifts from this
 * one.
 */
object Changes {

    /**
     * Which lens each kind of change belongs to.
     *
     * A type not in here is shown to nobody. That is the safe direction and
     * `tools/check-audit-lens.py` makes it a loud one: adding a register
     * without classifying it fails CI rather than shipping a feed with a
     * silent hole in it.
     */
    val LENS_OF: Map<String, Lens> = mapOf(
        // What is supposed to happen.
        "project" to Lens.PLAN,
        "project_task" to Lens.PLAN,
        "task_block" to Lens.PLAN,
        "assignment" to Lens.PLAN,
        "engagement" to Lens.PLAN,

        // What the job needs.
        "inventory_item" to Lens.STUFF,
        "project_material" to Lens.STUFF,
        "purchase_order" to Lens.STUFF,
        "purchase_order_line" to Lens.STUFF,
        "equipment" to Lens.STUFF,
        "trade" to Lens.STUFF,
        "catalogue" to Lens.STUFF,

        // Who is on it.
        "account" to Lens.PEOPLE,
        "membership" to Lens.PEOPLE,
        "certification" to Lens.PEOPLE,
        "company" to Lens.PEOPLE,

        // What happened and whether it can be proved.
        "photo" to Lens.EVIDENCE,
        "checklist_run" to Lens.EVIDENCE,
        "incident" to Lens.EVIDENCE,
        "violation" to Lens.EVIDENCE,
        "snag" to Lens.EVIDENCE,
        "toolbox_talk" to Lens.EVIDENCE,
        "permit" to Lens.EVIDENCE,
        "daily_log" to Lens.EVIDENCE,
        "concrete_pour" to Lens.EVIDENCE,
        "concrete_ticket" to Lens.EVIDENCE,
        "scaffold" to Lens.EVIDENCE,
        "lift_plan" to Lens.EVIDENCE,
        "excavation" to Lens.EVIDENCE,
        "temporary_works" to Lens.EVIDENCE,

        // What it cost and who owes what.
        "job_budget" to Lens.MONEY,
        "cost_entry" to Lens.MONEY,
        "variation" to Lens.MONEY,
        "invoice" to Lens.MONEY,
        "contract" to Lens.MONEY,
        "contract_amendment" to Lens.MONEY,
        "payment_application" to Lens.MONEY,

        // Hours are Money and not People, deliberately. A shift is somebody's
        // wages waiting to be worked out, and what a person minds about is not
        // who clocked in but what it came to. Chain narrows it further: the
        // lens says a role may read hours at all and says nothing about whose.
        "time_entry" to Lens.MONEY,

        // A copy of every ID number and wage in the firm leaving the device,
        // or coming back onto it. Filed under People because that is what it
        // is a copy of, and because the people who manage people are the ones
        // who should see that it happened.
        "backup" to Lens.PEOPLE,

        // The trail's own housekeeping, filed under People rather than hidden.
        // A purge entry says how many records were deleted and by whom, which
        // is exactly what somebody would want to see and exactly what whoever
        // ran it might prefer they did not.
        "audit_log" to Lens.PEOPLE,
    )

    /** The lens a change belongs to, or null if nothing has classified it. */
    fun lensOf(entityType: String): Lens? = LENS_OF[entityType]

    /**
     * Whether [role] may be shown a change to [entityType].
     *
     * An unclassified type is refused. Somebody has to decide what a new kind
     * of record is before it starts appearing in people's feeds, and the
     * decision belongs in the map above rather than in whatever a default
     * happened to be.
     */
    fun visibleTo(role: Role, entityType: String): Boolean =
        lensOf(entityType)?.let(role::canRead) == true

    /**
     * Filters a batch of changes for one person.
     *
     * Takes the type off each entry rather than a pre-filtered list, so a
     * caller cannot forget to filter: the shape that comes back is the shape
     * that goes on screen.
     */
    fun <T> visibleTo(role: Role, entries: List<T>, entityType: (T) -> String): List<T> =
        entries.filter { visibleTo(role, entityType(it)) }
}
