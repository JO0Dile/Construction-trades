package il.co.tradesmanager.core.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * The passphrase for the encrypted local database.
 *
 * The key itself is 32 random bytes generated once on the device. It is stored
 * in EncryptedSharedPreferences, whose own master key lives in the Android
 * Keystore and never leaves it — so the passphrase is not in the APK, not in
 * source control, and not readable from a backup of the app's files. The
 * database file is unreadable without the device.
 */
class DatabaseKey(context: Context) {

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Existing passphrase, or a freshly generated one stored for next launch. */
    fun passphrase(): ByteArray {
        prefs.getString(KEY_PASSPHRASE, null)?.let {
            return Base64.decode(it, Base64.NO_WRAP)
        }
        val generated = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PASSPHRASE, Base64.encodeToString(generated, Base64.NO_WRAP))
            .apply()
        return generated
    }

    /**
     * Enterprise remote wipe: drop the key and the device copy becomes
     * undecryptable ciphertext, whether or not the file itself is reachable.
     */
    fun destroy() {
        prefs.edit().remove(KEY_PASSPHRASE).apply()
    }

    private companion object {
        const val PREFS_NAME = "tradesmanager_keys"
        const val KEY_PASSPHRASE = "db_passphrase"
        const val KEY_BYTES = 32
    }
}
