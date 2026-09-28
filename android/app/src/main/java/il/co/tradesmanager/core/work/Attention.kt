package il.co.tradesmanager.core.work

/**
 * What on a job is waiting on somebody, gathered from its registers.
 *
 * The first thing a site manager opening a job wants is not the task list but
 * the short answer to "what is going wrong": the steel that failed and has
 * not been asked for again, the tile that was rejected, the question to the
 * engineer past its day, the delay nobody gave notice of. Each register
 * already knows its own; this puts them in one place, in the order they
 * matter, and leaves out whatever is fine.
 *
 * Nothing is counted here. The counts come from each register's own rule --
 * Inspections.state, Submittals.state, Queries.state, Risks.state,
 * Complaints.state, Substances.state, FirePoints.state -- so this cannot
 * disagree with the screen it points to.
 */
object Attention {

    /** In the order they are shown: what can hurt somebody first, then what holds the work, then paperwork. */
    enum class Item {
        RISKS_EXTREME,
        SUBSTANCES_WITHOUT_SHEET,
        FIRE_POINTS,
        INSPECTIONS_FAILED,
        INSPECTIONS_OVERDUE,
        MATERIALS_REJECTED,
        MATERIALS_OVERDUE,
        QUERIES_OVERDUE,
        DELAYS_WITHOUT_NOTICE,
        DELAYS_RUNNING,
        COMPLAINTS_WAITING,
        RISK_REVIEWS_OVERDUE,
    }

    data class Line(val item: Item, val count: Int)

    /** The lines worth showing: nothing for a zero, and the enum's own order. */
    fun lines(counts: Map<Item, Int>): List<Line> =
        Item.entries.mapNotNull { item -> counts[item]?.takeIf { it > 0 }?.let { Line(item, it) } }
}
