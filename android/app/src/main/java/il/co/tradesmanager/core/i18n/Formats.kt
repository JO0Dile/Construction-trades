package il.co.tradesmanager.core.i18n

import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/**
 * The Israeli conventions the brief fixes: metric units, the shekel, DD/MM/YYYY
 * dates and a 24-hour clock — rendered with the digits and numerals of whatever
 * language is active, so Arabic shows Arabic-Indic digits where the locale asks
 * for them.
 */
object Formats {

    val ILS: Currency = Currency.getInstance("ILS")

    private const val DATE_PATTERN = "dd/MM/yyyy"
    private const val TIME_PATTERN = "HH:mm"

    fun date(date: LocalDate, locale: Locale): String =
        DateTimeFormatter.ofPattern(DATE_PATTERN, locale).format(date)

    fun time(time: LocalTime, locale: Locale): String =
        DateTimeFormatter.ofPattern(TIME_PATTERN, locale).format(time)

    /**
     * Reads a date somebody typed, day first, month, then a four-digit year.
     *
     * The order is fixed Israeli convention rather than the device's; the
     * separator is not. A phone's number pad has no slash on it, so a dot, a
     * dash, a comma or a space between the parts reads the same as a slash,
     * and eight digits with nothing between them read as DDMMYYYY. The parts
     * are still checked strictly: 31/02 fails rather than quietly becoming 3
     * March, and a year first (2027-03-07) fails rather than being guessed
     * at. Null means "that is not a date", which the caller shows as an error
     * instead of storing a wrong one.
     */
    fun parseDate(text: String): LocalDate? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val parts = if (trimmed.all { it.isDigit() }) {
            if (trimmed.length != DIGITS_ONLY_LENGTH) return null
            listOf(trimmed.substring(0, 2), trimmed.substring(2, 4), trimmed.substring(4))
        } else {
            trimmed.split(DATE_SEPARATORS)
        }
        if (parts.size != 3 || parts.any { part -> part.isEmpty() || !part.all { it.isDigit() } }) return null
        val (day, month, year) = parts
        if (day.length > 2 || month.length > 2 || year.length != 4) return null
        return runCatching { LocalDate.of(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
    }

    /** What people put between the day, the month and the year, one or more of them. */
    private val DATE_SEPARATORS = Regex("[/.\\-,\\s]+")

    /** DDMMYYYY typed with nothing between. */
    private const val DIGITS_ONLY_LENGTH = 8

    fun dateTime(date: LocalDate, time: LocalTime, locale: Locale): String =
        "${date(date, locale)} ${time(time, locale)}"

    /** Money, always in shekels — the app does not pretend to multi-currency. */
    fun money(amount: Double, locale: Locale): String =
        NumberFormat.getCurrencyInstance(locale).apply {
            currency = ILS
            maximumFractionDigits = 2
        }.format(amount)

    /**
     * Money with the agorot dropped, for a dashboard tile.
     *
     * A summary figure is read at a glance from a van; two decimal places make
     * it longer without making it more useful, and the exact number is on the
     * invoice where it belongs.
     */
    fun moneyRounded(amount: Double, locale: Locale): String =
        NumberFormat.getCurrencyInstance(locale).apply {
            currency = ILS
            maximumFractionDigits = 0
        }.format(amount)

    /**
     * A stock figure. Whole numbers print without a decimal tail, because
     * "12 sockets" reads better on a phone in the sun than "12.00 sockets".
     */
    fun quantity(value: Double, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = if (value % 1.0 == 0.0) 0 else 2
        }.format(value)

    fun percent(fraction: Double, locale: Locale): String =
        NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 0 }
            .format(fraction.coerceIn(0.0, 1.0))
}
