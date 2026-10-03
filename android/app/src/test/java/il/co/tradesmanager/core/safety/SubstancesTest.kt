package il.co.tradesmanager.core.safety

import il.co.tradesmanager.core.safety.Substances.Hazard
import il.co.tradesmanager.core.safety.Substances.Refusal
import il.co.tradesmanager.core.safety.Substances.State
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubstancesTest {

    private val today = LocalDate.of(2026, 10, 8)

    @Test
    fun `a substance is on the register because it is hazardous, and somebody has to know where it is`() {
        assertEquals(Refusal.BLANK_NAME, Substances.addRefusal(" ", setOf(Hazard.FLAMMABLE), "Cage", null, today))
        assertEquals(Refusal.NO_HAZARD, Substances.addRefusal("Diesel", emptySet(), "Cage", null, today))
        assertEquals(Refusal.BLANK_KEPT_WHERE, Substances.addRefusal("Diesel", setOf(Hazard.FLAMMABLE), " ", null, today))
        assertEquals(Refusal.SHEET_IN_FUTURE, Substances.addRefusal("Diesel", setOf(Hazard.FLAMMABLE), "Cage", today.plusDays(1), today))
        assertNull("no sheet yet is allowed, and shown", Substances.addRefusal("Diesel", setOf(Hazard.FLAMMABLE), "Cage", null, today))
        assertNull(Substances.addRefusal("Diesel", setOf(Hazard.FLAMMABLE), "Cage", today, today))
    }

    @Test
    fun `a newer sheet replaces the one on file, an older or the same one does not`() {
        val onFile = LocalDate.of(2022, 3, 1)
        assertNull(Substances.sheetRefusal(LocalDate.of(2025, 1, 1), onFile, removed = false, today = today))
        assertNull("the first sheet for one that had none", Substances.sheetRefusal(onFile, null, removed = false, today = today))
        assertEquals(Refusal.SHEET_NOT_NEWER, Substances.sheetRefusal(onFile, onFile, removed = false, today = today))
        assertEquals(Refusal.SHEET_NOT_NEWER, Substances.sheetRefusal(onFile.minusDays(1), onFile, removed = false, today = today))
        assertEquals(Refusal.SHEET_IN_FUTURE, Substances.sheetRefusal(today.plusDays(1), onFile, removed = false, today = today))
        assertEquals(Refusal.ALREADY_REMOVED, Substances.sheetRefusal(today, onFile, removed = true, today = today))
        assertEquals(Refusal.ALREADY_REMOVED, Substances.removeRefusal(removed = true))
        assertNull(Substances.removeRefusal(removed = false))
    }

    @Test
    fun `no sheet, then an old sheet, then the rest, then what has gone`() {
        assertEquals(State.NO_SHEET, Substances.state(null, removed = false, today = today))
        assertEquals("five years to the day is not yet old", State.ON_SITE, Substances.state(today.minusYears(5), removed = false, today = today))
        assertEquals(State.SHEET_OLD, Substances.state(today.minusYears(5).minusDays(1), removed = false, today = today))
        assertEquals(State.ON_SITE, Substances.state(today.minusYears(1), removed = false, today = today))
        assertEquals("gone is gone, sheet or no sheet", State.REMOVED, Substances.state(null, removed = true, today = today))

        val rows = listOf(
            State.ON_SITE to "form oil",
            State.REMOVED to "Acid",
            State.NO_SHEET to "propane",
            State.SHEET_OLD to "Epoxy",
            State.NO_SHEET to "Diesel",
        )
        assertEquals(listOf(4, 2, 3, 0, 1), Substances.order(rows))
    }

    @Test
    fun `numbered per job, and the hazards stored the same way whatever order they were picked in`() {
        assertEquals("HS-001", Substances.reference(0))
        assertEquals("HS-012", Substances.reference(11))
        val picked = linkedSetOf(Hazard.HARMFUL, Hazard.FLAMMABLE)
        assertEquals("FLAMMABLE,HARMFUL", Substances.encode(picked))
        assertEquals(setOf(Hazard.FLAMMABLE, Hazard.HARMFUL), Substances.decode("FLAMMABLE,HARMFUL"))
        assertEquals("a name this version does not know is dropped", setOf(Hazard.TOXIC), Substances.decode("TOXIC,RADIOACTIVE"))
        assertEquals(emptySet<Hazard>(), Substances.decode(""))
    }
}
