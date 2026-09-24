package il.co.tradesmanager.i18n

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import il.co.tradesmanager.R
import java.io.File
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Android's own resource lookup, asked for the app's words in each language.
 *
 * The app shipped for twenty-four versions with every translation present and
 * most of the screen in English, and nothing in the build noticed, because
 * every check read the files rather than asking Android what a Hebrew phone
 * is shown. This asks. It runs the real resource system on the JVM, with the
 * plain Application rather than the app's own, which opens an encrypted
 * database whose native library does not exist here.
 *
 * "iw" as well as "he": Android and Java still hand the old code to apps on
 * some phones, and a Hebrew phone that reports "iw" must still get Hebrew.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocalizedResourcesTest {

    private fun inLanguage(tag: String): Context {
        val base = RuntimeEnvironment.getApplication()
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(tag))
        }
        return base.createConfigurationContext(configuration)
    }

    /** The source every language's resources are generated from. */
    private val source by lazy {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "shared/i18n/strings.json") }
            .first { it.isFile }
        Json.parseToJsonElement(file.readText()).jsonObject["strings"]!!.jsonObject
    }

    private fun expected(key: String, language: String): String =
        source[key]!!.jsonObject[language]!!.jsonPrimitive.content

    /** Words from across the app: the first screens, the registers, the newest features. */
    private val sample = mapOf(
        "set_title" to R.string.set_title,
        "inv_title" to R.string.inv_title,
        "ppe_title" to R.string.ppe_title,
        "visit_title" to R.string.visit_title,
        "dwg_title" to R.string.dwg_title,
        "qry_title" to R.string.qry_title,
        "cube_title" to R.string.cube_title,
        "whats_new_title" to R.string.whats_new_title,
        "gaps_title" to R.string.gaps_title,
    )

    @Test
    fun `a Hebrew phone is shown Hebrew`() {
        val context = inLanguage("he")
        sample.forEach { (key, id) -> assertEquals(key, expected(key, "he"), context.getString(id)) }
    }

    @Test
    fun `a Hebrew phone reporting the old code is shown Hebrew too`() {
        val context = inLanguage("iw")
        sample.forEach { (key, id) -> assertEquals(key, expected(key, "he"), context.getString(id)) }
    }

    @Test
    fun `an Arabic phone is shown Arabic`() {
        val context = inLanguage("ar")
        sample.forEach { (key, id) -> assertEquals(key, expected(key, "ar"), context.getString(id)) }
    }

    @Test
    fun `an English phone, and a language the app does not ship, are shown English`() {
        for (tag in listOf("en", "fr")) {
            val context = inLanguage(tag)
            sample.forEach { (key, id) -> assertEquals("$tag $key", expected(key, "en"), context.getString(id)) }
        }
    }

    @Test
    fun `right to left in Hebrew and Arabic, left to right in English`() {
        assertEquals(android.view.View.LAYOUT_DIRECTION_RTL, inLanguage("he").resources.configuration.layoutDirection)
        assertEquals(android.view.View.LAYOUT_DIRECTION_RTL, inLanguage("ar").resources.configuration.layoutDirection)
        assertEquals(android.view.View.LAYOUT_DIRECTION_LTR, inLanguage("en").resources.configuration.layoutDirection)
    }
}
