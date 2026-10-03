package il.co.tradesmanager.core.time

/**
 * Which block of the day a shift belongs to.
 *
 * `TimeEntryEntity.blockId` has existed since the schedule was built and
 * nothing has ever written to it, so no shift has ever been tied to the piece
 * of work it was planned as. The job is recorded — that half was fixed when
 * the check-in stopped passing a null project — but "eight hours on the
 * tower" and "eight hours on the third-floor slab, which was planned as six"
 * are different sentences, and only the second one tells anybody anything.
 *
 * The rule lives here rather than in the repository because it is the part
 * that can be wrong in a way nobody notices: a shift attributed to the wrong
 * block is worse than one attributed to none, and a rule that can only be
 * exercised through a database is a rule nobody exercises.
 *
 * Nothing here asks the worker anything. A man walking onto a site at ten to
 * seven with his gloves on will not answer a second question, and a check-in
 * that takes two taps is a check-in people stop doing.
 */
object DayPlan {

    /** One block on a day's plan, in the terms this rule needs. */
    data class Block(
        val id: String,
        val projectId: String?,
        val assigneeId: String?,
        /** Minutes past midnight; see TaskBlockEntity for why not a timestamp. */
        val startMinute: Int,
        val endMinute: Int,
    )

    /**
     * The block a shift starting now belongs to, or null when nothing fits.
     *
     * Null is a perfectly good answer and the common one. A labourer with no
     * block on the plan, somebody working a Saturday, a shift on a job the
     * plan says nothing about — all of those are shifts with a job and no
     * block, which is what the column has always held and is honest.
     *
     * The conditions, and why each one is there:
     *
     *  * **It has to be theirs.** An unassigned block is the plan saying what
     *    should happen, not who does it. Attaching a shift to it would put a
     *    name on somebody else's work.
     *  * **It has to be the same job**, when the shift names one. The whole
     *    point of recording the job was that the labour arithmetic is per
     *    job, and a block on a different site would undo that quietly.
     *  * **A block already running wins over one still to come.** Somebody
     *    who starts at 09:30 on a block that runs 08:00 to 12:00 is late for
     *    it, not early for the afternoon.
     *  * **Two blocks running at once is a double-booking**, and guessing
     *    between them is how a shift lands on the wrong one. The later start
     *    wins because it is the thing they have just moved onto; ties break
     *    on id so the same day always gives the same answer.
     */
    fun blockForShift(
        blocks: List<Block>,
        workerId: String?,
        projectId: String?,
        minuteOfDay: Int,
    ): String? {
        if (workerId.isNullOrBlank()) return null

        val theirs = blocks.filter { block ->
            block.assigneeId == workerId &&
                (projectId == null || block.projectId == projectId)
        }
        if (theirs.isEmpty()) return null

        val running = theirs.filter { minuteOfDay >= it.startMinute && minuteOfDay < it.endMinute }
        if (running.isNotEmpty()) {
            return running.maxWithOrNull(compareBy({ it.startMinute }, { it.id }))?.id
        }

        // Arrived before the first thing they are down for. Anything still to
        // come today is a better answer than none; the earliest of them is the
        // one they are early for.
        val upcoming = theirs.filter { it.startMinute > minuteOfDay }
        return upcoming.minWithOrNull(compareBy({ it.startMinute }, { it.id }))?.id
    }
}
