package il.co.tradesmanager.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Passcodes for the accounts on this device.
 *
 * The passcode is not what protects the data — SQLCipher and the Keystore do
 * that (see [DatabaseKey]). This stops the labourer who picks up the site
 * tablet from opening the finance lens, which is a different and more ordinary
 * problem. It still gets done properly: a stored passcode is a PBKDF2 hash with
 * a per-account salt, so a stolen database does not hand over the passcodes
 * people reuse elsewhere.
 *
 * Verification is constant-time. A timing oracle on a four-digit PIN is not the
 * likeliest attack on a building site, but comparing hashes with `==` is the
 * kind of thing that is free to get right and awkward to explain afterwards.
 */
object Passcode {

    /**
     * Four digits is what people will actually use with gloves on, so the cost
     * factor carries the weight instead of the length. 120k iterations is
     * roughly a tenth of a second on the low-end devices this app targets:
     * unnoticeable once at sign-in, and 120,000× the work for anyone grinding
     * through all ten thousand PINs.
     */
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"

    /** Short enough for gloves, long enough not to be guessed by a bystander. */
    const val MIN_LENGTH = 4

    data class Hashed(val hash: String, val salt: String)

    fun newSalt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return encode(bytes)
    }

    fun hash(passcode: String, salt: String = newSalt()): Hashed =
        Hashed(hash = encode(derive(passcode, decode(salt))), salt = salt)

    /**
     * True when [passcode] matches. A null hash means the account has no
     * passcode set — a one-person company should not have to type a PIN to see
     * their own stock — and anything verifies against it.
     */
    fun verify(passcode: String, hash: String?, salt: String?): Boolean {
        if (hash.isNullOrBlank() || salt.isNullOrBlank()) return true
        val candidate = runCatching { encode(derive(passcode, decode(salt))) }.getOrNull()
            ?: return false
        return MessageDigest.isEqual(
            candidate.toByteArray(Charsets.UTF_8),
            hash.toByteArray(Charsets.UTF_8),
        )
    }

    /**
     * Whether a typed password opens this account.
     *
     * Stricter than [verify] on the one case that matters once people sign in
     * by typing a name instead of picking it off a list: an account with no
     * passcode is opened by an empty password and by nothing else.
     *
     * With a picker, "no passcode" was a visible property of an account you
     * had already chosen. With a typed form, letting any password through
     * would mean anyone who knows a colleague's name is in — and on a shared
     * site tablet everybody knows everybody's name. Leaving the field blank is
     * still one tap for the sole trader who never set one.
     */
    fun opens(typed: String, hash: String?, salt: String?): Boolean =
        if (hash.isNullOrBlank() || salt.isNullOrBlank()) typed.isEmpty()
        else verify(typed, hash, salt)

    fun isAcceptable(passcode: String): Boolean =
        passcode.length >= MIN_LENGTH && passcode.none { it.isWhitespace() }

    private fun derive(passcode: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passcode.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    // NO_WRAP equivalent: the values go into a database column, not a MIME body.
    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)
}
