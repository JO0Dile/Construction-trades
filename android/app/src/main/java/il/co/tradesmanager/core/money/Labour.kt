package il.co.tradesmanager.core.money

/**
 * Hours worked, and what they cost.
 *
 * Labour is usually the largest number on a job and the one entered twice: once
 * when somebody clocks in, and again when a cost line is typed at the end of
 * the month. The app already records the first and has never used it, so the
 * cost has always come from somebody remembering to type the second.
 *
 * The multipliers below are the statutory ones. The **day length is not**, and
 * neither is whether a given day is a rest day: a collective agreement can be
 * more generous than the law, and Shabbat does not start at a fixed time on a
 * clock. Both are inputs. The app is not a payroll system and does not pretend
 * to be the authority on somebody's wages — it works out what a day of work on
 * this job cost, from the hours this job recorded.
 */
object Labour {

    /**
     * The default length of an ordinary day before overtime starts.
     *
     * A default, not a rule. It is what a firm changes first, and the reason it
     * is a parameter everywhere below rather than a constant used directly.
     */
    const val ORDINARY_DAY_HOURS = 8.0

    /** The first two hours past an ordinary day are paid at a quarter more. */
    const val FIRST_OVERTIME_HOURS = 2.0
    const val FIRST_OVERTIME_RATE = 1.25

    /** Everything past that, at half as much again. */
    const val FURTHER_OVERTIME_RATE = 1.5

    /**
     * Work on a day of rest, from the first hour rather than after eight.
     *
     * Which days those are is the caller's to say. It is not always Saturday:
     * it depends on the worker, and on a site with Jewish, Muslim and Christian
     * crews it is three different days.
     */
    const val REST_DAY_RATE = 1.5

    /**
     * A day's hours, split by what they are paid at.
     *
     * Kept as three numbers rather than one total because the split is the
     * answer. A day showing "ten hours" tells nobody what it cost, and a day
     * showing one figure hides the two hours that cost a quarter more.
     */
    data class Day(
        val ordinary: Double,
        val atFirstOvertimeRate: Double,
        val atFurtherOvertimeRate: Double,
    ) {
        val total: Double get() = ordinary + atFirstOvertimeRate + atFurtherOvertimeRate

        /** Hours as they are paid for, once the multipliers are applied. */
        val payable: Double
            get() = ordinary +
                atFirstOvertimeRate * FIRST_OVERTIME_RATE +
                atFurtherOvertimeRate * FURTHER_OVERTIME_RATE

        val hasOvertime: Boolean
            get() = atFirstOvertimeRate > 0.0 || atFurtherOvertimeRate > 0.0
    }

    /**
     * Splits a day's hours into ordinary time and the two overtime bands.
     *
     * On a rest day every hour is overtime from the first one, so the whole day
     * lands in the higher band and none of it is ordinary. That is what "from
     * the first hour" means, and doing it any other way gives somebody eight
     * hours of ordinary pay on a Saturday.
     */
    fun split(
        hoursWorked: Double,
        ordinaryDayHours: Double = ORDINARY_DAY_HOURS,
        isRestDay: Boolean = false,
    ): Day {
        if (hoursWorked <= 0.0) return Day(0.0, 0.0, 0.0)
        if (isRestDay) {
            // One band, at the rest-day rate, which happens to be the same
            // number as the further-overtime rate. Named separately anyway,
            // because they are different rules and one could change.
            return Day(ordinary = 0.0, atFirstOvertimeRate = 0.0, atFurtherOvertimeRate = hoursWorked)
        }
        val ordinary = minOf(hoursWorked, ordinaryDayHours)
        val overtime = (hoursWorked - ordinary).coerceAtLeast(0.0)
        val first = minOf(overtime, FIRST_OVERTIME_HOURS)
        return Day(
            ordinary = ordinary,
            atFirstOvertimeRate = first,
            atFurtherOvertimeRate = overtime - first,
        )
    }

    /**
     * What a day cost at a given hourly rate.
     *
     * Null rate means nobody has said what this person is paid, and the answer
     * is null rather than zero: a day with no rate recorded has an unknown
     * cost, and showing it as free would quietly make a job look profitable.
     */
    fun cost(day: Day, hourlyRate: Double?): Double? =
        if (hourlyRate == null) null else day.payable * hourlyRate

    /**
     * Whole hours and minutes from a span of minutes.
     *
     * Kept as a decimal because everything downstream multiplies it. Rounding
     * to two places at the boundary rather than at the end would lose a few
     * agorot per person per day, which on forty people over a year is a number
     * somebody notices.
     */
    fun hoursFrom(minutes: Long): Double = minutes / 60.0

    /**
     * A week's worth of days, added up.
     *
     * Bands are summed rather than re-split, because overtime is a daily
     * question first: two nine-hour days are two hours of overtime, and adding
     * them to eighteen and splitting once would make it ten.
     */
    fun week(days: List<Day>): Day = Day(
        ordinary = days.sumOf { it.ordinary },
        atFirstOvertimeRate = days.sumOf { it.atFirstOvertimeRate },
        atFurtherOvertimeRate = days.sumOf { it.atFurtherOvertimeRate },
    )
}
