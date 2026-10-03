package il.co.tradesmanager.core.safety

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PpeTest {

    private val signed = "0.1,0.1 0.5,0.5"
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_750_000_000_000L

    private fun issue(
        id: String,
        holder: String = "acc.1",
        name: String = "Yossi",
        issuedAt: Long = now - 10 * day,
        replaceBy: Long? = null,
        handedBackAt: Long? = null,
    ) = Ppe.Issue(id, holder, name, "Helmet", 1, issuedAt, replaceBy, handedBackAt)

    @Test
    fun `an issue needs a person, a thing, a count and a signature`() {
        assertEquals(Ppe.Refusal.NO_WORKER, Ppe.refusal(" ", "Helmet", 1, signed, now, null))
        assertEquals(Ppe.Refusal.NO_ITEM, Ppe.refusal("Yossi", "", 1, signed, now, null))
        assertEquals(Ppe.Refusal.BAD_QUANTITY, Ppe.refusal("Yossi", "Helmet", 0, signed, now, null))
        assertEquals(
            Ppe.Refusal.BAD_QUANTITY,
            Ppe.refusal("Yossi", "Helmet", Ppe.MOST_AT_ONCE + 1, signed, now, null),
        )
        assertEquals(Ppe.Refusal.NOT_SIGNED, Ppe.refusal("Yossi", "Helmet", 1, "", now, null))
        assertNull(Ppe.refusal("Yossi", "Helmet", Ppe.MOST_AT_ONCE, signed, now, null))
    }

    @Test
    fun `one stray tap is not a signature`() {
        assertEquals(Ppe.Refusal.NOT_SIGNED, Ppe.refusal("Yossi", "Helmet", 1, "0.5,0.5", now, null))
    }

    @Test
    fun `a replace-by date has to be after the day it was handed over`() {
        assertEquals(Ppe.Refusal.REPLACE_BY_TOO_EARLY, Ppe.refusal("Yossi", "Helmet", 1, signed, now, now))
        assertEquals(Ppe.Refusal.REPLACE_BY_TOO_EARLY, Ppe.refusal("Yossi", "Helmet", 1, signed, now, now - 1))
        assertNull(Ppe.refusal("Yossi", "Helmet", 1, signed, now, now + day))
    }

    @Test
    fun `where a thing stands`() {
        assertEquals(Ppe.State.IN_USE, Ppe.state(null, null, now))
        assertEquals(Ppe.State.OVERDUE, Ppe.state(now, null, now))
        assertEquals(Ppe.State.OVERDUE, Ppe.state(now - day, null, now))
        assertEquals(Ppe.State.DUE_SOON, Ppe.state(now + Ppe.DUE_SOON_DAYS * day, null, now))
        assertEquals(Ppe.State.IN_USE, Ppe.state(now + Ppe.DUE_SOON_DAYS * day + 1, null, now))
        assertEquals("handed back beats overdue", Ppe.State.HANDED_BACK, Ppe.state(now - day, now, now))
    }

    @Test
    fun `a short shelf is reported, not refused`() {
        assertNull(Ppe.shortfall(5.0, 5))
        assertNull(Ppe.shortfall(10.0, 1))
        assertEquals(2.0, Ppe.shortfall(1.0, 3)!!, 0.0)
        assertEquals(1.0, Ppe.shortfall(0.0, 1)!!, 0.0)
    }

    @Test
    fun `holders are what is still in hand, the worst first`() {
        val holders = Ppe.holders(
            listOf(
                issue("a", holder = "acc.1", name = "Avi"),
                issue("b", holder = "acc.2", name = "Bassam", replaceBy = now - day),
                issue("c", holder = "acc.3", name = "Carmel", handedBackAt = now - day),
                issue("d", holder = "acc.4", name = "Dana", replaceBy = now + 5 * day),
            ),
            now,
        )
        assertEquals(
            "overdue first, then due soon, then by name; nothing handed back",
            listOf("Bassam", "Dana", "Avi"),
            holders.map { it.name },
        )
        assertEquals(1, holders.first().overdue)
        assertEquals(1, holders[1].dueSoon)
    }

    @Test
    fun `one person's things sit together, worst first, under their latest name`() {
        val holders = Ppe.holders(
            listOf(
                issue("boots", name = "Yosi", issuedAt = now - 20 * day),
                issue("harness", name = "Yossi", issuedAt = now - 5 * day, replaceBy = now - day),
                issue("gloves", name = "Yossi", issuedAt = now - 2 * day),
            ),
            now,
        )
        val one = holders.single()
        assertEquals("Yossi", one.name)
        assertEquals(listOf("harness", "gloves", "boots"), one.items.map { it.first.id })
    }

    @Test
    fun `somebody with no account is one person however their name is typed`() {
        assertEquals(Ppe.typedHolderKey("  Abu Ahmad "), Ppe.typedHolderKey("abu ahmad"))
    }

    @Test
    fun `months are calendar months, clamped to the end of a short one`() {
        val zone = ZoneId.of("Asia/Jerusalem")
        fun at(date: LocalDate) = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        fun dateOf(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

        assertEquals(LocalDate.of(2027, 2, 28), dateOf(Ppe.monthsAfter(at(LocalDate.of(2026, 8, 31)), 6, zone)))
        assertEquals(LocalDate.of(2031, 3, 15), dateOf(Ppe.monthsAfter(at(LocalDate.of(2026, 3, 15)), 60, zone)))
        assertEquals(LocalDate.of(2029, 2, 28), dateOf(Ppe.monthsAfter(at(LocalDate.of(2028, 2, 29)), 12, zone)))
    }
}
