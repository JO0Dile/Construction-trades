package il.co.tradesmanager.core.audit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What an audit entry says, stored so it survives the language it was written
 * in.
 *
 * The test that matters is the last group: the register already has entries
 * in it, every one of them an English sentence, and none of them may start
 * rendering as a missing translation because this arrived. Nothing rewrites
 * an old row — an audit trail whose history gets tidied up is not an audit
 * trail — so the reading has to cope with both.
 */
class SummaryTest {

    @Test
    fun `a key and its arguments come back`() {
        val stored = Summary.of("permit_issued", "PTW-14", "Yossi")
        assertEquals("permit_issued|PTW-14|Yossi", stored)
        assertEquals(Summary.Parsed("permit_issued", listOf("PTW-14", "Yossi")), Summary.parse(stored))
    }

    @Test
    fun `a key with no arguments`() {
        assertEquals(Summary.Parsed("induction_signed", emptyList()), Summary.parse(Summary.of("induction_signed")))
    }

    @Test
    fun `an empty argument is still an argument`() {
        assertEquals(Summary.Parsed("k", listOf("")), Summary.parse(Summary.of("k", "")))
    }

    @Test
    fun `a separator inside somebody's own reference survives`() {
        // References are whatever the firm already calls things, and a job
        // called "A|B" must not quietly become two arguments.
        for (awkward in listOf(
            "A" + "|" + "B",
            "\\",
            "\\" + "|",
            "|" + "|",
            "ends with " + "\\",
            "שלום" + "|" + "עולם",
            "مرحبا" + "\\" + "بالعالم",
        )) {
            assertEquals(
                "round trip of " + awkward,
                Summary.Parsed("k", listOf(awkward, awkward)),
                Summary.parse(Summary.of("k", awkward, awkward)),
            )
        }
    }

    @Test
    fun `an argument that is itself a key is marked`() {
        val stored = Summary.of("joined_as", Summary.nest("site_manager"))
        assertEquals("site_manager", Summary.nested(Summary.parse(stored)!!.arguments[0]))
    }

    @Test
    fun `free text is never marked as a key`() {
        // The reason on a stock movement is a key when the plus and minus
        // wrote it and free text when a delivery did. Marking the free text
        // would print the mark.
        assertEquals("Delivered on PO-12", Summary.nest("Delivered on PO-12"))
        assertNull(Summary.nested("Delivered on PO-12"))
        assertNull(Summary.nested("plain"))
    }

    @Test
    fun `every sentence already in the register stays a sentence`() {
        // These are real summaries this app has written. Not one of them may
        // be mistaken for a key, or an entry somebody recorded last month
        // would come back as a blank line or a constant.
        for (stored in listOf(
            "Checklist signed",
            "Safety induction signed",
            "ID number set",
            "Placed",
            "Backfilled",
            "Item removed from inventory",
            "Left the company",
            "PTW-14 issued to Yossi",
            "Received 3 pcs of Cable",
            "Truck 88 6.0m3",
            "Rejected: too wet",
            "4.0 -> 3.0 (used_on_site)",
            "Delivered on PO-12",
            "Created from template flat-rewire",
            "",
            " ",
            "1234",
        )) {
            assertNull("'" + stored + "' was read as a key", Summary.parse(stored))
        }
    }

    @Test
    fun `a single lower-case word parses, which is why rendering falls back to the stored text`() {
        // An item genuinely called "cable" is a key by shape. Nothing can be
        // done about that at this level, and nothing needs to be: the
        // renderer prints the stored text for any key it does not know, so
        // the row reads "cable" either way. Written down because it is the
        // one case where parse() succeeding is not the same as the summary
        // being one of ours.
        assertEquals(Summary.Parsed("cable", emptyList()), Summary.parse("cable"))
    }

    @Test
    fun `a whole number is written without a trailing point zero`() {
        // "4.0 sockets" in a register was written by a program rather than by
        // a person, and reads that way to whoever is reconstructing a day.
        assertEquals("4", Summary.number(4.0))
        assertEquals("0", Summary.number(0.0))
        assertEquals("-3", Summary.number(-3.0))
        assertEquals("1000000", Summary.number(1_000_000.0))
    }

    @Test
    fun `a fraction keeps its fraction`() {
        // Half a cubic metre of concrete is not the same delivery as a whole
        // one, and the ticket has to be able to say so.
        assertTrue(Summary.number(2.5).contains("."))
        assertTrue(Summary.number(0.25).contains("."))
        assertTrue(Summary.number(-1.5).contains("."))
    }

    @Test
    fun `a date is a date and not thirteen digits`() {
        // This was an epoch millisecond count: "PO-12 due 1758585600000".
        // ISO order because the argument is stored once and read in three
        // languages, and 2025-09-23 is the one spelling that means the same
        // in all of them.
        val utc = java.time.ZoneId.of("UTC")
        assertEquals("2025-09-23", Summary.date(1_758_585_600_000L, utc))
        assertEquals("1970-01-01", Summary.date(0L, utc))
    }

    @Test
    fun `the day is the day where the work is`() {
        // Half past eleven at night in Israel is still that day, not the next
        // one, and a delivery date that moves because the server is in London
        // is a delivery date nobody trusts.
        val israel = java.time.ZoneId.of("Asia/Jerusalem")
        val lateEvening = java.time.ZonedDateTime
            .of(2026, 3, 1, 23, 30, 0, 0, israel)
            .toInstant().toEpochMilli()
        assertEquals("2026-03-01", Summary.date(lateEvening, israel))
    }

    @Test
    fun `keys are lower case, digits and underscores`() {
        for (key in listOf("a", "a1", "tw_released", "goods_received")) {
            assertEquals(Summary.Parsed(key, emptyList()), Summary.parse(key))
        }
        for (notAKey in listOf("Induction", "1abc", "_abc", "has space", "UPPER", "dash-ed")) {
            assertNull(notAKey, Summary.parse(notAKey))
        }
    }
}
