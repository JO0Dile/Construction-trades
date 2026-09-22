package il.co.tradesmanager.core.people

import il.co.tradesmanager.core.find.Search
import il.co.tradesmanager.core.security.Passcode
import java.security.SecureRandom

/**
 * The rules for a code sent to somebody's phone.
 *
 * Built now and connected to nothing, on purpose. An SMS has to be sent by
 * something that is not the phone receiving it, and there is no server yet —
 * `docs/SERVER.md` says at length why sending it from the app proves nothing
 * and why an SMS gateway key inside an APK is a bill waiting to happen. There
 * is deliberately no "enter the code we sent you" screen, because no code was
 * sent and a screen that asks for one teaches people the app lies to them.
 *
 * What is here is the part that is the same wherever it runs, and that has to
 * be right before anything sends anything: how long a code lives, how many
 * wrong tries it takes, how long a number has to wait afterwards, and what
 * counts as the same six digits.
 *
 * That last one is why this is worth writing before the server rather than
 * after. `Formats` puts Arabic-Indic digits on an Arabic screen, so the keypad
 * a man is holding gives back ٣٠١٢٣٤ for a code the server generated as
 * 301234, and comparing those as text says no. That is exactly the bug
 * [IdNumbers] was written to fix at the gate, found there after it had
 * shipped. A verification that rejects every Arabic-speaking worker would be
 * the same fault a second time, so the folding is here from the start and
 * tested.
 *
 * **The rate limit belongs on the server.** Six digits is a million guesses,
 * which is an afternoon for a script, and the only thing standing between a
 * number and that afternoon is the attempt count. A limit enforced in the app
 * is not a limit: the app is the thing being attacked. These constants are
 * the rule; the server has to be the one applying it.
 *
 * **And verification gates nothing that keeps somebody safe.** An unverified
 * number is a number nobody has confirmed, not a person who may not sign an
 * induction or report a near miss. Same rule as `core.money.Plans`.
 */
object Verification {

    /**
     * Six digits, which is what people expect and what an SMS reads well as.
     *
     * The length is not the security — [MOST_ATTEMPTS] is. Eight digits with
     * unlimited tries is weaker than four with five.
     */
    const val CODE_DIGITS = 6

    /** Ten minutes: long enough for a slow SMS, short enough to be worthless later. */
    const val LIFETIME_MS = 10 * 60 * 1000L

    /** Five wrong tries and this code is finished. */
    const val MOST_ATTEMPTS = 5

    /**
     * How long a number waits after burning through its tries.
     *
     * Against the number, not the device. Somebody grinding codes reinstalls
     * the app between attempts; the number is the thing being attacked and
     * the thing that has to cool off.
     */
    const val COOLING_OFF_MS = 15 * 60 * 1000L

    /**
     * How long before another code may be sent to the same number.
     *
     * Short enough that somebody whose message genuinely did not arrive is
     * not stuck, long enough that "resend" is not a free way to make the app
     * send an unlimited number of paid messages to somebody else's phone.
     */
    const val RESEND_AFTER_MS = 60 * 1000L

    /**
     * A code that was sent, as it is worth storing.
     *
     * The code itself is not kept. It is hashed the same way a passcode is,
     * because a database that is read gives up every code in flight
     * otherwise, and because people reuse the digits they have just typed.
     */
    data class Challenge(
        val phone: String,
        val codeHash: String,
        val codeSalt: String,
        val sentAt: Long,
        val attempts: Int = 0,
    )

    /** What happened when somebody typed a code in. */
    enum class Outcome {
        CORRECT,

        /** Wrong digits, and an attempt is spent. */
        WRONG,

        /** The code was right once and is not any more. */
        EXPIRED,

        /**
         * [MOST_ATTEMPTS] have been spent.
         *
         * Returned even for the correct code. Somebody who has just guessed
         * five times and then produced the right six digits is the case this
         * exists to stop.
         */
        TOO_MANY_ATTEMPTS,
    }

