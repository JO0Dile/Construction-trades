package il.co.tradesmanager.core.money

/**
 * What an order is worth and what of it is still to come.
 *
 * A pure function over quantities and prices, so the rule that decides whether
 * a job is over-committed can be tested without a database. The SQL that sums
 * the same thing across a job mirrors these two rules exactly; they are stated
 * here because this is where they can be pinned.
 */
object OrderTotals {

    /** One line's value, at the price agreed rather than an estimate. */
    fun lineValue(quantityOrdered: Double, unitPrice: Double): Double =
        quantityOrdered * unitPrice

    /**
     * What is still to come on a line.
     *
     * Never below zero. Suppliers do send fifty-two when you ordered fifty,
     * and without the floor a generous delivery would quietly credit the job
     * and make it look cheaper than it is.
     */
    fun outstandingQuantity(quantityOrdered: Double, quantityReceived: Double): Double =
        (quantityOrdered - quantityReceived).coerceAtLeast(0.0)

    fun outstandingValue(
        quantityOrdered: Double,
        quantityReceived: Double,
        unitPrice: Double,
    ): Double = outstandingQuantity(quantityOrdered, quantityReceived) * unitPrice
}
