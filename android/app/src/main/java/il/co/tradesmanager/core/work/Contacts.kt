package il.co.tradesmanager.core.work

import il.co.tradesmanager.core.safety.EmergencySheet

/**
 * Who is who on a job from outside the firm: the client, the architect,
 * the engineers, the supervisor, the municipality's inspector, the
 * subcontractors and suppliers -- and how to reach each of them.
 *
 * The page every site office keeps pinned to the wall and never keeps up to
 * date. So each entry says what the person is on this job, and keeps a way
 * to reach them that is at least a number or an address; somebody who has
 * left the job is marked off, not deleted, because the supervisor who
 * signed the steel in March is still who signed it.
 */
object Contacts {

    enum class Kind {
        CLIENT,
        SUPERVISOR,
        ARCHITECT,
        STRUCTURAL_ENGINEER,
        OTHER_ENGINEER,
        SAFETY_CONSULTANT,
        MUNICIPALITY,
        SUBCONTRACTOR,
        SUPPLIER,
        OTHER,
    }

    enum class Refusal {
        BLANK_NAME,
        NO_WAY_TO_REACH,
        NOT_A_PHONE_NUMBER,
        NOT_AN_EMAIL,
        ALREADY_REMOVED,
    }

    fun refusal(name: String, phone: String, email: String): Refusal? = when {
        name.isBlank() -> Refusal.BLANK_NAME
        phone.isBlank() && email.isBlank() -> Refusal.NO_WAY_TO_REACH
        phone.isNotBlank() && !EmergencySheet.isDialable(phone) -> Refusal.NOT_A_PHONE_NUMBER
        email.isNotBlank() && !looksLikeEmail(email) -> Refusal.NOT_AN_EMAIL
        else -> null
    }

    /** Something, an @, something, a dot, something; no spaces. Enough to catch a phone number typed in the wrong box. */
    fun looksLikeEmail(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.any { it.isWhitespace() }) return false
        val at = trimmed.indexOf('@')
        if (at <= 0 || at != trimmed.lastIndexOf('@')) return false
        val domain = trimmed.substring(at + 1)
        val dot = domain.lastIndexOf('.')
        return dot > 0 && dot < domain.length - 1
    }

    /** By what they are on the job, in the enum's order, then by name; those who have left last. */
    fun order(rows: List<Triple<Kind, String, Boolean>>): List<Int> =
        rows.indices.sortedWith(compareBy({ rows[it].third }, { rows[it].first.ordinal }, { rows[it].second.lowercase() }))

    fun kindOf(stored: String): Kind = Kind.entries.firstOrNull { it.name == stored } ?: Kind.OTHER
}
