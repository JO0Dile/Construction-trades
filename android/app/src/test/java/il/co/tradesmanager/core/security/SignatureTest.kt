package il.co.tradesmanager.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A signature that cannot be read back is not evidence, so both directions of
 * this are pinned — including the ones where the stored text is damaged.
 */
class SignatureTest {

    private fun p(x: Float, y: Float) = Signature.Point(x, y)

    @Test
    fun `a signature survives the round trip`() {
        val strokes = listOf(
            listOf(p(0f, 0f), p(0.5f, 0.25f), p(1f, 1f)),
            listOf(p(0.125f, 0.875f), p(0.25f, 0.5f)),
        )
        val decoded = Signature.decode(Signature.encode(strokes))
        assertEquals(2, decoded.size)
        assertEquals(3, decoded[0].size)
        assertEquals(0.5f, decoded[0][1].x, 0.001f)
        assertEquals(0.25f, decoded[0][1].y, 0.001f)
        assertEquals(0.875f, decoded[1][0].y, 0.001f)
    }

    @Test
    fun `coordinates are written without a locale decimal comma`() {
        // The bug this guards against turns every signature captured on a
        // phone set to Hebrew or Arabic into unreadable rubbish, because the
        // format separates coordinates with the same comma.
        val encoded = Signature.encode(listOf(listOf(p(0.5f, 0.25f))))
        assertEquals("0.5,0.25", encoded)
    }

    @Test
    fun `nothing drawn encodes to nothing`() {
        assertEquals("", Signature.encode(emptyList()))
        assertEquals("", Signature.encode(listOf(emptyList())))
        assertTrue(Signature.decode("").isEmpty())
        assertTrue(Signature.decode(null).isEmpty())
        assertTrue(Signature.decode("   ").isEmpty())
    }

    @Test
    fun `a damaged signature reads back as much as it can, and never throws`() {
        // A permit with a corrupted signature still has to open. Showing part
        // of it beats crashing the screen that was meant to display it.
        val decoded = Signature.decode("0.1,0.1 rubbish 0.2,0.2;;0.3,notanumber;0.4,0.4 0.5,0.5")
        assertEquals(2, decoded.size)
        assertEquals(2, decoded[0].size)
        assertEquals(0.2f, decoded[0][1].x, 0.001f)
        assertEquals(2, decoded[1].size)
    }

    @Test
    fun `points outside the pad are pulled back onto it`() {
        val encoded = Signature.encode(listOf(listOf(p(-4f, 9f))))
        assertEquals("0,1", encoded)
        val decoded = Signature.decode("-2,-2 3,3")
        assertEquals(0f, decoded[0][0].x, 0.0001f)
        assertEquals(1f, decoded[0][1].y, 0.0001f)
    }

    @Test
    fun `one tap is not a signature`() {
        // Stops a permit being signed by a phone in somebody's pocket, and a
        // dialog being signed off by a thumb resting on the glass.
        assertFalse(Signature.isSigned(null))
        assertFalse(Signature.isSigned(""))
        assertFalse(Signature.isSigned("0.5,0.5"))
        assertFalse(Signature.isSigned("0.5,0.5;0.6,0.6"))
        assertTrue(Signature.isSigned("0.5,0.5 0.6,0.6"))
    }

    @Test
    fun `the stored form stays small`() {
        // Every signed permit, induction and checklist carries one of these.
        val stroke = (0..200).map { p(it / 200f, (it % 40) / 40f) }
        val encoded = Signature.encode(listOf(stroke))
        assertTrue("was ${encoded.length} chars", encoded.length < 2_500)
        assertEquals(201, Signature.decode(encoded)[0].size)
    }
}
