package il.co.tradesmanager.core.audit

/**
 * What an audit entry says, stored so it can be read in a language nobody had
 * chosen when it was written.
 *
 * The summary used to be English prose built at the moment of the write —
 * "Checklist signed", "PTW-14 issued to Yossi". That is the one kind of text
 * in this app which cannot be translated at the point it is created, because
 * the person who reads it is not the person who wrote it. A Hebrew-speaking
 * site manager opening the audit trail, or a safety officer handed an export
 * six months later, reads whatever language the labourer's phone happened to
 * be in. The audit trail is the register this app exists to produce, and it
 * was the least readable screen in it.
 *
 * So what is stored is a key and its arguments, and the language is chosen by
 * whoever is looking:
 *
 *     permit_issued|PTW-14|Yossi
 *
 * The arguments are the data — a reference, a name, a number — and stay as
 * they were typed. An argument beginning with [NESTED] is itself a key, which
 * is how a role or a status arrives translated rather than as `SITE_MANAGER`
 * in the middle of a Hebrew sentence.
 *
 * **Anything that does not parse as a key is shown exactly as it is stored.**
 * That is what makes this safe to introduce into a register that already has
 * entries in it: every row written before this existed is English prose, and
 * English prose is not a key, so it prints as it always did. Nothing is
 * rewritten — an audit trail whose old rows get edited to look tidier is not
 * an audit trail.
 */
object Summary {

    /** Between the key and its arguments. */
    const val SEPARATOR = '|'

    /** In front of an argument that is itself a key. */
    const val NESTED = '@'

    private const val ESCAPE = '\\'

    /**
     * A key: lower case, digits and underscores, starting with a letter.
     *
     * Narrow on purpose. It is what tells a stored key apart from the English
     * prose already in the register, and "Checklist signed" — a capital and a
     * space — can never be mistaken for one.
     */
    private val KEY = Regex("[a-z][a-z0-9_]*")

    data class Parsed(val key: String, val arguments: List<String>)

    /**
     * Stores [key] with [arguments].
     *
     * A separator or an escape inside an argument is escaped rather than
     * rejected: the arguments are people's names and their own references, and
     * a register that refuses to record a job called "A|B" is worse than one
     * that stores it awkwardly.
     */
    fun of(key: String, vararg arguments: String): String =
        (listOf(key) + arguments.map(::escape)).joinToString(SEPARATOR.toString())

    /**
     * A number, as it should be written into a summary.
     *
     * Plain digits, and no trailing `.0` on a whole number — a register that
     * says "4.0 sockets" was written by a program rather than by a person,
     * and reads that way. ASCII rather than the reader's own digits on
     * purpose: the argument is stored once and read in three languages, so it
     * cannot be formatted for any one of them.
     */
    fun number(value: Double): String = when {
        !value.isFinite() -> value.toString()
        value == Math.rint(value) && kotlin.math.abs(value) < 1e15 -> value.toLong().toString()
        else -> value.toString()
    }

    /**
     * A moment, as it should be written into a summary.
     *
     * The day it fell on where the work is, in ISO order. This was an epoch
     * millisecond count — "PO-12 due 1758585600000" — which is a thirteen
     * digit number where a delivery date should be, and was that in English
     * too. A date is stored rather than formatted because it is written once
     * and read in three languages, and 2026-10-01 is the one spelling that
     * means the same in all of them.
     */
    fun date(epochMillis: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toString()

    /** Null when this is not one of ours, which means show it as it is. */
    fun parse(stored: String): Parsed? {
        val parts = split(stored)
        val key = parts.firstOrNull() ?: return null
        if (!KEY.matches(key)) return null
        return Parsed(key, parts.drop(1))
    }

    /** True when [argument] is a key in its own right, with the mark removed. */
    fun nested(argument: String): String? =
        argument.takeIf { it.length > 1 && it[0] == NESTED }?.substring(1)?.takeIf { KEY.matches(it) }

    /**
     * Marks [key] as an argument to be looked up rather than printed.
     *
     * Something that is not a key is returned untouched, because the callers
     * are not always sure. A stock movement's reason is a key when the plus
     * and minus wrote it and free text when a delivery did, and marking the
     * free text would print the mark.
     */
    fun nest(key: String): String = if (KEY.matches(key)) NESTED + key else key

    private fun escape(argument: String): String = buildString {
        for (c in argument) when (c) {
            ESCAPE -> append(ESCAPE).append(ESCAPE)
            SEPARATOR -> append(ESCAPE).append(SEPARATOR)
            else -> append(c)
        }
    }

    private fun split(stored: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        for (c in stored) {
            when {
                escaped -> { current.append(c); escaped = false }
                c == ESCAPE -> escaped = true
                c == SEPARATOR -> { parts.add(current.toString()); current.clear() }
                else -> current.append(c)
            }
        }
        if (escaped) current.append(ESCAPE)
        parts.add(current.toString())
        return parts
    }
}
