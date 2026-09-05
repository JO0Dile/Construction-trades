package il.co.tradesmanager.core.money

/**
 * What a job is worth, what it has cost, and what is still owed.
 *
 * Deliberately a plain data class with no database or Android in sight: these
 * are the numbers somebody makes decisions on, and every one of them is
 * asserted in `JobFinancialsTest`. A rounding slip here is not a display bug,
 * it is an invoice that does not match a statement.
 *
 * Everything is **net of VAT** except where a name says otherwise. That is the
 * number that tells you whether a job made anything; VAT passes through and is
 * added once, at the point of printing a document, from the rate that document
 * was issued under.
 */
data class JobFinancials(
    /** What the client agreed to pay, before any variations. */
    val contractValue: Double = 0.0,
    /** Variations the client has signed off. These change what is owed. */
    val approvedVariations: Double = 0.0,
    /** Variations raised but not yet decided. These change nothing yet. */
    val proposedVariations: Double = 0.0,
    /** Money actually spent: entered cost lines. */
    val costToDate: Double = 0.0,
    /**
     * Money the job has committed but not yet spent — priced material lines
     * that are on the job sheet and not yet bought. Not a cost; a warning.
     */
    val committedCosts: Double = 0.0,
    val invoiced: Double = 0.0,
    val paid: Double = 0.0,
    val vatRate: Double = ISRAELI_VAT,
) {

    /** What the client owes once approved variations are counted. */
    val revisedContract: Double get() = contractValue + approvedVariations

    /** What it would become if every open variation were approved. */
    val potentialContract: Double get() = revisedContract + proposedVariations

    /** Spent plus committed: the number that tells you if you are heading over. */
    val forecastCost: Double get() = costToDate + committedCosts

    val margin: Double get() = revisedContract - costToDate

    val forecastMargin: Double get() = revisedContract - forecastCost

    /**
     * Margin as a fraction of the revised contract.
     *
     * Zero rather than infinity on a job with no contract value yet: a new job
     * showing "∞% margin" would be worse than showing nothing.
     */
    val marginFraction: Double
        get() = if (revisedContract == 0.0) 0.0 else margin / revisedContract

    /** Work done but not yet billed. Negative means over-billed. */
    val uninvoiced: Double get() = revisedContract - invoiced

    /** Billed and not yet paid — the number a chase call is about. */
    val outstanding: Double get() = invoiced - paid

    /** True when spending has passed what the client is paying. */
    val isOverBudget: Boolean get() = costToDate > revisedContract

    /** True when it has not yet, but is forecast to. */
    val isForecastOverBudget: Boolean get() = !isOverBudget && forecastCost > revisedContract

    fun withVat(net: Double): Double = net * (1.0 + vatRate)

    fun vatOn(net: Double): Double = net * vatRate

    /** Nested rather than in the companion, so they read as JobFinancials.X. */
    object VariationStatus {
        const val PROPOSED = "PROPOSED"
        const val APPROVED = "APPROVED"
        const val REJECTED = "REJECTED"
    }

    object InvoiceStatus {
        const val DRAFT = "DRAFT"
        const val SENT = "SENT"
        const val PAID = "PAID"
    }

    /** What money went out on. Categories a quantity surveyor would name. */
    object CostCategory {
        const val MATERIAL = "MATERIAL"
        const val LABOUR = "LABOUR"
        const val PLANT = "PLANT"
        const val SUBCONTRACTOR = "SUBCONTRACTOR"
        const val OTHER = "OTHER"

        val all = listOf(MATERIAL, LABOUR, PLANT, SUBCONTRACTOR, OTHER)
    }

    companion object {
        /**
         * Israel's standard rate, 18% since 1 January 2025.
         *
         * Only ever a default for a job that has not set its own. A job priced
         * under the old 17% keeps its own rate, because its paperwork has to
         * keep saying what it said at the time.
         */
        const val ISRAELI_VAT = 0.18
    }
}
