package il.co.tradesmanager.core.sync

/**
 * Which of two versions of a row is the newer one.
 *
 * The timestamp alone is not enough. Two phones on the same site, both offline,
 * both editing the same job, can easily write in the same millisecond — and if
 * the answer to "which is newer" depends on which device is asking, the two
 * never agree and the row flips back and forth forever. So the device id is
 * part of the comparison, giving a total order every device computes the same
 * way. It is arbitrary which device wins a tie; what matters is that everyone
 * agrees who did.
 */
data class Revision(val updatedAt: Long, val deviceId: String) : Comparable<Revision> {

    override fun compareTo(other: Revision): Int {
        val byTime = updatedAt.compareTo(other.updatedAt)
        // The tie-break is lexical on the device id: meaningless as a rule,
        // but identical on every device, which is the only property needed.
        return if (byTime != 0) byTime else deviceId.compareTo(other.deviceId)
    }
}

object Revisions {

    /**
     * A day. Phones drift, and a phone somebody has set by hand can be out by
     * hours; more than a day ahead of everybody else is a broken clock rather
     * than an eager one.
     */
    const val PLAUSIBLE_SKEW_MILLIS = 24L * 60L * 60L * 1000L

    fun laterOf(a: Revision, b: Revision): Revision = if (a >= b) a else b

    /**
     * A row claiming to be from the future.
     *
     * This is the failure that quietly wrecks last-writer-wins: one phone with
     * its clock set wrong writes a timestamp a year ahead, and from then on it
     * wins every argument on every device, silently overwriting everybody
     * else's work. It cannot be resolved locally — the fix belongs to whatever
     * the devices sync through, which is the only party that knows the real
     * time — but it can be spotted and reported rather than obeyed.
     */
    fun isFromTheFuture(revision: Revision, now: Long): Boolean =
        revision.updatedAt > now + PLAUSIBLE_SKEW_MILLIS
}
