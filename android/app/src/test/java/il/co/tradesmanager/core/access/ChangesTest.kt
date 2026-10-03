package il.co.tradesmanager.core.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What each person is told has changed.
 *
 * The promises here are the ones somebody would ask about after being shown
 * the feed for the first time: "can the lads see what the job is worth", "does
 * the labourer find out the drawing moved".
 */
class ChangesTest {

    @Test
    fun `a labourer hears the programme move and the drawing replaced`() {
        assertTrue(Changes.visibleTo(Role.WORKER, "project_task"))
        assertTrue(Changes.visibleTo(Role.WORKER, "task_block"))
        assertTrue(Changes.visibleTo(Role.WORKER, "photo"))
    }

    @Test
    fun `a labourer does not hear what the job is worth`() {
        // The whole reason the feed is filtered rather than just shown.
        assertFalse(Changes.visibleTo(Role.WORKER, "cost_entry"))
        assertFalse(Changes.visibleTo(Role.WORKER, "contract"))
        assertFalse(Changes.visibleTo(Role.WORKER, "invoice"))
        assertFalse(Changes.visibleTo(Role.WORKER, "payment_application"))
    }

    @Test
    fun `a labourer does not hear about other people's records`() {
        assertFalse(Changes.visibleTo(Role.WORKER, "membership"))
        assertFalse(Changes.visibleTo(Role.WORKER, "certification"))
        assertFalse(Changes.visibleTo(Role.WORKER, "account"))
    }

    @Test
    fun `the finance clerk hears the money and not the programme`() {
        assertTrue(Changes.visibleTo(Role.FINANCE, "invoice"))
        assertFalse(Changes.visibleTo(Role.FINANCE, "project_task"))
    }

    @Test
    fun `the safety officer hears the evidence and not the money`() {
        assertTrue(Changes.visibleTo(Role.SAFETY_OFFICER, "incident"))
        assertTrue(Changes.visibleTo(Role.SAFETY_OFFICER, "permit"))
        assertFalse(Changes.visibleTo(Role.SAFETY_OFFICER, "cost_entry"))
        assertFalse(Changes.visibleTo(Role.SAFETY_OFFICER, "time_entry"))
    }

    @Test
    fun `hours are money, not people`() {
        // A shift is somebody's wages waiting to be worked out. Filing it
        // under People would put it in front of an HR role that has no
        // business with the figure and hide it from the manager who does.
        assertEquals(Lens.MONEY, Changes.lensOf("time_entry"))
        assertTrue(Changes.visibleTo(Role.MANAGER, "time_entry"))
        assertFalse(Changes.visibleTo(Role.HR, "time_entry"))
    }

    @Test
    fun `a kind of change nobody has classified is shown to nobody`() {
        // Including to an owner. Somebody has to decide what a new register is
        // before it starts appearing in people's feeds, and a default that
        // showed it to the most privileged role would make the decision by
        // never being noticed.
        assertNull(Changes.lensOf("shipping_manifest"))
        Role.entries.forEach { role ->
            assertFalse(
                "an unclassified change reached $role",
                Changes.visibleTo(role, "shipping_manifest"),
            )
        }
    }

    @Test
    fun `every classified type maps to a lens somebody can read`() {
        // A type mapped to a lens no role reads would be classified and still
        // invisible, which is the failure this file exists to make loud.
        Changes.LENS_OF.forEach { (type, lens) ->
            assertTrue(
                "$type is filed under $lens and no role reads it",
                Role.entries.any { it.canRead(lens) },
            )
        }
    }

    @Test
    fun `filtering a batch keeps the order and drops the rest`() {
        data class Row(val type: String, val label: String)
        val rows = listOf(
            Row("project_task", "task moved"),
            Row("cost_entry", "cost added"),
            Row("photo", "drawing replaced"),
        )
        assertEquals(
            listOf("task moved", "drawing replaced"),
            Changes.visibleTo(Role.WORKER, rows) { it.type }.map { it.label },
        )
    }

    @Test
    fun `an owner hears everything that is classified`() {
        Changes.LENS_OF.keys.forEach { type ->
            assertTrue("the owner did not hear about $type", Changes.visibleTo(Role.OWNER, type))
        }
    }
}
