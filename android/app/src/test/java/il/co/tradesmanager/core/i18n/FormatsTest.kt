package il.co.tradesmanager.core.i18n

import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatsTest {

    private val hebrew: Locale = Locale.forLanguageTag("he")
    private val english: Locale = Locale.forLanguageTag("en")

    @Test
    fun `dates use the Israeli day-month-year order`() {
        val date = LocalDate.of(2026, 9, 3)
        assertEquals("03/09/2026", Formats.date(date, english))
        assertEquals("03/09/2026", Formats.date(date, hebrew))
    }

    @Test
    fun `times use a 24 hour clock with no meridiem`() {
        val afternoon = LocalTime.of(17, 5)
        assertEquals("17:05", Formats.time(afternoon, english))
        assertEquals("17:05", Formats.time(afternoon, hebrew))
    }

    @Test
    fun `money is always shekels regardless of the display language`() {
        assertEquals("ILS", Formats.ILS.currencyCode)
        listOf(english, hebrew).forEach { locale ->
            val formatted = Formats.money(1234.5, locale)
            assertTrue("no amount in $formatted", formatted.contains("1") && formatted.contains("5"))
        }
    }

    @Test
    fun `whole quantities print without a decimal tail`() {
        assertEquals("12", Formats.quantity(12.0, english))
        assertEquals("12.5", Formats.quantity(12.5, english))
    }

    @Test
    fun `percent clamps out of range progress instead of overflowing the bar`() {
        assertEquals(Formats.percent(1.0, english), Formats.percent(1.4, english))
        assertEquals(Formats.percent(0.0, english), Formats.percent(-0.2, english))
    }
}
