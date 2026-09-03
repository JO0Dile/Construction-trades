package il.co.tradesmanager.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizedTextTest {

    private val cable = mapOf(
        "en" to "NYY cable 3×1.5 mm²",
        "he" to "כבל NYY 3×1.5 ממ״ר",
        "ar" to "كابل NYY 3×1.5 ملم²",
    )

    @Test
    fun `resolves the exact language`() {
        assertEquals("כבל NYY 3×1.5 ממ״ר", cable.resolve("he"))
        assertEquals("كابل NYY 3×1.5 ملم²", cable.resolve("ar"))
    }

    @Test
    fun `falls back from a regional tag to its base language`() {
        assertEquals("כבל NYY 3×1.5 ממ״ר", cable.resolve("he-IL"))
        assertEquals("كابل NYY 3×1.5 ملم²", cable.resolve("ar-IL"))
    }

    @Test
    fun `falls back to the source language for a language with no translation`() {
        // Russian is planned but not translated: a Russian phone must still
        // show the item rather than an empty row.
        assertEquals("NYY cable 3×1.5 mm²", cable.resolve("ru"))
    }

    @Test
    fun `falls back to any translation when the source language is missing`() {
        val onlyHebrew = mapOf("he" to "מפסק פחת")
        assertEquals("מפסק פחת", onlyHebrew.resolve("en"))
    }

    @Test
    fun `blank translations are skipped rather than shown`() {
        val partial = mapOf("he" to "  ", "en" to "RCD")
        assertEquals("RCD", partial.resolve("he"))
    }

    @Test
    fun `empty text resolves to empty string, never to a crash`() {
        assertEquals("", emptyMap<String, String>().resolve("he"))
    }

    @Test
    fun `search index contains every language lowercased`() {
        val index = cable.searchable()
        assertTrue("Hebrew term missing", index.contains("כבל"))
        assertTrue("Arabic term missing", index.contains("كابل"))
        assertTrue("English term missing", index.contains("nyy cable"))
    }

    @Test
    fun `localizedTextOf drops blank entries`() {
        val text = localizedTextOf("en" to "Socket", "he" to "", "ar" to "مقبس")
        assertEquals(setOf("en", "ar"), text.keys)
    }
}
