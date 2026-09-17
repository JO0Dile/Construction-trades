package il.co.tradesmanager.core.people

/**
 * What the office may change about somebody, and what it may not.
 *
 * The rules live here rather than in the repository for the same reason
 * `core.safety.Violations` does: this is the part worth testing, and a rule
 * that can only be exercised through a database is a rule nobody exercises.
 * The repository asks these questions and then enforces the answer — see
 * `AccountRepository.correctDetails`.
 *
 * The rule that matters is the ID number. It can be filled in when it is
 * missing and not changed once it is there. That number goes on the gate list
 * and on the induction record, so a quiet edit is how one person ends up
 * standing behind another person's paperwork — the same one-way door
 * `AccountRepository.setIdNumber` has always had, said once, here.
 */
object Corrections {

    /** Somebody's details: as they are held, or as somebody proposes them. */
    data class Details(
        val displayName: String,
        val phone: String?,
        val email: String?,
        val idNumber: String?,
    )

    /** What is wrong with a proposal, in terms a screen can put a sentence to. */
    sealed interface Fault {
        /** A row with nobody's name on it is a row nobody can read. */
        data object NameIsBlank : Fault

        data class BadPhone(val fault: Contact.PhoneFault) : Fault

        data class BadEmail(val fault: Contact.EmailFault) : Fault

        /** It is already set. Adding a missing one is welcome; changing one is not. */
        data object IdNumberIsSet : Fault
    }

    /**
     * Whitespace is not an answer.
     *
     * Blank and null mean the same thing throughout — not recorded — so they
     * are made the same thing before anything compares them. Otherwise "  "
     * typed over an empty box counts as a change and writes an audit row
     * saying somebody's phone number was corrected to nothing.
     */
    fun tidy(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * What stops this correction, or null if nothing does.
     *
     * A blank phone number is allowed and means "still unknown", which is the
     * true state of every account made before the app asked for one. A number
     * that is *there* has to be a number: the same rule the sign-up form
     * applies, so somebody cannot get a bad one in through the side door.
     */
    fun blocks(held: Details, proposed: Details): Fault? {
        if (proposed.displayName.trim().isEmpty()) return Fault.NameIsBlank

        tidy(proposed.phone)?.let { phone ->
            Contact.blocksPhone(phone)?.let { return Fault.BadPhone(it) }
        }
        tidy(proposed.email)?.let { email ->
            Contact.blocksEmail(email)?.let { return Fault.BadEmail(it) }
        }

        val heldId = tidy(held.idNumber)
        val proposedId = tidy(proposed.idNumber)
        if (heldId != null && proposedId != null && proposedId != heldId) {
            return Fault.IdNumberIsSet
        }
        return null
    }

    /**
     * The details as they end up. Ask [blocks] first; this assumes the answer
     * was null and will otherwise happily keep what it was told to keep.
     *
     * The held ID number wins over the proposed one rather than the other way
     * round, so that a screen which shows the number in a disabled box and
     * sends it back unchanged cannot overwrite anything, and a screen with a
     * bug in it cannot either.
     */
    fun applied(held: Details, proposed: Details): Details = Details(
        displayName = proposed.displayName.trim(),
        phone = tidy(proposed.phone),
        email = tidy(proposed.email),
        idNumber = tidy(held.idNumber) ?: tidy(proposed.idNumber),
    )

    /** Which fields this correction actually changes, for the audit trail. */
    fun changed(held: Details, next: Details): List<String> = buildList {
        if (next.displayName != held.displayName) add("name")
        if (next.phone != tidy(held.phone)) add("phone")
        if (next.email != tidy(held.email)) add("email")
        if (next.idNumber != tidy(held.idNumber)) add("ID number")
    }
}
