package il.co.tradesmanager.core.evidence

import il.co.tradesmanager.core.i18n.Formats
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * What gets burned into the corner of a site photograph.
 *
 * A photograph of a defect is only worth anything later if somebody can say
 * when it was taken and where. Both facts already sit in the database beside
 * the file — but a photograph gets emailed, printed and put in front of a
 * loss adjuster, and at that point it is a picture of a wall with nothing
 * attached to it. So the stamp goes into the pixels, where it travels with
 * the file.
 *
 * The text is worked out here, away from any drawing code, because what a
 * stamp says is worth testing and how it is painted is not.
 */
object PhotoStamp {

    /**
     * Six decimal places, about a tenth of a metre. Finer than the phone
     * knows and coarse enough to read off a printout.
     */
    private const val COORDINATE_PLACES = 6

    /**
     * The lines to draw, top to bottom. Empty facts are left out rather than
     * printed blank: a photograph stamped "no location" says less than one
     * with a date and nothing else.
     */
    fun lines(
        capturedAt: Long,
        latitude: Double?,
        longitude: Double?,
        recordedBy: String?,
        locale: Locale,
        zone: ZoneId,
    ): List<String> {
        val moment = Instant.ofEpochMilli(capturedAt).atZone(zone)
        return listOfNotNull(
            Formats.dateTime(moment.toLocalDate(), moment.toLocalTime(), locale),
            coordinates(latitude, longitude),
            recordedBy?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * Coordinates, or null when the phone did not know where it was.
     *
     * Formatted for the root locale on purpose. A phone set to Hebrew or
     * Arabic would otherwise be capable of writing "32,085300" — and a comma
     * where a decimal point belongs turns a coordinate into two numbers, in a
     * stamp whose whole job is to be readable years later by somebody who was
     * not there.
     */
    fun coordinates(latitude: Double?, longitude: Double?): String? {
        if (latitude == null || longitude == null) return null
        if (latitude.isNaN() || longitude.isNaN()) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val format = "%.${COORDINATE_PLACES}f"
        return String.format(Locale.ROOT, format, latitude) + ", " +
            String.format(Locale.ROOT, format, longitude)
    }

    /**
     * Whether a photograph of this kind gets stamped.
     *
     * Site photographs do. A person's own photograph and the picture of their
     * ID document do not: stamping somebody's face with the coordinates of
     * where they were standing is no use as evidence of anything and is not a
     * thing to do to a person's identity documents.
     */
    fun appliesTo(ownerType: String): Boolean = !ownerType.startsWith("account.")
}
