package il.co.tradesmanager.core.people

import il.co.tradesmanager.core.people.Verification.Challenge
import il.co.tradesmanager.core.people.Verification.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/**
 * The rules for a code sent to somebody's phone.
 *
 * Nothing sends one yet — there is no server, and `docs/SERVER.md` explains at
 * length why the app cannot send it to itself. These are the rules that have
 * to be right before anything does, and most of them are the kind that are
 * only ever discovered to be wrong by the person they lock out.
 *
 * The Arabic-Indic digits are written as escapes on purpose. A test that a
 * code typed on an Arabic keypad is accepted, with the digits pasted in
 * invisibly, is a test that stops meaning what it says the moment somebody
 * reformats the file.
 */
class VerificationTest {

    private val sentAt = 1_700_000_000_000L
    private val code = "301234"
    private val challenge = Verification.issue("+972500000000", code, sentAt)

    /* ------------------------------------------------------- the code itself */

    @Test
    fun `a code is always six digits, leading zeros kept`() {
        // Seeded so the assertion is about the rule and not about luck.
        val random = SecureRandom.getInstance("SHA1PRNG").apply { setSeed(ByteArray(8)) }
        repeat(2_000) {
            val generated = Verification.newCode(random)
            assertEquals(Verification.CODE_DIGITS, generated.length)
            assertTrue("not plain digits: $generated", generated.all { it in '0'..'9' })
        }
    }

    @Test
    fun `a code is not the same one every time`() {
        val random = SecureRandom()
        val drawn = (1..500).map { Verification.newCode(random) }.toSet()
        // Five hundred draws from a million: a handful of collisions is
        // ordinary, five hundred identical values is a broken generator.
        assertTrue("only ${drawn.size} distinct codes in 500 draws", drawn.size > 400)
    }

    @Test
    fun `the code is not stored`() {
        // The hash is a hash, not the digits with a coat on.
        assertFalse(challenge.codeHash.contains(code))
        assertFalse(challenge.codeSalt.contains(code))
        assertTrue(challenge.codeHash.isNotBlank())
        assertTrue(challenge.codeSalt.isNotBlank())
    }

    /* ------------------------------------------------ how people type it back */

    @Test
    fun `typed on an arabic keypad`() {
        // The one that matters. Formats puts Arabic-Indic digits on an Arabic
        // screen, so those are the digits the keypad gives back. This is the
        // same fault IdNumbers was written to fix at the gate, and it is the
        // reason these rules exist before the server rather than after it.
        assertEquals(
            Outcome.CORRECT,
            Verification.check(
                challenge,
                "٣٠١٢٣٤",
                sentAt,
            ),
        )
        assertEquals(
            Outcome.CORRECT,
            Verification.check(
                challenge,
                "۳۰۱۲۳۴",
                sentAt,
            ),
        )
    }

    @Test
    fun `typed the way an sms reads`() {
        assertEquals(Outcome.CORRECT, Verification.check(challenge, "301 234", sentAt))
        assertEquals(Outcome.CORRECT, Verification.check(challenge, " 301234 ", sentAt))
        assertEquals(Outcome.CORRECT, Verification.check(challenge, "301-234", sentAt))
    }

    @Test
    fun `pasted out of a message with its direction mark`() {
        assertEquals(Outcome.CORRECT, Verification.check(challenge, "‏" + code, sentAt))
        assertEquals(Outcome.CORRECT, Verification.check(challenge, code + "‎", sentAt))
    }

    @Test
    fun `wrong is wrong, and no prefix counts`() {
        for (wrong in listOf("301235", "30123", "3012345", "", "abcdef", "30123a")) {
            assertEquals("'$wrong' should be refused", Outcome.WRONG, Verification.check(challenge, wrong, sentAt))
        }
    }

    /* ----------------------------------------------------------------- expiry */

    @Test
    fun `good until the last millisecond and not after it`() {
        val lastMoment = sentAt + Verification.LIFETIME_MS - 1
        assertEquals(Outcome.CORRECT, Verification.check(challenge, code, lastMoment))
        assertEquals(
            Outcome.EXPIRED,
            Verification.check(challenge, code, sentAt + Verification.LIFETIME_MS),
        )
        assertEquals(sentAt + Verification.LIFETIME_MS, Verification.expiresAt(challenge))
    }

    @Test
    fun `an expired code says so whether the digits were right or not`() {
        // Neither answer tells somebody grinding codes whether they were close.
        val after = sentAt + Verification.LIFETIME_MS
        assertEquals(Outcome.EXPIRED, Verification.check(challenge, code, after))
        assertEquals(Outcome.EXPIRED, Verification.check(challenge, "000000", after))
    }

