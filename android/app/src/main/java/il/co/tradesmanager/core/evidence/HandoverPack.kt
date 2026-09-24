package il.co.tradesmanager.core.evidence

/**
 * Whether a job is finished, across everything the app has been recording.
 *
 * The registers each answer their own question — is this scaffold safe, has
 * that permit been signed back, did the props come out — and nobody can see all
 * of them at once. At handover that is exactly the question: what is still
 * open, anywhere on this job.
 *
 * The pack does **not** refuse to print while things are outstanding. An
 * interim pack is a real thing: a client asks for the file at the end of a
 * phase, or a subcontractor leaves and wants their part of it. What it does
 * instead is record what was outstanding at the moment it was produced, so an
 * interim pack reads as interim rather than looking final. A document that
 * quietly omits the eleven permits nobody closed is worse than no document,
 * because somebody will file it and believe it.
 */
object HandoverPack {

    /**
     * A kind of thing that can still be open.
     *
     * Each one is a register that answers its own question elsewhere. Gathered
     * here only to be counted, which is why this is an enum of kinds rather
     * than anything that knows what a scaffold is.
     */
    enum class Item {
        /** Defects somebody marked as holding up handover. */
        BLOCKING_SNAGS,

        /** Issued and never signed back. */
        OPEN_PERMITS,

        /** Still standing, and still needing a weekly inspection. */
        SCAFFOLDS_STANDING,

        /** Propping and formwork not yet struck. */
        TEMPORARY_WORKS_STANDING,

        /** Trenches not backfilled. */
        EXCAVATIONS_OPEN,

        /** Lift plans raised and never marked done. */
        LIFTS_INCOMPLETE,

        /** Pours started and never finished. */
        POURS_UNFINISHED,

        /**
         * Pours with a twenty-eight-day cube result under the strength the
         * mix asks for. Not a verdict on the structure -- that is the
         * engineer's -- but a handover file with an unanswered low result
         * in it is not finished.
         */
        CUBES_FOR_ENGINEER,

        /** Finished pours with no twenty-eight-day cube result recorded yet. */
        POURS_WITHOUT_28_DAY_RESULT,

        /** Days somebody wrote up and never signed. */
        UNSIGNED_DAILY_LOGS,

        /** Questions put to the designers and never answered. */
        QUERIES_UNANSWERED,

        /** Inspections still waiting, overdue, or failed and not asked again. */
        INSPECTIONS_OUTSTANDING,

        /** Pours with no passed inspection of the steel or the forms set against them. */
        POURS_WITHOUT_INSPECTION,

        /** Materials still waiting for an answer, or rejected and not sent again. */
        SUBMITTALS_OUTSTANDING,

        /**
         * Loads of waste with neither a ticket number nor a photograph of
         * the ticket: nothing yet shows where they went.
         */
        WASTE_WITHOUT_TICKET,
    }

    data class Outstanding(val item: Item, val count: Int)

    /**
     * What is still open, and whether that is nothing.
     *
     * [outstanding] keeps the enum's own order rather than sorting by count.
     * The order is roughly how much each matters at handover — a blocking snag
     * before an unsigned log — and sorting by size would put twenty unsigned
     * logs above one scaffold still standing in the street.
     */
    data class Readiness(val outstanding: List<Outstanding>) {

        val isComplete: Boolean get() = outstanding.isEmpty()

        /** How many things, not how many kinds. */
        val total: Int get() = outstanding.sumOf { it.count }
    }

    /**
     * Reads a count per kind into a readiness.
     *
     * Zero and absent both mean nothing outstanding, and neither appears in the
     * result: a pack that lists "0 open permits" alongside "3 blocking snags"
     * buries the three in a wall of zeroes. Negative counts are treated as
     * zero — they can only come from a query that has gone wrong, and a
     * negative would otherwise cancel out something real in [total].
     */
    fun readiness(counts: Map<Item, Int>): Readiness = Readiness(
        Item.entries.mapNotNull { item ->
            val count = counts[item] ?: 0
            if (count > 0) Outstanding(item, count) else null
        },
    )
}
