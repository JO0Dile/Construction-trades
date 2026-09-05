package il.co.tradesmanager.core.security

/**
 * A signature drawn with a finger, as something that can be stored and read
 * back years later.
 *
 * Points are normalised to 0..1 rather than stored in pixels. A signature
 * captured on a 6-inch phone has to redraw correctly in a report, on a tablet,
 * and on whatever device somebody opens the record with in five years' time,
 * and pixels tie it to the screen it was drawn on.
 *
 * [decode] never throws. A signature is evidence attached to a permit or an
 * induction, and a record with a corrupted signature must still open — showing
 * what it can and saying the rest is unreadable — rather than crashing the
 * screen that was supposed to display it.
 */
object Signature {

    /** Three decimals: a thousandth of the pad, finer than a fingertip. */
    private const val SCALE = 1000.0

    private const val STROKE_SEPARATOR = ';'
    private const val POINT_SEPARATOR = ' '
    private const val COORD_SEPARATOR = ','

    /** A stroke is one unbroken movement of the finger. */
    data class Point(val x: Float, val y: Float)

    fun encode(strokes: List<List<Point>>): String = strokes
        .filter { it.isNotEmpty() }
        .joinToString(STROKE_SEPARATOR.toString()) { stroke ->
            stroke.joinToString(POINT_SEPARATOR.toString()) { point ->
                "${round(point.x)}$COORD_SEPARATOR${round(point.y)}"
            }
        }

    fun decode(text: String?): List<List<Point>> {
        if (text.isNullOrBlank()) return emptyList()
        return text.split(STROKE_SEPARATOR)
            .map { stroke -> decodeStroke(stroke) }
            .filter { it.isNotEmpty() }
    }

    private fun decodeStroke(stroke: String): List<Point> = stroke
        .split(POINT_SEPARATOR)
        .mapNotNull { decodePoint(it) }

    private fun decodePoint(point: String): Point? {
        val parts = point.split(COORD_SEPARATOR)
        if (parts.size != 2) return null
        val x = parts[0].toFloatOrNull() ?: return null
        val y = parts[1].toFloatOrNull() ?: return null
        if (x.isNaN() || y.isNaN()) return null
        return Point(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
    }

    /**
     * Whether this counts as somebody having signed.
     *
     * One stray tap is not a signature. Requiring a stroke that actually
     * travels stops a permit or an induction being signed off by a phone in
     * somebody's pocket, and stops a dialog being dismissed by a thumb.
     */
    fun isSigned(text: String?): Boolean = decode(text).any { it.size >= 2 }

    /**
     * Deliberately not String.format. That formats for the current locale, and
     * in a locale whose decimal separator is a comma it would write "0,5" into
     * a format whose coordinates are separated by commas — turning every
     * signature captured in half the world into unreadable rubbish. Double's
     * own toString is locale-independent.
     */
    private fun round(value: Float): String {
        val clamped = value.coerceIn(0f, 1f)
        val rounded = Math.round(clamped * SCALE) / SCALE
        // Trailing zeros are noise in a string stored on every signed record;
        // "0.5" says the same as "0.500" in a third of the bytes.
        return rounded.toString().removeSuffix(".0")
    }
}
