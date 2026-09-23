package il.co.tradesmanager.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Code points built with `Char(code)` rather than escapes, for the reason the
 * parser's own documentation gives.
 */
class NumbersTest {

    private fun chars(vararg codes: Int) = codes.joinToString("") { Char(it).toString() }

    private fun parses(text: String, expected: Double) =
        assertEquals("'$text'", expected, Numbers.parseDecimal(text)!!, 0.0)

    @Test
    fun `ascii digits read as they always did`() {
        parses("35", 35.0)
        parses("-5", -5.0)
        parses("+7", 7.0)
        parses(" 12 ", 12.0)
        parses(".5", 0.5)
        parses("5.", 5.0)
    }

    @Test
    fun `arabic-indic digits are read, which they were not before`() {
        parses(chars(0x0663, 0x0665), 35.0)
        parses(chars(0x0663, 0x0665, 0x066B, 0x0665), 35.5)
        parses(chars(0x06F4, 0x06F2), 42.0)
    }

    @Test
    fun `a comma is a decimal point`() {
        parses("33,5", 33.5)
    }

    @Test
    fun `the unicode minus sign is a minus`() {
        parses(chars(0x2212) + "5", -5.0)
    }

    @Test
    fun `thousands separators and pasted spaces are dropped`() {
        parses("1 200", 1200.0)
        parses("1" + chars(0x066C) + "200", 1200.0)
        parses("1" + chars(0x00A0) + "200", 1200.0)
    }

    @Test
    fun `two decimal points is two possible numbers, so it is none`() {
        assertNull(Numbers.parseDecimal("1.200,50"))
    }

    @Test
    fun `things the JVM would parse and nobody means are refused`() {
        listOf("NaN", "Infinity", "0x1p3", "1e3", "5d", "--5", "5-", "", ".", "abc").forEach {
            assertNull("'$it'", Numbers.parseDecimal(it))
        }
    }

    @Test
    fun `whole numbers are read in any digits, and a fraction is not one`() {
        assertEquals(12L, Numbers.parseWhole("12"))
        assertEquals(12L, Numbers.parseWhole(chars(0x0661, 0x0662)))
        assertEquals(12L, Numbers.parseWhole("12.0"))
        assertEquals(-3L, Numbers.parseWhole("-3"))
        assertNull(Numbers.parseWhole("2.5"))
        assertNull(Numbers.parseWhole("abc"))
        assertNull(Numbers.parseWhole(""))
    }

    @Test
    fun `a decimal field keeps the comma, so seven and a half stays seven and a half`() {
        // The filter it replaces kept digits and full stops only, and turned
        // this into 75 without a word.
        assertEquals("7,5", Numbers.typingDecimal("7,5"))
        assertEquals(7.5, Numbers.parseDecimal(Numbers.typingDecimal("7,5"))!!, 0.0)

        val arabic = chars(0x0662, 0x066B, 0x0665)
        assertEquals(arabic, Numbers.typingDecimal(arabic))
        assertEquals(2.5, Numbers.parseDecimal(Numbers.typingDecimal(arabic))!!, 0.0)
    }

    @Test
    fun `a decimal field drops what is not a number, and a minus only where allowed`() {
        assertEquals("2.5", Numbers.typingDecimal("abc2.5x"))
        assertEquals("1200", Numbers.typingDecimal("1 200"))
        assertEquals("5", Numbers.typingDecimal("-5"))
        assertEquals("-5", Numbers.typingDecimal("-5", allowNegative = true))
        assertEquals("5", Numbers.typingDecimal("5-", allowNegative = true))
        assertEquals(-5.0, Numbers.parseDecimal(Numbers.typingDecimal("-5", allowNegative = true))!!, 0.0)
    }
}