    /* --------------------------------------------------------------- attempts */

    @Test
    fun `the last attempt still works`() {
        val nearlySpent = challenge.copy(attempts = Verification.MOST_ATTEMPTS - 1)
        assertEquals(Outcome.CORRECT, Verification.check(nearlySpent, code, sentAt))
    }

    @Test
    fun `the right code does not rescue a burnt challenge`() {
        // Somebody who has guessed five times and then produced the right six
        // digits is exactly the case the attempt count exists to stop.
        val spent = challenge.copy(attempts = Verification.MOST_ATTEMPTS)
        assertEquals(Outcome.TOO_MANY_ATTEMPTS, Verification.check(spent, code, sentAt))
    }

    @Test
    fun `a correct code spends an attempt too`() {
        // A count that only moves on failure is a count that can be reset by
        // guessing right once.
        assertEquals(1, Verification.spendAttempt(challenge).attempts)
        assertEquals(6, Verification.spendAttempt(challenge.copy(attempts = 5)).attempts)
    }

    @Test
    fun `five wrong guesses is what it takes`() {
        var current = challenge
        repeat(Verification.MOST_ATTEMPTS) {
            assertEquals(Outcome.WRONG, Verification.check(current, "000000", sentAt))
            current = Verification.spendAttempt(current)
        }
        assertEquals(Outcome.TOO_MANY_ATTEMPTS, Verification.check(current, code, sentAt))
    }

    /* ----------------------------------------------------------------- resend */

    @Test
    fun `nothing sent yet means a code may be sent`() {
        assertTrue(Verification.mayResend(null, sentAt))
    }

    @Test
    fun `resend waits, then allows`() {
        assertFalse(Verification.mayResend(challenge, sentAt))
        assertFalse(Verification.mayResend(challenge, sentAt + Verification.RESEND_AFTER_MS - 1))
        assertTrue(Verification.mayResend(challenge, sentAt + Verification.RESEND_AFTER_MS))
    }

    @Test
    fun `a number that burnt its tries waits much longer`() {
        val spent = challenge.copy(attempts = Verification.MOST_ATTEMPTS)
        assertTrue(
            "cooling off must be longer than an ordinary resend",
            Verification.COOLING_OFF_MS > Verification.RESEND_AFTER_MS,
        )
        assertFalse(Verification.mayResend(spent, sentAt + Verification.RESEND_AFTER_MS))
        assertFalse(Verification.mayResend(spent, sentAt + Verification.COOLING_OFF_MS - 1))
        assertTrue(Verification.mayResend(spent, sentAt + Verification.COOLING_OFF_MS))
    }

    /* ------------------------------------------------------------ the grind */

    @Test
    fun `one live code is not worth guessing at`() {
        // Not "years to walk the code space" — a code only lives ten minutes,
        // so nobody walks it. What an attacker gets is MOST_ATTEMPTS guesses
        // against each live code, and a fresh one costs them a whole
        // cooling-off period, because burning the attempts is what forces the
        // resend.
        var space = 1L
        repeat(Verification.CODE_DIGITS) { space *= 10 }
        assertTrue(
            "one live code is guessable at 1 in ${space / Verification.MOST_ATTEMPTS}",
            space / Verification.MOST_ATTEMPTS >= 100_000,
        )

        // Stated so that raising the attempts "to be friendlier" has to argue
        // with this line: the attempts are what buys the strength, not the
        // length of the code.
        assertTrue(Verification.MOST_ATTEMPTS * 20 <= space / 1_000)
    }

    /* ------------------------------------------------------- what may be sent */

    @Test
    fun `a number with no digits in it is not worth sending to`() {
        assertFalse(Verification.sendableTo(null))
        assertFalse(Verification.sendableTo(""))
        assertFalse(Verification.sendableTo("   "))
        assertFalse(Verification.sendableTo("call me"))
        assertTrue(Verification.sendableTo("+972 50-000-0000"))
        // Written on an Arabic screen, which is where this would have failed.
        assertTrue(Verification.sendableTo("٠٥٠٠٠٠٠٠٠٠"))
    }

    @Test
    fun `a challenge with no hash refuses everything`() {
        // Not "accepts everything", which is what Passcode.verify does with a
        // blank hash by design — an account with no passcode is opened by
        // anybody. A verification code with no hash is a broken row, and the
        // safe reading of a broken row is no.
        val broken = Challenge(phone = "+97250", codeHash = "", codeSalt = "", sentAt = sentAt)
        assertEquals(Outcome.WRONG, Verification.check(broken, code, sentAt))
        assertEquals(Outcome.WRONG, Verification.check(broken, "", sentAt))
    }
}
