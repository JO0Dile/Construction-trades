package il.co.tradesmanager.core.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingsTest {

    private fun sheet(id: String, number: String, revision: String, at: Long, superseded: Long? = null) =
        Drawings.Sheet(id, number, revision, receivedAt = at, supersededAt = superseded)

    @Test
    fun `one drawing written three ways is one drawing`() {
        assertEquals(Drawings.key("A-101"), Drawings.key(" a 101 "))
        assertEquals(Drawings.key("A-101"), Drawings.key("A101"))
    }

    @Test
    fun `a drawing needs a number and a revision, and not the one already current`() {
        val current = sheet("1", "A-101", "B", at = 10)
        assertEquals(Drawings.Refusal.NO_NUMBER, Drawings.refusal(" - ", "C", current))
        assertEquals(Drawings.Refusal.NO_REVISION, Drawings.refusal("A-101", " ", current))
        assertEquals(Drawings.Refusal.ALREADY_CURRENT, Drawings.refusal("A-101", " b", current))
        assertNull(Drawings.refusal("A-101", "C", current))
        assertNull(Drawings.refusal("A-102", "A", null))
    }

    @Test
    fun `the list is current revisions only, numbers in number order`() {
        val sheets = listOf(
            sheet("a", "A-10", "A", at = 1),
            sheet("b", "A-2", "A", at = 2, superseded = 5),
            sheet("c", "A-2", "B", at = 5),
            sheet("d", "S-01", "P1", at = 3),
        )
        assertEquals(listOf("c", "a", "d"), Drawings.current(sheets).map { it.id })
    }

    @Test
    fun `history is every earlier revision of that drawing, newest first`() {
        val sheets = listOf(
            sheet("a", "A-2", "A", at = 1, superseded = 4),
            sheet("b", "A-2", "B", at = 4, superseded = 9),
            sheet("c", "A-2", "C", at = 9),
            sheet("x", "A-3", "A", at = 2, superseded = 3),
        )
        assertEquals(listOf("b", "a"), Drawings.history(sheets, "a2").map { it.id })
        assertEquals("c", Drawings.currentOf(sheets, "A 2")!!.id)
    }

    @Test
    fun `numbers inside drawing numbers compare as numbers`() {
        assertTrue(Drawings.naturalCompare("A2", "A10") < 0)
        assertTrue(Drawings.naturalCompare("A010", "A9") > 0)
        assertEquals("leading zeros do not make a different number", 0, Drawings.naturalCompare("S01", "S1"))
        assertTrue(Drawings.naturalCompare("A", "B") < 0)
    }
}
