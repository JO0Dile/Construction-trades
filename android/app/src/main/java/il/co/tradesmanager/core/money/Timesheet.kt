package il.co.tradesmanager.core.money

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Clock-ins turned into a timesheet.
 *
 * The grouping is the whole job. Overtime is a **daily** question, so hours
 * have to be gathered into person-days before any band is applied — two
 * nine-hour days are two hours of overtime, and one eighteen-hour day is eight.
 * Summing a week and splitting once gets a different, larger answer, and it is
 * the kind of error that survives review because the total hours are right.
 *
 * A shift is attributed to the day it **started**. Somebody who clocks in at
 * ten at night and out at six is on one long night, not two short days, and
 * splitting it across midnight would turn one shift with overtime into two
 * without any.
 */
object Timesheet {

    /**
     * One finished shift, as this file needs it.
     *
     * Not the stored row. Everything else in `core.money` is arithmetic with
     * no database in sight, and reaching into a Room entity here to save a
     * mapping would be the first crack in that — the mapping lives in the view
     * model, which is where knowing about storage is somebody's job.
     */
    data class Shift(
        val workerName: String,
        /** When the shift started. Which day it lands on is decided from this. */
        val startedAt: Long,
        val minutes: Long,
        val hourlyRate: Double?,
    )

    /** One person's day: the hours, the bands, and what it cost. */
    data class PersonDay(
        val workerName: String,
        val date: LocalDate,
        val hours: Labour.Day,
        /**
         * Null when nobody has recorded what this person is paid. Not zero:
         * a day with an unknown cost must not make a job look profitable.
         */
        val cost: Double?,
        val isRestDay: Boolean,
    )

    /** One person across the whole period. */
    data class PersonTotal(
        val workerName: String,
        val hours: Labour.Day,
        val cost: Double?,
        /** Days where nobody has said what this person is paid. */
        val daysWithoutRate: Int,
    ) {
        val rateIsIncomplete: Boolean get() = daysWithoutRate > 0
    }

    /**
     * Groups finished entries into person-days.
     *
     * [restDays] answers, per person and date, whether that day is a day of
     * rest for that person. It is a function rather than a set of weekdays
     * because it is not the same day for everybody: on a site with Jewish,
     * Muslim and Christian crews it is three different days, and hard-coding
     * Saturday would underpay two of the three.
     */
    fun personDays(
        shifts: List<Shift>,
        zone: ZoneId,
        ordinaryDayHours: Double = Labour.ORDINARY_DAY_HOURS,
        restDays: (workerName: String, date: LocalDate) -> Boolean = { _, _ -> false },
    ): List<PersonDay> = shifts
        .map { shift ->
            val date = Instant.ofEpochMilli(shift.startedAt).atZone(zone).toLocalDate()
            Triple(shift.workerName to date, shift.minutes, shift.hourlyRate)
        }
        .groupBy({ it.first }, { it.second to it.third })
        .map { (key, spans) ->
            val (worker, date) = key
            val restDay = restDays(worker, date)
            val hours = Labour.split(
                hoursWorked = Labour.hoursFrom(spans.sumOf { it.first }),
                ordinaryDayHours = ordinaryDayHours,
                isRestDay = restDay,
            )
            // The rate from the day's own entries. Several shifts in a day
            // normally share one; if they disagree, the last one clocked wins,
            // because that is the one somebody most recently said was right.
            val rate = spans.lastOrNull { it.second != null }?.second
            PersonDay(
                workerName = worker,
                date = date,
                hours = hours,
                cost = Labour.cost(hours, rate),
                isRestDay = restDay,
            )
        }
        .sortedWith(compareByDescending<PersonDay> { it.date }.thenBy { it.workerName })

    /** Rolls person-days up per person, adding the bands rather than re-splitting. */
    fun totals(days: List<PersonDay>): List<PersonTotal> = days
        .groupBy { it.workerName }
        .map { (worker, theirs) ->
            val withoutRate = theirs.count { it.cost == null && it.hours.total > 0.0 }
            PersonTotal(
                workerName = worker,
                hours = Labour.week(theirs.map { it.hours }),
                // Null only when nothing at all is known. A partial figure is
                // more use than none, as long as the screen says it is partial.
                cost = theirs.mapNotNull { it.cost }.takeIf { it.isNotEmpty() }?.sum(),
                daysWithoutRate = withoutRate,
            )
        }
        .sortedBy { it.workerName }

    /**
     * What the recorded hours came to across everybody.
     *
     * Deliberately **not** added to the job's cost lines. Labour entered as a
     * cost and labour clocked on site are two accounts of the same money, and
     * silently summing them doubles it. The Money screen shows this beside the
     * entered figure and lets somebody see they disagree, which is the useful
     * thing: a gap between them is either hours nobody costed or a cost line
     * nobody worked.
     */
    fun totalCost(days: List<PersonDay>): Double = days.sumOf { it.cost ?: 0.0 }

    /** Whether any day in the period has no rate against it. */
    fun hasUnpricedDays(days: List<PersonDay>): Boolean =
        days.any { it.cost == null && it.hours.total > 0.0 }
}
