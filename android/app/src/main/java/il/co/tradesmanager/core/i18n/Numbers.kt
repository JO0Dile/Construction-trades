package il.co.tradesmanager.core.i18n

/**
 * A number somebody typed, in whichever digits their keyboard gave them.
 *
 * Every number field in the app parsed with `replace(',', '.').toDoubleOrNull()`,
 * and the JVM only reads ASCII digits. An Arabic keyboard that types the
 * Arabic-Indic digits -- which several do, on the number pad as well --
 * produced a quantity, a price or a temperature that was simply refused, with
 * nothing on the screen to say why. `Formats` already puts those digits on the
 * screen; what is on the screen is what gets typed back in.
 *
 * Accepted, and folded to ASCII before parsing:
 *
 * - Arabic-Indic digits (U+0660 to U+0669) and the Extended Arabic-Indic
 *   digits used in Persian and Urdu (U+06F0 to U+06F9);
 * - a comma, or the Arabic decimal separator (U+066B), as the decimal point;
 * - the Arabic thousands separator (U+066C), ordinary spaces and the no-break
 *   spaces a pasted number carries, all dropped;
 * - the Unicode minus sign (U+2212) as well as the hyphen.
 *
 * Written with code points rather than character escapes on purpose: the
 * toolchain this repository is edited with has turned escaped direction marks
 * into the real invisible characters more than once.
 */
object Numbers {

    private const val ARABIC_INDIC_ZERO = 0x0660
    private const val EXTENDED_ARABIC_INDIC_ZERO = 0x06F0
    private const val ARABIC_DECIMAL_SEPARATOR = 0x066B
    private const val ARABIC_THOUSANDS_SEPARATOR = 0x066C
    private const val MINUS_SIGN = 0x2212
    private const val NO_BREAK_SPACE = 0x00A0
    private const val NARROW_NO_BREAK_SPACE = 0x202F

    /**
     * The typed text as a number, or null if it is not one.
     *
     * Two decimal points are not a number: "1.200,50" could be either of two
     * values depending on who typed it, and guessing is how a delivery of
     * twelve hundred becomes one of one point two.
     */
    fun parseDecimal(text: String): Double? {
        val out = StringBuilder(text.length)
        for (c in text) {
            val code = c.code
            when {
                code in ARABIC_INDIC_ZERO..ARABIC_INDIC_ZERO + 9 ->
                    out.append('0' + (code - ARABIC_INDIC_ZERO))
                code in EXTENDED_ARABIC_INDIC_ZERO..EXTENDED_ARABIC_INDIC_ZERO + 9 ->
                    out.append('0' + (code - EXTENDED_ARABIC_INDIC_ZERO))
                c == ',' || code == ARABIC_DECIMAL_SEPARATOR -> out.append('.')
                code == MINUS_SIGN -> out.append('-')
                c == ' ' || code == NO_BREAK_SPACE || code == NARROW_NO_BREAK_SPACE ||
                    code == ARABIC_THOUSANDS_SEPARATOR -> Unit
                else -> out.append(c)
            }
        }
        val folded = out.toString()
        if (folded.count { it == '.' } > 1) return null
        // Only digits, one point and a leading sign. Kotlin's own parser also
        // accepts "NaN", "Infinity", hex floats and a trailing "d", none of
        // which anybody means when they type a quantity.
        if (!DECIMAL.matches(folded)) return null
        return folded.toDoubleOrNull()
    }

    /**
     * A whole number somebody typed -- a headcount, a number of days, a
     * percentage -- read the same way as [parseDecimal], or null.
     *
     * "2.5" is not a whole number and is refused rather than truncated: a
     * headcount of two and a half is a typo, and silently writing two is a
     * record that disagrees with what was typed.
     */
    fun parseWhole(text: String): Long? {
        val value = parseDecimal(text) ?: return null
        if (value != Math.rint(value) || kotlin.math.abs(value) > MAX_WHOLE) return null
        return value.toLong()
    }

    /**
     * What a decimal field keeps as somebody types into it.
     *
     * Seven fields filtered their input to `isDigit() || c == '.'`, which
     * throws the comma away. On a keyboard that offers a comma as the decimal
     * point -- the Hebrew and Arabic ones do -- "7,5" became "75": a payment
     * ten times too big, a concrete pour ten times too large, a lift radius of
     * twenty-five metres for one of two and a half. Nothing on the screen said
     * anything was wrong, because the field showed exactly what it had kept.
     *
     * This keeps every digit [parseDecimal] can read, every decimal point it
     * accepts, and -- only where a field allows it -- a minus sign at the
     * front. What it keeps always parses to the number the person meant.
     */
    fun typingDecimal(text: String, allowNegative: Boolean = false): String = buildString {
        for (c in text) {
            val code = c.code
            val keep = c in '0'..'9' ||
                code in ARABIC_INDIC_ZERO..ARABIC_INDIC_ZERO + 9 ||
                code in EXTENDED_ARABIC_INDIC_ZERO..EXTENDED_ARABIC_INDIC_ZERO + 9 ||
                c == '.' || c == ',' || code == ARABIC_DECIMAL_SEPARATOR ||
                (allowNegative && isEmpty() && (c == '-' || code == MINUS_SIGN))
            if (keep) append(c)
        }
    }

    /** Past this a Double stops holding every whole number exactly. */
    private const val MAX_WHOLE = 9_007_199_254_740_991.0

    private val DECIMAL = Regex("[+-]?(\\d+\\.?\\d*|\\.\\d+)")
}
