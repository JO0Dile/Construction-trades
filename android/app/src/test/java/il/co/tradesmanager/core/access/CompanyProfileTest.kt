package il.co.tradesmanager.core.access

import il.co.tradesmanager.core.access.CompanyProfile.Audience
import il.co.tradesmanager.core.access.CompanyProfile.Field
import il.co.tradesmanager.core.access.CompanyProfile.Publication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanyProfileTest {

    private val firm = "org.firm"
    private val published = Publication(setOf(Field.PHONE))

    @Test
    fun `nothing is published by default`() {
        Field.entries.forEach { field ->
            assertFalse(
                "$field should not be published by a firm that never chose to",
                CompanyProfile.visibleTo(field, Audience.WORKFORCE, Publication()),
            )
        }
    }

    @Test
    fun `the firm sees its own details whatever it published`() {
        Field.entries.forEach { field ->
            assertTrue(CompanyProfile.visibleTo(field, Audience.INSIDE, Publication()))
        }
    }

    @Test
    fun `the workforce sees exactly what was published and no more`() {
        assertTrue(CompanyProfile.visibleTo(Field.PHONE, Audience.WORKFORCE, published))
        assertFalse(CompanyProfile.visibleTo(Field.EMAIL, Audience.WORKFORCE, published))
        assertFalse(CompanyProfile.visibleTo(Field.ADDRESS, Audience.WORKFORCE, published))
    }

    @Test
    fun `another firm sees only what is on an invoice anyway`() {
        val everything = Publication(Field.entries.toSet())
        assertTrue(
            CompanyProfile.visibleTo(Field.REGISTRATION_NUMBER, Audience.OUTSIDE, everything),
        )
        // Publishing to your own crew is not publishing to the world.
        assertFalse(CompanyProfile.visibleTo(Field.PHONE, Audience.OUTSIDE, everything))
        assertFalse(CompanyProfile.visibleTo(Field.ADDRESS, Audience.OUTSIDE, everything))
    }

    @Test
    fun `management is inside the firm, the tools are not`() {
        assertEquals(
            Audience.INSIDE,
            CompanyProfile.audienceOf(firm, firm, managementRole = true),
        )
        assertEquals(
            Audience.WORKFORCE,
            CompanyProfile.audienceOf(firm, firm, managementRole = false),
        )
    }

    @Test
    fun `a crew engaged by the firm reads as its workforce, not as a stranger`() {
        assertEquals(
            Audience.WORKFORCE,
            CompanyProfile.audienceOf(
                viewerCompanyId = "org.crew",
                subjectCompanyId = firm,
                managementRole = false,
                engagedBySubject = true,
            ),
        )
    }

    @Test
    fun `somebody with no company is outside`() {
        assertEquals(Audience.OUTSIDE, CompanyProfile.audienceOf(null, firm, managementRole = true))
    }

    @Test
    fun `every role that touches the books counts as management`() {
        listOf(Role.OWNER, Role.MANAGER, Role.FINANCE, Role.HR).forEach {
            assertTrue("$it should be management", CompanyProfile.isManagement(it))
        }
        assertFalse(CompanyProfile.isManagement(Role.WORKER))
    }
}
