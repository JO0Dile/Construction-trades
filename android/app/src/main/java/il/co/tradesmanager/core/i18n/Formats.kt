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
    private const val PARSE_PATTERN = "dd/MM/uuuu"
    private const val TIME_PATTERN = "HH:mm"

    fun date(date: LocalDate, locale: Locale): String =
        DateTimeFormatter.ofPattern(DATE_PATTERN, locale).format(date)

    fun time(time: LocalTime, locale: Locale): String =
        DateTimeFormatter.ofPattern(TIME_PATTERN, locale).format(time)

    /**
     * Reads a date somebody typed as DD/MM/YYYY.
     *
     * Parsed under [Locale.ROOT] and strictly: the pattern is fixed Israeli
     * convention rather than the device's, and 31/02 has to fail rather than
     * quietly become 3 March. Null means "that is not a date", which the
     * caller shows as an error instead of storing a wrong one.
     */
    fun parseDate(text: String): LocalDate? = runCatching {
        LocalDate.parse(
            text.trim(),
            // "uuuu", not "yyyy": under a strict resolver, year-of-era needs an
            // era field and every date would fail to parse. This is the one
            // difference between the display pattern and the parse pattern.
            DateTimeFormatter.ofPattern(PARSE_PATTERN, Locale.ROOT)
                .withResolverStyle(java.time.format.ResolverStyle.STRICT)
                .withChronology(java.time.chrono.IsoChronology.INSTANCE),
        )
    }.getOrNull()

    fun dateTime(date: LocalDate, time: LocalTime, locale: Locale): String =
        "${date(date, locale)} ${time(time, locale)}"

    /** Money, always in shekels — the app does not pretend to multi-currency. */
    fun money(amount: Double, locale: Locale): String =
        NumberFormat.getCurrencyInstance(locale).apply {
            currency = ILS
            maximumFractionDigits = 2
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
