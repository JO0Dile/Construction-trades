package il.co.tradesmanager.core.safety

import il.co.tradesmanager.core.safety.EmergencySheet.Essential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencySheetTest {

    private val complete = EmergencySheet.Sheet(
        hospitalName = "Soroka",
        assemblyPoint = "Car park by the gate",
        firstAiders = "Yossi, Samir",
        siteContactPhone = "054-1234567",
    )

    @Test
    fun `a job with no sheet is missing every essential, in the order they are shown`() {
        assertEquals(Essential.entries.toList(), EmergencySheet.missing(null))
        assertEquals(Essential.entries.toList(), EmergencySheet.missing(EmergencySheet.Sheet()))
    }

    @Test
    fun `the four essentials make it complete, and blank is missing whatever else is filled`() {
        assertTrue(EmergencySheet.missing(complete).isEmpty())
        assertEquals(listOf(Essential.FIRST_AIDERS), EmergencySheet.missing(complete.copy(firstAiders = "  ")))
        assertEquals(
            "the name without a number is no use to somebody who has to ring",
            listOf(Essential.SITE_CONTACT),
            EmergencySheet.missing(complete.copy(siteContactPhone = "", siteContactName = "Avi")),
        )
    }

    @Test
    fun `a phone field holds something dialable or nothing`() {
        assertTrue(EmergencySheet.isDialable("054-1234567"))
        assertTrue(EmergencySheet.isDialable("+972 54 123 4567"))
        assertTrue(EmergencySheet.isDialable("(08) 640-0111"))
        assertTrue(EmergencySheet.isDialable("101"))
        assertFalse(EmergencySheet.isDialable("ask Avi"))
        assertFalse(EmergencySheet.isDialable("12"))
        assertFalse(EmergencySheet.isDialable("054+123"))
        assertNull("blank is not known yet, not wrong", EmergencySheet.refusal(complete.copy(hospitalPhone = "")))
        assertEquals(EmergencySheet.Refusal.NOT_A_PHONE_NUMBER, EmergencySheet.refusal(complete.copy(hospitalPhone = "the one on the sign")))
        assertEquals(EmergencySheet.Refusal.NOT_A_PHONE_NUMBER, EmergencySheet.refusal(complete.copy(siteContactPhone = "Avi")))
    }

    @Test
    fun `what is dialled is the digits, with the plus kept if it led`() {
        assertEquals("0541234567", EmergencySheet.dialString(" 054-123 4567 "))
        assertEquals("+972541234567", EmergencySheet.dialString("+972 (54) 123-4567"))
    }
}
