package il.co.tradesmanager.core.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartyTest {

    @Test
    fun `the chain only ever runs downward`() {
        assertTrue(Party.SECOND_TIER.mayEngage().isEmpty())
        assertTrue(Party.SUPPLIER.mayEngage().isEmpty())
        assertFalse(Party.FIRST_TIER.mayEngage().contains(Party.GENERAL_CONTRACTOR))
        assertTrue(Party.FIRST_TIER.mayEngage().contains(Party.SECOND_TIER))
    }

    @Test
    fun `every engagement goes to something deeper or beside the chain`() {
        Party.entries.forEach { above ->
            above.mayEngage().forEach { below ->
                assertTrue(
                    "$above may engage $below, which is not below it",
                    below.depth > above.depth || below == Party.CONSULTANT,
                )
            }
        }
    }

    @Test
    fun `an unknown position falls to the one that can do least`() {
        assertEquals(Party.SECOND_TIER, Party.parse("PRIME_CONTRACTOR"))
        assertEquals(Party.SECOND_TIER, Party.parse(null))
        assertEquals(Party.SECOND_TIER, Party.parse(""))
        assertFalse(Party.parse("nonsense").canSubcontract)
    }

    @Test
    fun `position is read case-insensitively and trimmed`() {
        assertEquals(Party.GENERAL_CONTRACTOR, Party.parse(" general_contractor "))
    }
}
