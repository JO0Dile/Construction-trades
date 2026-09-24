package il.co.tradesmanager.core.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CubeTestsTest {

    @Test
    fun `the Israeli grade is read however it is written`() {
        for (written in listOf("B-30", "B30", "b 30", "ב-30", "ב30", "ב־30", "B-30 SCC", "pump mix B-30")) {
            assertEquals(written, 30.0, CubeTests.specifiedMpa(written)!!, 0.0)
        }
    }

    @Test
    fun `the European pair is compared on its cube figure`() {
        assertEquals(37.0, CubeTests.specifiedMpa("C30/37")!!, 0.0)
        assertEquals(30.0, CubeTests.specifiedMpa("c25 / 30")!!, 0.0)
    }

    @Test
    fun `anything it cannot read is left alone rather than guessed`() {
        for (written in listOf(null, "", "30", "pump mix", "ABC30", "C30", "B3")) {
            assertNull(written, CubeTests.specifiedMpa(written))
        }
    }

    @Test
    fun `only twenty-eight days is judged`() {
        val early = CubeTests.judge(7, listOf(18.0, 19.0, 20.0), specifiedMpa = 30.0)
        assertEquals(CubeTests.Verdict.EARLY, early.verdict)
        assertFalse("a low seven-day result is a reason to watch, not a failure", early.needsEngineer)
    }

    @Test
    fun `a low mean, and a low cube in a good mean, both go to the engineer`() {
        val lowMean = CubeTests.judge(28, listOf(28.0, 29.0, 30.5), specifiedMpa = 30.0)
        assertEquals(CubeTests.Verdict.MEAN_BELOW, lowMean.verdict)
        assertTrue(lowMean.needsEngineer)

        val lowCube = CubeTests.judge(28, listOf(33.0, 34.0, 28.5), specifiedMpa = 30.0)
        assertEquals(CubeTests.Verdict.CUBE_BELOW, lowCube.verdict)
        assertEquals(28.5, lowCube.lowestMpa, 0.0)
        assertTrue(lowCube.needsEngineer)
    }

    @Test
    fun `a set at or above the specified strength meets it`() {
        val good = CubeTests.judge(28, listOf(30.0, 33.0, 36.0), specifiedMpa = 30.0)
        assertEquals(CubeTests.Verdict.MEETS, good.verdict)
        assertEquals(33.0, good.meanMpa, 1e-9)
        assertFalse(good.needsEngineer)
    }

    @Test
    fun `with no specified strength there is nothing to judge against`() {
        assertEquals(CubeTests.Verdict.NO_SPECIFICATION, CubeTests.judge(28, listOf(10.0), specifiedMpa = null).verdict)
    }

    @Test
    fun `numbers that cannot be a cube are refused, low ones are not`() {
        assertEquals(CubeTests.Refusal.NO_CUBES, CubeTests.refusal(28, emptyList()))
        assertEquals(CubeTests.Refusal.IMPLAUSIBLE_STRENGTH, CubeTests.refusal(28, listOf(0.0)))
        assertEquals(CubeTests.Refusal.IMPLAUSIBLE_STRENGTH, CubeTests.refusal(28, listOf(300.0)))
        assertEquals(CubeTests.Refusal.IMPLAUSIBLE_STRENGTH, CubeTests.refusal(28, List(CubeTests.MOST_CUBES + 1) { 30.0 }))
        assertEquals(CubeTests.Refusal.BAD_AGE, CubeTests.refusal(0, listOf(30.0)))
        assertNull("a bad result is the one most worth recording", CubeTests.refusal(28, listOf(12.0)))
    }

    @Test
    fun `a stored set is judged from its text, against its own pour's mix`() {
        val stored = CubeTests.judgeStored(28, listOf("28.0", "29.5", "not a number"), mixDesign = "B-30")!!
        assertEquals(CubeTests.Verdict.MEAN_BELOW, stored.verdict)
        assertEquals(30.0, stored.specifiedMpa!!, 0.0)
        assertNull(CubeTests.judgeStored(28, listOf("x"), "B-30"))
    }

    @Test
    fun `a finished pour is owed its twenty-eight-day result until one is in`() {
        assertTrue(CubeTests.awaitingJudgedResult(finished = true, setAges = emptyList()))
        assertTrue("seven days is not the result it is judged on", CubeTests.awaitingJudgedResult(true, listOf(7)))
        assertFalse(CubeTests.awaitingJudgedResult(true, listOf(7, 28)))
        assertFalse("fifty-six days counts too", CubeTests.awaitingJudgedResult(true, listOf(56)))
        assertFalse("a pour still running is outstanding for that instead", CubeTests.awaitingJudgedResult(false, emptyList()))
    }
}
