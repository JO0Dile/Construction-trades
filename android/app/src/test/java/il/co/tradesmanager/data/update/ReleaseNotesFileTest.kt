package il.co.tradesmanager.data.update

import il.co.tradesmanager.BuildConfig
import il.co.tradesmanager.core.update.ReleaseNotes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The notes file the app ships, read the way the app reads it. */
class ReleaseNotesFileTest {

    private val shipped: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "shared/assets/releases/notes.json") }
        .firstOrNull { it.isFile }
        ?: File("shared/assets/releases/notes.json")

    @Test
    fun `the shipped notes parse and describe this very version in three languages`() {
        val entries = ReleaseNotesFile.decode(shipped.readText())
        val current = ReleaseNotes.between(entries, installed = null, target = BuildConfig.VERSION_NAME).single()
        assertTrue(current.points.isNotEmpty())
        current.points.forEach { point ->
            for (language in listOf("en", "he", "ar")) {
                assertTrue("$language missing", point[language].orEmpty().isNotBlank())
            }
        }
    }

    @Test
    fun `a file that will not parse costs the list, never the update`() {
        assertEquals(emptyList<ReleaseNotes.Entry>(), ReleaseNotesFile.decode("{ not json"))
        assertEquals(emptyList<ReleaseNotes.Entry>(), ReleaseNotesFile.decode("{\"versions\": [{\"version\": \"\"}]}"))
    }
}
