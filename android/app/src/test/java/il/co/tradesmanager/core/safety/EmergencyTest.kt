package il.co.tradesmanager.core.safety

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A wrong digit here is the one mistake in this app that cannot be put right
 * afterwards, so the numbers are pinned by a test rather than trusted to review.
 */
class EmergencyTest {

    @Test
    fun `the numbers are Israel's public emergency numbers`() {
        assertEquals("101", Emergency.AMBULANCE)
        assertEquals("102", Emergency.FIRE_AND_RESCUE)
        assertEquals("100", Emergency.POLICE)
    }

    @Test
    fun `the ambulance is offered first`() {
        assertEquals(listOf("101", "102", "100"), Emergency.ORDER)
    }
}
