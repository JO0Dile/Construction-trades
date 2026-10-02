package il.co.tradesmanager.core.safety

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The company's safety record as rates rather than counts: how many people
 * were hurt for every million hours worked, across every job.
 *
 * Three injuries means one thing on a job of four men and another on a job of
 * two hundred. The figure a client's safety department, a tender or an
 * insurer asks for is the rate, and until now it had to be worked out by hand
 * from a timesheet and an incident book that were both already in the app.
 *
 * The hours come from check-ins and check-outs, the injuries from the
 * incident register, so neither can be typed in to make a better number. The
 * rate per million hours is a common industry measure; nothing here claims it
 * is the definition any particular regulation uses, and a client who wants
 * another base (two hundred thousand hours is also common) can divide.
 */
object SafetyStats {

    enum class Period { THIS_MONTH, LAST_MONTH, THIS_QUARTER, THIS_YEAR, LAST_12_MONTHS }

    /** The days a period covers, [first] up to and including [last]. */
    data class Span(val first: LocalDate, val last: LocalDate)

    /**
     * A period that is still running ends today: "this year" in October is
     * January to today, not to December, so the hours and the injuries are
     * counted over the same days.
     */
    fun span(period: Period, today: LocalDate): Span = when (period) {
        Period.THIS_MONTH -> Span(today.withDayOfMonth(1), today)
        Period.LAST_MONTH -> today.withDayOfMonth(1).minusMonths(1).let { Span(it, it.plusMonths(1).minusDays(1)) }
        Period.THIS_QUARTER -> Span(LocalDate.of(today.year, (today.monthValue - 1) / MONTHS_PER_QUARTER * MONTHS_PER_QUARTER + 1, 1), today)
        Period.THIS_YEAR -> Span(today.withDayOfYear(1), today)
        Period.LAST_12_MONTHS -> Span(today.minusYears(1).plusDays(1), today)
    }

    /** From the first day's first moment up to, not including, the moment after the last day, on the site's clock. */
    fun window(span: Span, zone: ZoneId): LongRange {
        val start = span.first.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = span.last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start until end
    }

    /**
     * How much of one finished shift falls inside [window].
     *
     * A shift that crosses midnight into the period counts only its part
     * inside. A shift longer than [Muster.STALE_AFTER_MS] -- the same line the
     * roll call draws -- is almost always somebody who never checked out, and
     * counting the night and the weekend as work would make every rate look
     * better than it is; it counts only that long.
     */
    fun countedMillis(checkInAt: Long, checkOutAt: Long, window: LongRange): Long {
        if (checkOutAt <= checkInAt) return 0L
        val end = minOf(checkOutAt, checkInAt + Muster.STALE_AFTER_MS)
        val from = maxOf(checkInAt, window.first)
        val to = minOf(end, window.last + 1)
        return (to - from).coerceAtLeast(0L)
    }

    /** Longer than any real shift: counted short, and said so on the page. */
    fun looksForgotten(checkInAt: Long, checkOutAt: Long): Boolean = checkOutAt - checkInAt > Muster.STALE_AFTER_MS

    data class Figures(
        val workedMillis: Long = 0L,
        /** Finished shifts with any time inside the period. */
        val shifts: Int = 0,
        /** Of those, the ones counted short as a forgotten check-out. */
        val shiftsCapped: Int = 0,
        val nearMisses: Int = 0,
        /** First aid on site and back to work. */
        val minorInjuries: Int = 0,
        /** Taken to hospital, or off work beyond the day. */
        val seriousInjuries: Int = 0,
        val fatalities: Int = 0,
        /** Recorded and not cancelled since. */
        val violations: Int = 0,
        val talksHeld: Int = 0,
    ) {
        val hoursWorked: Double get() = workedMillis / MILLIS_PER_HOUR

        /** Everybody hurt, from first aid up: every incident that is not a near miss. */
        val injuries: Int get() = minorInjuries + seriousInjuries + fatalities

        /** Injuries per million hours, or null with no hours to divide by. */
        val injuryRate: Double? get() = perMillionHours(injuries)

        /** Serious injuries and deaths per million hours, or null with no hours. */
        val seriousRate: Double? get() = perMillionHours(seriousInjuries + fatalities)

        /**
         * Near misses written down for each injury, or null with no injuries.
         *
         * Higher is better, which surprises people: a site that writes its near
         * misses down is a site that notices them before they become injuries.
         */
        val nearMissesPerInjury: Double? get() = if (injuries == 0) null else nearMisses.toDouble() / injuries

        /** Nothing worked and nothing recorded: a job that was not running. */
        val isEmpty: Boolean get() = this == Figures()

        operator fun plus(other: Figures): Figures = Figures(
            workedMillis = workedMillis + other.workedMillis,
            shifts = shifts + other.shifts,
            shiftsCapped = shiftsCapped + other.shiftsCapped,
            nearMisses = nearMisses + other.nearMisses,
            minorInjuries = minorInjuries + other.minorInjuries,
            seriousInjuries = seriousInjuries + other.seriousInjuries,
            fatalities = fatalities + other.fatalities,
            violations = violations + other.violations,
            talksHeld = talksHeld + other.talksHeld,
        )

        private fun perMillionHours(count: Int): Double? =
            if (workedMillis <= 0L) null else count * PER_HOURS / hoursWorked
    }

