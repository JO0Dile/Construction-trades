package il.co.tradesmanager.core.i18n

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * In-app language switching.
 *
 * AppCompat's per-app locales are the whole implementation: on Android 13 and
 * later this hands the choice to the system language picker, and below it
 * AppCompat persists and re-applies it. Either way the activity is recreated
 * with the new resources, so every string, date, number and layout direction
 * changes at once — there is no list of things to remember to refresh.
 */
object LocaleController {

    /** The tag currently in force, or the system default when none is set. */
    fun currentTag(): String =
        AppCompatDelegate.getApplicationLocales()[0]?.toLanguageTag()
            ?: Locale.getDefault().toLanguageTag()

    fun currentLocale(): Locale = Locale.forLanguageTag(currentTag())

    fun isRightToLeft(): Boolean = AppLanguages.isRightToLeft(currentLocale())

    fun apply(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }

    /** Hands the choice back to the device's own language setting. */
    fun followSystem() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }
}
