package il.co.tradesmanager.core.i18n

import android.content.Context
import android.text.TextUtils
import android.view.View
import androidx.annotation.XmlRes
import il.co.tradesmanager.R
import java.util.Locale
import org.xmlpull.v1.XmlPullParser

/**
 * A language the app ships translations for.
 *
 * [endonym] is the language's name in itself ("עברית", "العربية"), which is what
 * a language picker has to show: a Hebrew speaker looking for Hebrew is not
 * helped by the word "Hebrew".
 */
data class AppLanguage(
    val tag: String,
    val endonym: String,
    val isRightToLeft: Boolean,
) {
    val locale: Locale get() = Locale.forLanguageTag(tag)
}

/**
 * The list of shipped languages, read at runtime from res/xml/locales_config.xml.
 *
 * Nothing here enumerates Hebrew, Arabic or English. Adding Russian is: one
 * `<locale>` line in that XML, a `values-ru/strings.xml` generated from the
 * shared catalogue, and the code in `resourceConfigurations`. The picker, the
 * layout direction and the display name all follow from the platform.
 */
object AppLanguages {

    fun supported(context: Context): List<AppLanguage> =
        parseLocaleConfig(context, R.xml.locales_config).map { tag ->
            val locale = Locale.forLanguageTag(tag)
            AppLanguage(
                tag = tag,
                endonym = locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) },
                isRightToLeft = isRightToLeft(locale),
            )
        }

    fun isRightToLeft(locale: Locale): Boolean =
        TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL

    private fun parseLocaleConfig(context: Context, @XmlRes resId: Int): List<String> {
        val tags = mutableListOf<String>()
        context.resources.getXml(resId).use { parser ->
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "locale") {
                    parser.getAttributeValue(ANDROID_NS, "name")
                        ?.takeIf { it.isNotBlank() }
                        ?.let(tags::add)
                }
                event = parser.next()
            }
        }
        return tags
    }

    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
}
