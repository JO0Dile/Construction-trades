package il.co.tradesmanager.core.security

/**
 * How long records are kept before a purge may remove them.
 *
 * The app supplies the mechanism and refuses to supply the policy. How long
 * site records must be held is a legal question with different answers for a
 * payment application, a scaffold inspection and a worker's ID number, and it
 * is answered by the organisation running the app — eventually a ministry —
 * not by whoever wrote this file.
 *
 * So the default keeps everything. A number invented here would quietly
 * destroy evidence on somebody else's job, months later, with nobody having
 * decided anything.
 */
object Retention {

    private const val DAY_MILLIS = 86_400_000L

    /** Offered in the settings picker. Zero keeps everything. */
    val CHOICES: List<Int> = listOf(0, 365, 3 * 365, 7 * 365)

    /**
     * The instant before which entries may be removed, or null to keep all.
     *
     * Null rather than zero for "keep everything": a cutoff of zero is a real
     * instant in 1970 that happens to delete nothing today, and code reading
     * it as a number would eventually treat it as a policy. Null is the
     * absence of one, and the caller has to handle it.
     *
     * A non-positive period is the absence of a policy too, not a period that
     * runs backwards — [days] is validated where it is stored, and this
     * refuses to invent a future cutoff if that validation is ever bypassed.
     */
    fun cutoff(now: Long, days: Int): Long? =
        if (days <= 0) null else now - days * DAY_MILLIS
}
