package il.co.tradesmanager.core.people

/**
 * How to reach somebody, and what counts as an answer.
 *
 * A phone number is required and an email address is not, which is the way
 * round a site works: everybody on one has a phone and is reached on it within
 * the hour, and plenty of them have never had an email address. Requiring the
 * email would be requiring the thing that is optional in real life.
 *
 * The number is deliberately **not** checked against Israeli formats. Israeli
 * sites are worked by Romanian, Moldovan, Chinese, Thai, Ukrainian and
 * Palestinian crews, and a rule that only accepts `05x` would refuse the
 * people most likely to need somebody to be able to ring them. What is checked
 * is that it could be dialled at all.
 */
object Contact {

    /** Shorter than any national number worth dialling. */
    const val MIN_DIGITS = 7

    /** E.164 allows fifteen digits, country code included. Nothing is longer. */
    const val MAX_DIGITS = 15

    /** Punctuation people put in phone numbers, and nothing else. */
    private const val PUNCTUATION = " -()./"

    enum class PhoneFault {
        /** Required. The one field on the form that is. */
        MISSING,

        /** Letters, or punctuation nobody puts in a phone number. */
        NOT_A_NUMBER,

        TOO_SHORT,
        TOO_LONG,

        /**
         * `0000000000`, and the rest of the family.
         *
         * The cheapest way past a required field is to hold one key down. It
         * is worth naming because the person doing it is not being difficult:
         * they are being asked for something at a moment they do not want to
         * give it, and a form that accepts it has collected a column of
         * zeroes rather than a way of reaching anybody.
         */
        ONE_DIGIT_REPEATED,
    }

    /**
     * One fault, not several.
     *
     * An address is either usable or it is not, and "your email is missing a
     * dot after the @" is a level of pedantry that helps nobody type it right.
     */
    enum class EmailFault { NOT_AN_ADDRESS }

    /** Null when [raw] is a number somebody could ring. */
    fun blocksPhone(raw: String?): PhoneFault? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return PhoneFault.MISSING

        // A leading + is the country code and belongs there; anywhere else it
        // is not a phone number.
        val body = text.removePrefix("+")
        if (body.any { !it.isDigit() && it !in PUNCTUATION }) return PhoneFault.NOT_A_NUMBER

        val digits = body.filter { it.isDigit() }
        return when {
            digits.length < MIN_DIGITS -> PhoneFault.TOO_SHORT
            digits.length > MAX_DIGITS -> PhoneFault.TOO_LONG
            digits.toSet().size < 2 -> PhoneFault.ONE_DIGIT_REPEATED
            else -> null
        }
    }

    /** Null when [raw] is blank -- it is optional -- or looks like an address. */
    fun blocksEmail(raw: String?): EmailFault? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null

        val at = text.indexOf('@')
        val domain = if (at >= 0) text.substring(at + 1) else ""
        val looksRight = at > 0 &&
            text.indexOf('@', at + 1) < 0 &&
            text.none { it.isWhitespace() } &&
            domain.contains('.') &&
            !domain.startsWith('.') &&
            !domain.endsWith('.') &&
            !domain.contains("..")
        return if (looksRight) null else EmailFault.NOT_AN_ADDRESS
    }
}
