package il.co.tradesmanager.core.evidence

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoStampTest {

    private val israel: ZoneId = ZoneId.of("Asia/Jerusalem")
    private val english: Locale = Locale.forLanguageTag("en")

    private fun at(date: String, hour: Int, minute: Int) =
        LocalDate.parse(date).atTime(hour, minute).atZone(israel).toInstant().toEpochMilli()

    @Test
    fun `coordinates never use a locale decimal comma`() {
        // A comma where a decimal point belongs turns one coordinate into two
        // numbers, in a stamp whose whole job is to be read years later.
        assertEquals("32.085300, 34.781800", PhotoStamp.coordinates(32.0853, 34.7818))
        Locale.setDefault(Locale.forLanguageTag("he"))
        try {
            assertEquals("32.085300, 34.781800", PhotoStamp.coordinates(32.0853, 34.7818))
        } finally {
            Locale.setDefault(english)
        }
    }

    @Test
    fun `no location means no coordinate line, not a blank one`() {
        assertNull(PhotoStamp.coordinates(null, null))
        assertNull(PhotoStamp.coordinates(32.0853, null))
        assertNull(PhotoStamp.coordinates(null, 34.7818))
    }

    @Test
    fun `impossible coordinates are dropped rather than printed`() {
        assertNull(PhotoStamp.coordinates(Double.NaN, 34.0))
        assertNull(PhotoStamp.coordinates(91.0, 34.0))
        assertNull(PhotoStamp.coordinates(32.0, 181.0))
    }

    @Test
    fun `a stamp carries when, where and who`() {
        val lines = PhotoStamp.lines(
            capturedAt = at("2026-09-05", 18, 24),
            latitude = 32.0853,
            longitude = 34.7818,
            recordedBy = "hammam",
            locale = english,
            zone = israel,
        )
        assertEquals(3, lines.size)
        assertTrue(lines[0].contains("05/09/2026"))
        assertEquals("32.085300, 34.781800", lines[1])
        assertEquals("hammam", lines[2])
    }

    @Test
    fun `a photo with no location still gets a date`() {
        val lines = PhotoStamp.lines(
            capturedAt = at("2026-09-05", 7, 5),
            latitude = null,
            longitude = null,
            recordedBy = null,
            locale = english,
            zone = israel,
        )
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("05/09/2026"))
    }

    @Test
    fun `a blank name is not a line`() {
        val lines = PhotoStamp.lines(
            at("2026-09-05", 7, 5), null, null, "   ", english, israel,
        )
        assertEquals(1, lines.size)
    }

    @Test
    fun `site photographs are stamped and people are not`() {
        // Stamping somebody's face with the coordinates of where they stood is
        // no use as evidence and is not a thing to do to an ID document.
        assertTrue(PhotoStamp.appliesTo("project.photo"))
        assertTrue(PhotoStamp.appliesTo("snag.raised"))
        assertTrue(PhotoStamp.appliesTo("incident"))
        assertFalse(PhotoStamp.appliesTo("account.photo"))
        assertFalse(PhotoStamp.appliesTo("account.id_document"))
    }
}
