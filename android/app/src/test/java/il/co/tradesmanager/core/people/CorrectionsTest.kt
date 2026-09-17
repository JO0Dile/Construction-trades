package il.co.tradesmanager.core.people

import il.co.tradesmanager.core.people.Corrections.Details
import il.co.tradesmanager.core.people.Corrections.Fault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules the office corrects a crew list by.
 *
 * Most people on a site never open this app: somebody on the gate put them on
 * the books with a name heard once and a number read off a scrap of paper. So
 * the correction path has to work, and the one thing it must never do is
 * quietly move an ID number from one person to another.
 */
class CorrectionsTest {

    private val held = Details(
        displayName = "Hammam",
        phone = "050-123-4567",
        email = null,
        idNumber = "301234567",
    )

    private fun proposal(
        name: String = held.displayName,
        phone: String? = held.phone,
        email: String? = held.email,
        idNumber: String? = held.idNumber,
    ) = Details(name, phone, email, idNumber)

    /* ------------------------------------------------------------- the name */

    @Test
    fun `a name can be corrected`() {
        val proposed = proposal(name = "Hammam Zoabi")
        assertNull(Corrections.blocks(held, proposed))
        assertEquals("Hammam Zoabi", Corrections.applied(held, proposed).displayName)
    }

    @Test
    fun `a blank name is refused`() {
        assertEquals(Fault.NameIsBlank, Corrections.blocks(held, proposal(name = "")))
        assertEquals(Fault.NameIsBlank, Corrections.blocks(held, proposal(name = "   ")))
    }

    @Test
    fun `a name is trimmed rather than stored with its spaces`() {
        val proposed = proposal(name = "  Hammam  ")
        assertNull(Corrections.blocks(held, proposed))
        assertEquals("Hammam", Corrections.applied(held, proposed).displayName)
        // And so it is not counted as a change, which would write an audit row
        // saying a name was corrected to the name it already was.
        assertTrue(Corrections.changed(held, Corrections.applied(held, proposed)).isEmpty())
    }

    /* ------------------------------------------------------------ the phone */

    @Test
    fun `a number that is not a number is refused`() {
        val fault = Corrections.blocks(held, proposal(phone = "ring the office"))
        assertEquals(Fault.BadPhone(Contact.PhoneFault.NOT_A_NUMBER), fault)
    }

    @Test
    fun `a number too short to ring is refused`() {
        assertEquals(
            Fault.BadPhone(Contact.PhoneFault.TOO_SHORT),
            Corrections.blocks(held, proposal(phone = "0501")),
        )
    }

    @Test
    fun `an empty phone number is allowed and means not recorded`() {
        // Every account made before the app asked for one has none. Requiring
        // it here would make those people's names uncorrectable.
        val proposed = proposal(phone = "")
        assertNull(Corrections.blocks(held, proposed))
        assertNull(Corrections.applied(held, proposed).phone)
    }

    @Test
    fun `a foreign number keeps its country code`() {
        // Romanian, Moldovan, Chinese, Thai, Ukrainian and Palestinian crews
        // all work these sites. See Contact: this is not an Israeli-only field.
        val proposed = proposal(phone = "+40 721 234 567")
        assertNull(Corrections.blocks(held, proposed))
        assertEquals("+40 721 234 567", Corrections.applied(held, proposed).phone)
    }

    /* ------------------------------------------------------------ the email */

    @Test
    fun `an address that is not one is refused`() {
        val fault = Corrections.blocks(held, proposal(email = "hammam at example"))
        assertTrue(fault is Fault.BadEmail)
    }

    @Test
    fun `no email address is allowed`() {
        assertNull(Corrections.blocks(held, proposal(email = "")))
        assertNull(Corrections.blocks(held, proposal(email = null)))
    }

    /* -------------------------------------------------------- the ID number */

    @Test
    fun `a missing ID number can be filled in`() {
        val blank = held.copy(idNumber = null)
        val proposed = proposal(idNumber = "301234567")
        assertNull(Corrections.blocks(blank, proposed))
        assertEquals("301234567", Corrections.applied(blank, proposed).idNumber)
    }

    @Test
    fun `an ID number that is already set cannot be changed`() {
        // The number on the gate list and on the induction record. Editing it
        // is how one person ends up standing behind another person's paperwork.
        assertEquals(
            Fault.IdNumberIsSet,
            Corrections.blocks(held, proposal(idNumber = "409876543")),
        )
    }

    @Test
    fun `sending the same ID number back is not a change`() {
        // What a screen that shows the number in a disabled box will do.
        assertNull(Corrections.blocks(held, proposal(idNumber = "  301234567  ")))
        assertEquals("301234567", Corrections.applied(held, proposal()).idNumber)
    }

    @Test
    fun `a set ID number survives a proposal that omits it`() {
        val proposed = proposal(idNumber = "")
        assertNull(Corrections.blocks(held, proposed))
        assertEquals("301234567", Corrections.applied(held, proposed).idNumber)
    }

    @Test
    fun `a bad screen cannot overwrite an ID number even if blocks is skipped`() {
        // applied() is deliberately not a second copy of the rule: the held
        // number wins whatever it is handed, so a caller that forgets to ask
        // blocks() first still cannot move a number between two people.
        assertEquals(
            "301234567",
            Corrections.applied(held, proposal(idNumber = "409876543")).idNumber,
        )
    }

    /* ------------------------------------------------------ what to record */

    @Test
    fun `nothing changed is nothing recorded`() {
        assertTrue(Corrections.changed(held, Corrections.applied(held, proposal())).isEmpty())
    }

    @Test
    fun `every changed field is named for the audit trail`() {
        val blank = held.copy(idNumber = null)
        val proposed = Details("Hammam Zoabi", "+40721234567", "h@example.com", "301234567")
        assertEquals(
            listOf("name", "phone", "email", "ID number"),
            Corrections.changed(blank, Corrections.applied(blank, proposed)),
        )
    }

    @Test
    fun `whitespace typed over an empty box is not a change`() {
        val blank = held.copy(phone = null, email = null, idNumber = null)
        val proposed = Details(blank.displayName, "   ", "  ", " ")
        assertTrue(Corrections.changed(blank, Corrections.applied(blank, proposed)).isEmpty())
    }

    @Test
    fun `tidy makes blank and absent the same answer`() {
        assertNull(Corrections.tidy(null))
        assertNull(Corrections.tidy(""))
        assertNull(Corrections.tidy("   "))
        assertEquals("0501234567", Corrections.tidy("  0501234567 "))
    }
}
