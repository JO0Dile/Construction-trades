package il.co.tradesmanager.core.safety

/**
 * The visitor log's rules, which are few on purpose.
 *
 * A visitor log is filled in at a gate by somebody who wants to get back to
 * work, about somebody who is standing there waiting. Every field it demands
 * is a field that gets "x" typed into it. So the only thing refused is a
 * visit with no name, because a nameless row on a roll call is a row nobody
 * can shout for.
 */
object Visits {

    enum class Refusal {
        BLANK_NAME,

        /** Signing out a visit that has already been signed out. */
        ALREADY_LEFT,
    }

    fun refusal(name: String): Refusal? = if (name.isBlank()) Refusal.BLANK_NAME else null

    fun signOutRefusal(leftAt: Long?): Refusal? = if (leftAt != null) Refusal.ALREADY_LEFT else null

    /**
     * Signed in long enough ago that they have probably gone and nobody
     * signed them out. The same shift-and-a-bit a clocking gets -- see
     * [Muster.STALE_AFTER_MS] -- and for the same reason flagged, never
     * dropped: a roll call that quietly leaves somebody off is the one
     * mistake it must not make.
     */
    fun forgotten(arrivedAt: Long, now: Long): Boolean = now - arrivedAt > Muster.STALE_AFTER_MS

    /** An open visit as the roll call takes it. */
    fun onRollCall(name: String, arrivedAt: Long, projectId: String): Muster.OpenCheckIn =
        Muster.OpenCheckIn(
            personId = null,
            name = name,
            checkInAt = arrivedAt,
            projectId = projectId,
            visitor = true,
        )
}
