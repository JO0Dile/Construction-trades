package il.co.tradesmanager.core.work

import il.co.tradesmanager.core.work.Contacts.Kind
import il.co.tradesmanager.core.work.Contacts.Refusal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsTest {

    @Test
    fun `a contact has a name and a way to reach them that is one`() {
        assertEquals(Refusal.BLANK_NAME, Contacts.refusal(" ", "054-1234567", ""))
        assertEquals(Refusal.NO_WAY_TO_REACH, Contacts.refusal("Noa", " ", " "))
        assertEquals(Refusal.NOT_A_PHONE_NUMBER, Contacts.refusal("Noa", "ask at the gate", ""))
        assertEquals(Refusal.NOT_AN_EMAIL, Contacts.refusal("Noa", "", "054-1234567"))
        assertNull(Contacts.refusal("Noa", "054-1234567", ""))
        assertNull(Contacts.refusal("Noa", "", "noa@studio.co.il"))
    }

    @Test
    fun `an email has one at, a dotted domain and no spaces`() {
        assertTrue(Contacts.looksLikeEmail("noa@studio.co.il"))
        assertTrue(Contacts.looksLikeEmail(" supervisor@city.gov.il "))
        assertFalse(Contacts.looksLikeEmail("noa@studio"))
        assertFalse(Contacts.looksLikeEmail("@studio.co.il"))
        assertFalse(Contacts.looksLikeEmail("noa@@studio.co.il"))
        assertFalse(Contacts.looksLikeEmail("noa @studio.co.il"))
        assertFalse(Contacts.looksLikeEmail("noa@studio."))
    }

    @Test
    fun `listed by what they are on the job, then by name, and those who left last`() {
        val rows = listOf(
            Triple(Kind.SUBCONTRACTOR, "Cohen Electric", false),
            Triple(Kind.CLIENT, "Ramat Homes", false),
            Triple(Kind.SUPERVISOR, "old supervisor", true),
            Triple(Kind.SUBCONTRACTOR, "Avraham Plumbing", false),
            Triple(Kind.SUPERVISOR, "Dana", false),
        )
        assertEquals(listOf(1, 4, 3, 0, 2), Contacts.order(rows))
        assertEquals(Kind.MUNICIPALITY, Contacts.kindOf("MUNICIPALITY"))
        assertEquals(Kind.OTHER, Contacts.kindOf("NEIGHBOUR"))
    }
}