    /** A job of the company's, and the job it is part of, if any. */
    data class Job(val id: String, val parentId: String?)

    data class Shift(val projectId: String?, val checkInAt: Long, val checkOutAt: Long)

    /** A count from a register for one job; [kind] is the incident severity as stored, where there is one. */
    data class Tally(val projectId: String?, val kind: String?, val total: Int)

    /** One row per job, with its parts -- a floor, a block -- counted in it. */
    data class Row(val projectId: String, val figures: Figures)

    data class Sheet(
        /** Worst first: most injuries, then most hours. Jobs with nothing in the period are left out. */
        val rows: List<Row>,
        val total: Figures,
        /** When the last serious injury or death on any of the jobs happened, ever; null if none was recorded. */
        val lastSeriousAt: Long? = null,
    )

    /**
     * The sheet for [jobs] over [window].
     *
     * Only the company's own jobs are counted. A shift or an incident on a job
     * that is not among them -- another company's on the same phone, or one
     * deleted since -- is not this company's record and is not in its total.
     */
    fun sheetOf(
        jobs: List<Job>,
        window: LongRange,
        shifts: List<Shift>,
        incidents: List<Tally>,
        violations: List<Tally>,
        talks: List<Tally>,
        lastSeriousAt: Long? = null,
    ): Sheet {
        val parentOf = jobs.associate { it.id to it.parentId }
        val byRow = HashMap<String, Figures>()
        fun add(projectId: String?, change: (Figures) -> Figures) {
            val id = projectId?.takeIf { it in parentOf } ?: return
            val row = rootOf(id, parentOf)
            byRow[row] = change(byRow[row] ?: Figures())
        }
        shifts.forEach { shift ->
            val counted = countedMillis(shift.checkInAt, shift.checkOutAt, window)
            if (counted > 0L) {
                val capped = if (looksForgotten(shift.checkInAt, shift.checkOutAt)) 1 else 0
                add(shift.projectId) { it.copy(workedMillis = it.workedMillis + counted, shifts = it.shifts + 1, shiftsCapped = it.shiftsCapped + capped) }
            }
        }
        incidents.forEach { tally ->
            add(tally.projectId) {
                when (Incidents.parse(tally.kind)) {
                    Incidents.Severity.NEAR_MISS -> it.copy(nearMisses = it.nearMisses + tally.total)
                    Incidents.Severity.MINOR -> it.copy(minorInjuries = it.minorInjuries + tally.total)
                    Incidents.Severity.SERIOUS -> it.copy(seriousInjuries = it.seriousInjuries + tally.total)
                    Incidents.Severity.FATAL -> it.copy(fatalities = it.fatalities + tally.total)
                }
            }
        }
        violations.forEach { tally -> add(tally.projectId) { it.copy(violations = it.violations + tally.total) } }
        talks.forEach { tally -> add(tally.projectId) { it.copy(talksHeld = it.talksHeld + tally.total) } }
        val rows = byRow.filterValues { !it.isEmpty }
            .map { (id, figures) -> Row(id, figures) }
            .sortedWith(
                compareByDescending<Row> { it.figures.injuries }
                    .thenByDescending { it.figures.workedMillis }
                    .thenBy { it.projectId },
            )
        return Sheet(rows = rows, total = rows.fold(Figures()) { sum, row -> sum + row.figures }, lastSeriousAt = lastSeriousAt)
    }

    /**
     * The top-level job [id] is part of. A parent that is not among the jobs
     * -- deleted, or another company's -- ends the climb, and so does a loop,
     * which the data should never hold and a report should never hang on.
     */
    fun rootOf(id: String, parentOf: Map<String, String?>): String {
        var current = id
        val seen = HashSet<String>()
        while (seen.add(current)) {
            val parent = parentOf[current] ?: return current
            if (parent !in parentOf) return current
            current = parent
        }
        return current
    }

    /** Whole days from [at] to [today] on the site's calendar; nought for today, never negative. */
    fun daysSince(at: Long, today: LocalDate, zone: ZoneId): Long =
        ChronoUnit.DAYS.between(Instant.ofEpochMilli(at).atZone(zone).toLocalDate(), today).coerceAtLeast(0L)

    /** The base of every rate here. */
    const val PER_HOURS = 1_000_000.0

    private const val MILLIS_PER_HOUR = 3_600_000.0
    private const val MONTHS_PER_QUARTER = 3
}
