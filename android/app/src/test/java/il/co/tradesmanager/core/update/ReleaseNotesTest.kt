package il.co.tradesmanager.core.update

import il.co.tradesmanager.core.i18n.localizedTextOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {

    private fun entry(version: String) = ReleaseNotes.Entry(
        version = version,
        points = listOf(localizedTextOf("en" to "Did $version", "he" to "עשה $version", "ar" to "فعل $version")),
    )

    private val history = listOf("0.20.0", "0.19.1", "0.19.0", "0.18.0", "0.17.0").map(::entry)

    @Test
    fun `an update that skips versions lists every one of them, newest first`() {
        val due = ReleaseNotes.between(history, installed = "0.17.0", target = "v0.20.0")
        assertEquals(listOf("0.20.0", "0.19.1", "0.19.0", "0.18.0"), due.map { it.version })
    }

    @Test
    fun `nothing the phone already has, and nothing past the version on offer`() {
        val due = ReleaseNotes.between(history, installed = "0.18.0", target = "0.19.0")
        assertEquals(listOf("0.19.0"), due.map { it.version })
    }

    @Test
    fun `a debug suffix does not hide a version`() {
        val due = ReleaseNotes.between(history, installed = "0.19.0-debug", target = "0.20.0")
        assertEquals(listOf("0.20.0", "0.19.1"), due.map { it.version })
    }

    @Test
    fun `with no record of what was seen, only the version itself`() {
        val due = ReleaseNotes.between(history, installed = null, target = "0.19.0")
        assertEquals(listOf("0.19.0"), due.map { it.version })
    }

    @Test
    fun `order in the file does not decide order on the screen`() {
        val due = ReleaseNotes.between(history.reversed(), installed = "0.18.0", target = "0.20.0")
        assertEquals(listOf("0.20.0", "0.19.1", "0.19.0"), due.map { it.version })
    }

    @Test
    fun `points come out in the phone's language`() {
        assertEquals(listOf("עשה 0.18.0"), ReleaseNotes.points(entry("0.18.0"), "he-IL"))
        assertEquals(listOf("فعل 0.18.0"), ReleaseNotes.points(entry("0.18.0"), "ar"))
        assertEquals(listOf("Did 0.18.0"), ReleaseNotes.points(entry("0.18.0"), "en"))
    }

    @Test
    fun `what's new is shown after an update, never on a first install`() {
        assertFalse("first install", ReleaseNotes.shouldShow(lastSeen = null, current = "0.25.0", freshInstall = true))
        assertTrue("updated from before it was recorded", ReleaseNotes.shouldShow(null, "0.25.0", freshInstall = false))
        assertTrue("updated", ReleaseNotes.shouldShow("0.24.0", "0.25.0", freshInstall = false))
        assertFalse("already seen", ReleaseNotes.shouldShow("0.25.0", "0.25.0", freshInstall = false))
        assertFalse("older build put back on", ReleaseNotes.shouldShow("0.26.0", "0.25.0", freshInstall = false))
    }

    @Test
    fun `versions compare as numbers, not as text`() {
        assertTrue(ReleaseNotes.compare(ReleaseNotes.parse("0.10.0"), ReleaseNotes.parse("0.9.1")) > 0)
        assertEquals(0, ReleaseNotes.compare(ReleaseNotes.parse("v1.2"), ReleaseNotes.parse("1.2.0")))
    }
}
