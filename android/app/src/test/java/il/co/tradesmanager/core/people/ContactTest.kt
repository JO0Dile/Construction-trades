package il.co.tradesmanager.core.people

import il.co.tradesmanager.core.people.Contact.EmailFault
import il.co.tradesmanager.core.people.Contact.PhoneFault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Who this form has to let through.
 *
 * Half of these are foreign numbers on purpose. The people on an Israeli site
 * least able to argue with a form that refuses them are the ones whose number
 * does not start 05.
 */
class ContactTest {

    @Test
    fun `the numbers people actually type are accepted`() {
        listOf(
            "0501234567",           // Israeli mobile
            "050-123-4567",         // with the hyphens
            "050 123 4567",         // with spaces
            "+972 50 123 4567",     // written out in full
            "+972-50-1234567",
            "03-1234567",           // an office landline
            "(03) 1234567",
            "+40 721 234 567",      // Romanian
            "+86 138 0013 8000",    // Chinese
            "+66 81 234 5678",      // Thai
            "+380 67 123 4567",     // Ukrainian
            "+972595551234",        // a West Bank mobile
        ).forEach { assertNull("$it should be accepted", Contact.blocksPhone(it)) }
    }

    @Test
    fun `a phone number is required`() {
        assertEquals(PhoneFault.MISSING, Contact.blocksPhone(""))
        assertEquals(PhoneFault.MISSING, Contact.blocksPhone("   "))
        assertEquals(PhoneFault.MISSING, Contact.blocksPhone(null))
    }

    @Test
    fun `holding one key down is not a phone number`() {
        // The cheapest way past a required field, and the reason the field
        // would otherwise collect a column of zeroes.
        assertEquals(PhoneFault.ONE_DIGIT_REPEATED, Contact.blocksPhone("0000000000"))
        assertEquals(PhoneFault.ONE_DIGIT_REPEATED, Contact.blocksPhone("1111111"))
        assertEquals(PhoneFault.ONE_DIGIT_REPEATED, Contact.blocksPhone("999-999-9999"))

        // But a memorable real number is not that, and must go through.
        assertNull(Contact.blocksPhone("052-2222222"))
    }

    @Test
    fun `a name typed into the phone box is named as that`() {
        assertEquals(PhoneFault.NOT_A_NUMBER, Contact.blocksPhone("ask the foreman"))
        assertEquals(PhoneFault.NOT_A_NUMBER, Contact.blocksPhone("050-1234567 ext 4"))
        // A + belongs at the front, as the country code, and nowhere else.
        assertEquals(PhoneFault.NOT_A_NUMBER, Contact.blocksPhone("050+1234567"))
    }

    @Test
    fun `too few digits to ring, and more than any number has`() {
        assertEquals(PhoneFault.TOO_SHORT, Contact.blocksPhone("12345"))
        assertEquals(PhoneFault.TOO_SHORT, Contact.blocksPhone("050-123"))
        assertEquals(PhoneFault.TOO_LONG, Contact.blocksPhone("+9721234567890123"))
    }

    @Test
    fun `a number is handed to the dialler as digits`() {
        assertEquals("0501234567", Contact.dialable("050-123-4567"))
        assertEquals("+972501234567", Contact.dialable("+972 50 123 4567"))
        assertEquals("031234567", Contact.dialable("(03) 1234567"))
    }

    @Test
    fun `only a number carrying its country code may be messaged`() {
        // The whole point of the rule. 0501234567 is Israeli to an Israeli and
        // Romanian to a Romanian, and both work this site. Guessing would
        // address a message to whoever holds that number in the country we
        // guessed.
        assertEquals("972501234567", Contact.international("+972-50-1234567"))
        assertEquals("40721234567", Contact.international("+40 721 234 567"))
        assertNull(Contact.international("0501234567"))
        assertNull(Contact.international("03-1234567"))
    }

    @Test
    fun `no email is a complete answer`() {
        // It is optional, and plenty of people on a site have never had one.
        assertNull(Contact.blocksEmail(""))
        assertNull(Contact.blocksEmail("   "))
        assertNull(Contact.blocksEmail(null))
    }

    @Test
    fun `an address given is an address that works`() {
        listOf(
            "yossi@gmail.com",
            "info@bnei-avraham.co.il",
            "a.b@sub.domain.org",
            "foreman+site3@example.com",
        ).forEach { assertNull("$it should be accepted", Contact.blocksEmail(it)) }
    }

    @Test
    fun `something that is not an address is refused`() {
        listOf(
            "yossi",                 // no at sign
            "@gmail.com",            // nothing in front of it
            "yossi@",                // nothing behind it
            "yossi@gmail",           // no dot in the domain
            "yossi@@gmail.com",      // two at signs
            "yossi @gmail.com",      // a space
            "yossi@.com",            // domain starts with the dot
            "yossi@gmail.",          // and ends with one
            "yossi@gmail..com",      // two dots together
        ).forEach { assertEquals("$it should be refused", EmailFault.NOT_AN_ADDRESS, Contact.blocksEmail(it)) }
    }
}
