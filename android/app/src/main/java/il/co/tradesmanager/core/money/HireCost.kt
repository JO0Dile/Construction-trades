package il.co.tradesmanager.core.money

/**
 * What a hired machine has run up.
 *
 * A pure function rather than a SQL sum on purpose. The aggregate could be
 * computed in the database, but then the same rule would exist twice — once in
 * SQL for the total and once in Kotlin for the row — and the two would drift
 * the first time somebody changed how a part-day counts. A firm has tens of
 * machines, not millions, so summing in Kotlin costs nothing and leaves one
 * definition of the rule.
 */
object HireCost {

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    /**
     * Days on hire, counted from [startedOn] to whichever comes first: today,
     * or the day it went back.
     *
     * A machine returned last Tuesday must stop charging on Tuesday, not carry
     * on quietly for the rest of the job — which is exactly the mistake that
     * makes a hire bill and a job sheet disagree.
     *
     * Part days count as whole days, because hire companies charge that way.
     */
    fun daysOnHire(startedOn: Long?, endsOn: Long?, now: Long): Long {
        if (startedOn == null) return 0L
        val until = minOf(endsOn ?: now, now)
        if (until <= startedOn) return 0L
        return (until - startedOn + DAY_MILLIS - 1) / DAY_MILLIS
    }

    /** Net of VAT, like every other figure the Money lens reads. */
    fun accrued(ratePerDay: Double?, startedOn: Long?, endsOn: Long?, now: Long): Double {
        if (ratePerDay == null) return 0.0
        return daysOnHire(startedOn, endsOn, now) * ratePerDay
    }
}
