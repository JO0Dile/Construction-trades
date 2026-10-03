package il.co.tradesmanager.core.people

import il.co.tradesmanager.core.find.Search

/**
 * When two ID numbers are the same person's.
 *
 * They were compared as text, and text is the wrong thing to compare. An ID
 * number is a number, and the same number reaches this app written several
 * ways: `Formats` puts Arabic-Indic digits on an Arabic screen and the keypad
 * gives those digits back, so a gateman on an Arabic phone types ٣٠١٢٣٤٥٦٧ for
 * a man whose row says 301234567. Somebody reading off a document types the
 * separators that are printed on it. A number pasted out of a message brings an
 * invisible direction mark with it.
 *
 * Every one of those compared unequal, and the damage is not a failed search.
 * At the gate, a man who **is** on the books reads as a stranger, so he gets
 * added a second time — and `setIdNumber` is a one-way door, so the duplicate
 * keeps the number and the two records cannot be told apart afterwards. The
 * same comparison is what stops one person being issued another person's
 * number, so getting it wrong lets that through as well.
 *
 * So: compare the digits and nothing else.
 *
 * Deliberately not a validity check. This does not know what a teudat zehut
 * looks like, does not count nine digits and does not run the check digit. A
 * foreign passport number, an army number and a number somebody mistyped are
 * all things a site office has to be able to write down, and a gate that
 * refuses a real person because their document is not Israeli is worse than
 * one that records what it was told.
 */
object IdNumbers {

    /**
     * The comparable form of an ID number, or null when there is nothing to
     * compare — blank, or a string with no digits in it at all.
     *
     * Null rather than empty on purpose: an empty search box must not find the
     * first person with no number on their row.
     */
    fun canonical(idNumber: String?): String? =
        idNumber?.let { Search.digits(it) }?.takeIf { it.isNotEmpty() }

    /**
     * Whether these two name the same person.
     *
     * Two missing numbers are not a match. Most people on a site have no
     * number on their row yet, and treating "neither has one" as "these are
     * the same man" would merge the whole crew.
     */
    fun same(one: String?, other: String?): Boolean {
        val canonical = canonical(one) ?: return false
        return canonical == canonical(other)
    }
}
