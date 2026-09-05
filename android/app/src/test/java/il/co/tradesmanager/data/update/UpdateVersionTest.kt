package il.co.tradesmanager.data.update

import il.co.tradesmanager.data.update.UpdateRepository.Companion.isNewer
import il.co.tradesmanager.data.update.UpdateRepository.Companion.normalise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The comparison behind the update button.
 *
 * Getting this wrong is not cosmetic: too eager and every launch nags a phone
 * that is already current, too lax and a site keeps running last month's
 * safety checklists.
 */
class UpdateVersionTest {

    @Test
    fun `tags are read whether or not they are dressed up`() {
        assertEquals(listOf(1, 2, 3), normalise("v1.2.3"))
        assertEquals(listOf(1, 2, 3), normalise("1.2.3"))
        assertEquals(listOf(0, 2, 0), normalise("0.2.0-debug"))
        assertEquals(listOf(1, 0, 0), normalise("  V1.0.0  "))
    }

    @Test
    fun `a newer release is offered`() {
        assertTrue(isNewer(normalise("v0.3.0"), normalise("0.2.0-debug")))
        assertTrue(isNewer(normalise("1.0.0"), normalise("0.9.9")))
        assertTrue(isNewer(normalise("0.2.1"), normalise("0.2.0")))
    }

    @Test
    fun `the version already installed is not offered again`() {
        assertFalse(isNewer(normalise("v0.2.0"), normalise("0.2.0-debug")))
        assertFalse(isNewer(normalise("0.1.0"), normalise("0.2.0")))
    }

    @Test
    fun `a missing part counts as zero rather than as newer`() {
        // "1.2" and "1.2.0" are the same release named two ways; offering an
        // update between them would loop forever.
        assertFalse(isNewer(normalise("1.2"), normalise("1.2.0")))
        assertFalse(isNewer(normalise("1.2.0"), normalise("1.2")))
        assertTrue(isNewer(normalise("1.2.1"), normalise("1.2")))
    }

    @Test
    fun `a tag that is not a version does not trigger an update`() {
        assertFalse(isNewer(normalise("latest"), normalise("0.2.0")))
        assertFalse(isNewer(normalise(""), normalise("0.2.0")))
    }
}