    /**
     * A fresh code, uniformly distributed over every value of [CODE_DIGITS]
     * digits, leading zeros kept.
     *
     * `nextInt(bound)` rather than digit-by-digit: it is one call, it is
     * uniform, and it cannot be got subtly wrong the way a loop can. The
     * padding matters — dropping a leading zero would turn a sixth of all
     * codes into five digits, and the person typing would have no way to know
     * which they were sent.
     */
    fun newCode(random: SecureRandom = SecureRandom()): String {
        var bound = 1
        repeat(CODE_DIGITS) { bound *= 10 }
        return random.nextInt(bound).toString().padStart(CODE_DIGITS, '0')
    }

    /**
     * The comparable form of a typed code: its digits, in ASCII, and nothing
     * else.
     *
     * Spaces because an SMS reads "301 234" and people type it back that way.
     * Arabic-Indic and Extended Arabic-Indic digits because that is what the
     * keypad gives on an Arabic phone. Invisible direction marks because a
     * code pasted out of a message carries them.
     */
    fun typed(code: String): String = Search.digits(code)

    /** When the code stops working. */
    fun expiresAt(challenge: Challenge): Long = challenge.sentAt + LIFETIME_MS

    /**
     * Checks a typed code, in the order that gives away least.
     *
     * Expiry first, then the attempt count, then the digits. A challenge that
     * has expired says so whether or not the digits were right, and one that
     * has run out of tries says that — neither answer tells somebody grinding
     * codes whether they were close.
     */
    fun check(challenge: Challenge, code: String, now: Long): Outcome = when {
        now >= expiresAt(challenge) -> Outcome.EXPIRED
        challenge.attempts >= MOST_ATTEMPTS -> Outcome.TOO_MANY_ATTEMPTS
        matches(challenge, code) -> Outcome.CORRECT
        else -> Outcome.WRONG
    }

    /**
     * The challenge after a try, with the attempt spent.
     *
     * A separate call rather than something [check] does, because [check] is
     * pure and whoever stores the challenge is the one that can save the
     * count. A correct code spends an attempt too: it is about to be thrown
     * away, and a count that only moves on failure is a count that can be
     * reset by guessing right once.
     */
    fun spendAttempt(challenge: Challenge): Challenge =
        challenge.copy(attempts = challenge.attempts + 1)

    /**
     * Whether another code may be sent to this number yet.
     *
     * No challenge at all means yes — nothing has been sent. One that has run
     * out of tries has to wait [COOLING_OFF_MS] from when it was sent;
     * anything else waits [RESEND_AFTER_MS].
     */
    fun mayResend(challenge: Challenge?, now: Long): Boolean {
        if (challenge == null) return true
        val wait = if (challenge.attempts >= MOST_ATTEMPTS) COOLING_OFF_MS else RESEND_AFTER_MS
        return now >= challenge.sentAt + wait
    }

    /**
     * Whether this is a plausible six digits to send at all.
     *
     * Not a check that the number exists or can receive messages — only the
     * network finds that out. It stops the obvious waste: an empty field, or
     * a string with no digits in it.
     */
    fun sendableTo(phone: String?): Boolean = !Search.digits(phone.orEmpty()).isEmpty()

    private fun matches(challenge: Challenge, code: String): Boolean {
        if (challenge.codeHash.isBlank() || challenge.codeSalt.isBlank()) return false
        val digits = typed(code)
        if (digits.length != CODE_DIGITS) return false
        return Passcode.verify(digits, challenge.codeHash, challenge.codeSalt)
    }

    /**
     * A challenge for a freshly generated code.
     *
     * Hashing goes through [Passcode] rather than a second implementation,
     * because there should be exactly one place in this app that knows how a
     * secret is stored.
     */
    fun issue(phone: String, code: String, sentAt: Long): Challenge {
        val hashed = Passcode.hash(code)
        return Challenge(
            phone = phone,
            codeHash = hashed.hash,
            codeSalt = hashed.salt,
            sentAt = sentAt,
        )
    }
}
