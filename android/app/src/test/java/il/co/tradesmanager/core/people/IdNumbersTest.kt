package il.co.tradesmanager.core.people

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When two ID numbers are the same person's.
 *
 * This is the gate's rule, and the gate is where getting it wrong costs
 * something: a man who is already on the books reads as a stranger, gets added
 * again, and because setting an ID number is a one-way door the duplicate
 * keeps it. The same comparison is what stops one person being issued
 * another's number, so a miss lets that through too.
 *
 * The Arabic-Indic digits are written as escapes deliberately. A test that a
 * number typed on an Arabic keypad is found, with the digits pasted in
 * invisibly, is a test that stops meaning what it says.
 */
class IdNumbersTest {

    private val stored = "301234567"

    @Test
    fun `the same digits are the same person`() {
        assertTrue(IdNumbers.same(stored, stored))
        assertTrue(IdNumbers.same(stored, "  301234567  "))
    }

    @Test
    fun `typed on an arabic keypad`() {
        // This is the one that mattered. Formats puts Arabic-Indic digits on
        // an Arabic screen, so those are the digits that get typed back in.
        assertTrue(IdNumbers.same(stored, "\u0663\u0660\u0661\u0662\u0663\u0664\u0665\u0666\u0667"))
        assertEquals(stored, IdNumbers.canonical("\u0663\u0660\u0661\u0662\u0663\u0664\u0665\u0666\u0667"))
    }

    @Test
    fun `read off a document, with its separators`() {
        assertTrue(IdNumbers.same(stored, "301-234-567"))
        assertTrue(IdNumbers.same(stored, "301 234 567"))
        assertTrue(IdNumbers.same(stored, "301/234/567"))
    }

    @Test
    fun `pasted with an invisible direction mark`() {
        assertTrue(IdNumbers.same(stored, "\u200F" + "301234567"))
        assertTrue(IdNumbers.same(stored, "301234567" + "\u200F"))
    }

    @Test
    fun `a different number is a different person`() {
        assertFalse(IdNumbers.same(stored, "301234568"))
        assertFalse(IdNumbers.same(stored, "30123456"))
        // Not a prefix match, not a substring match.
        assertFalse(IdNumbers.same(stored, "3012345670"))
    }

    @Test
    fun `nothing to compare finds nobody`() {
        assertNull(IdNumbers.canonical(null))
        assertNull(IdNumbers.canonical(""))
        assertNull(IdNumbers.canonical("   "))
        // A string with no digits in it at all is not a number.
        assertNull(IdNumbers.canonical("no digits"))
    }

    @Test
    fun `two people with no number are not the same person`() {
        // Most people on a site have no number on their row yet. Treating
        // "neither has one" as a match would merge the whole crew.
        assertFalse(IdNumbers.same(null, null))
        assertFalse(IdNumbers.same("", ""))
        assertFalse(IdNumbers.same(stored, null))
        assertFalse(IdNumbers.same(null, stored))
    }

    @Test
    fun `it does not judge whether the number is a real one`() {
        // A foreign passport, an army number, a number somebody mistyped: all
        // things a site office has to be able to write down. A gate that
        // refuses a real person because their document is not Israeli is worse
        // than one that records what it was told.
        assertEquals("12", IdNumbers.canonical("12"))
        assertEquals("9876543210987", IdNumbers.canonical("9876543210987"))
        assertEquals("123456789", IdNumbers.canonical("AB-123456789"))
    }
}
