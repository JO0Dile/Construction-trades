package il.co.tradesmanager.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasscodeTest {

    @Test
    fun `the right passcode verifies and a wrong one does not`() {
        val stored = Passcode.hash("4821")

        assertTrue(Passcode.verify("4821", stored.hash, stored.salt))
        assertFalse(Passcode.verify("4822", stored.hash, stored.salt))
        assertFalse(Passcode.verify("", stored.hash, stored.salt))
        assertFalse(Passcode.verify("48210", stored.hash, stored.salt))
    }

    @Test
    fun `the passcode itself is never what gets stored`() {
        val stored = Passcode.hash("4821")

        assertNotEquals("4821", stored.hash)
        assertFalse(stored.hash.contains("4821"))
    }

    @Test
    fun `two people with the same passcode do not get the same hash`() {
        // Without a per-account salt, one cracked PIN would unlock everyone who
        // happened to choose it — and on a site, several people will.
        val first = Passcode.hash("1234")
        val second = Passcode.hash("1234")

        assertNotEquals(first.salt, second.salt)
        assertNotEquals(first.hash, second.hash)
        assertTrue(Passcode.verify("1234", first.hash, first.salt))
        assertTrue(Passcode.verify("1234", second.hash, second.salt))
    }

    @Test
    fun `a hash only verifies against its own salt`() {
        val first = Passcode.hash("1234")
        val second = Passcode.hash("1234")

        assertFalse(Passcode.verify("1234", first.hash, second.salt))
    }

    @Test
    fun `an account with no passcode lets anyone in`() {
        // Deliberate: a sole trader should not have to type a PIN to look at
        // their own stock. The absence of a passcode is a decision, not a gap.
        assertTrue(Passcode.verify("", null, null))
        assertTrue(Passcode.verify("anything", null, null))
        assertTrue(Passcode.verify("", "", ""))
    }

    @Test
    fun `a corrupted salt refuses rather than admitting`() {
        val stored = Passcode.hash("1234")

        assertFalse(Passcode.verify("1234", stored.hash, "not base64 at all!!"))
    }

    @Test
    fun `four digits is the floor and whitespace is not a passcode`() {
        assertTrue(Passcode.isAcceptable("1234"))
        assertTrue(Passcode.isAcceptable("918273"))
        assertFalse(Passcode.isAcceptable("123"))
        assertFalse(Passcode.isAcceptable(""))
        assertFalse(Passcode.isAcceptable("12 34"))
    }

    @Test
    fun `non-digit passcodes work, because some people will use words`() {
        val stored = Passcode.hash("סיסמה")

        assertTrue(Passcode.verify("סיסמה", stored.hash, stored.salt))
        assertFalse(Passcode.verify("סיסמא", stored.hash, stored.salt))
    }
}
