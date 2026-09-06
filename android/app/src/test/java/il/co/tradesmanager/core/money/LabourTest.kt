package il.co.tradesmanager.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Overtime is a daily question, not a weekly one, and a rest day is overtime
 * from the first hour. Both are got wrong in the direction that underpays.
 */
class LabourTest {

    private val cent = 0.005

    @Test
    fun `a normal day is all ordinary time`() {
        val day = Labour.split(8.0)
        assertEquals(8.0, day.ordinary, cent)
        assertEquals(0.0, day.atFirstOvertimeRate, cent)
        assertEquals(0.0, day.atFurtherOvertimeRate, cent)
        assertFalse(day.hasOvertime)
        assertEquals(8.0, day.payable, cent)
    }

    @Test
    fun `a short day is not padded out to eight`() {
        val day = Labour.split(5.5)
        assertEquals(5.5, day.ordinary, cent)
        assertEquals(5.5, day.payable, cent)
    }

    @Test
    fun `the first two hours over are worth a quarter more`() {
        val day = Labour.split(10.0)
        assertEquals(8.0, day.ordinary, cent)
        assertEquals(2.0, day.atFirstOvertimeRate, cent)
        assertEquals(0.0, day.atFurtherOvertimeRate, cent)
        // 8 + 2 × 1.25
        assertEquals(10.5, day.payable, cent)
    }

    @Test
    fun `past that it is half as much again`() {
        val day = Labour.split(12.0)
        assertEquals(8.0, day.ordinary, cent)
        assertEquals(2.0, day.atFirstOvertimeRate, cent)
        assertEquals(2.0, day.atFurtherOvertimeRate, cent)
        // 8 + 2 × 1.25 + 2 × 1.5
        assertEquals(13.5, day.payable, cent)
    }

    @Test
    fun `one hour over lands entirely in the first band`() {
        val day = Labour.split(9.0)
        assertEquals(1.0, day.atFirstOvertimeRate, cent)
        assertEquals(0.0, day.atFurtherOvertimeRate, cent)
        assertEquals(9.25, day.payable, cent)
    }

    @Test
    fun `a rest day is overtime from the first hour, not after eight`() {
        // The one that underpays if it is got wrong: eight hours of ordinary
        // pay on a day of rest.
        val day = Labour.split(8.0, isRestDay = true)
        assertEquals(0.0, day.ordinary, cent)
        assertEquals(8.0, day.atFurtherOvertimeRate, cent)
        assertEquals(12.0, day.payable, cent)
        assertTrue(day.hasOvertime)
    }

    @Test
    fun `a short rest day is still paid from the first hour`() {
        val day = Labour.split(3.0, isRestDay = true)
        assertEquals(0.0, day.ordinary, cent)
        assertEquals(4.5, day.payable, cent)
    }

    @Test
    fun `a firm on a longer ordinary day gets its own number, not the default`() {
        // A collective agreement can be more generous than the law. The app is
        // not the authority on what somebody's day is.
        val day = Labour.split(10.0, ordinaryDayHours = 9.0)
        assertEquals(9.0, day.ordinary, cent)
        assertEquals(1.0, day.atFirstOvertimeRate, cent)
        assertEquals(10.25, day.payable, cent)
    }

    @Test
    fun `nothing worked costs nothing and splits into nothing`() {
        val day = Labour.split(0.0)
        assertEquals(0.0, day.total, cent)
        assertEquals(0.0, day.payable, cent)
        // A negative span is a clock that went backwards, not negative work.
        assertEquals(0.0, Labour.split(-3.0).total, cent)
    }

    @Test
    fun `overtime is counted daily, so two long days are not one longer one`() {
        // Two nine-hour days are two hours of overtime. Adding them to eighteen
        // and splitting once would make it ten, and cost the job the difference.
        val week = Labour.week(listOf(Labour.split(9.0), Labour.split(9.0)))
        assertEquals(16.0, week.ordinary, cent)
        assertEquals(2.0, week.atFirstOvertimeRate, cent)
        assertEquals(0.0, week.atFurtherOvertimeRate, cent)
        assertEquals(18.5, week.payable, cent)

        val wrong = Labour.split(18.0)
        assertEquals(8.0, wrong.atFurtherOvertimeRate, cent)
        assertTrue(wrong.payable > week.payable)
    }

    @Test
    fun `a week mixing ordinary days, a long one and a rest day adds up in bands`() {
        val week = Labour.week(
            listOf(
                Labour.split(8.0),
                Labour.split(8.0),
                Labour.split(11.0),
                Labour.split(6.0, isRestDay = true),
            ),
        )
        // Three ordinary eights (the 11-hour day contributes 8), two hours in
        // the first band, and seven in the second: one from the long day and
        // the whole six-hour rest day.
        assertEquals(24.0, week.ordinary, cent)
        assertEquals(2.0, week.atFirstOvertimeRate, cent)
        assertEquals(7.0, week.atFurtherOvertimeRate, cent)
        assertEquals(33.0, week.total, cent)
        // 24 + 2 × 1.25 + 7 × 1.5
        assertEquals(37.0, week.payable, cent)
    }

    @Test
    fun `a day with no rate recorded costs an unknown amount, not nothing`() {
        // Showing it as free would quietly make a job look profitable.
        assertNull(Labour.cost(Labour.split(8.0), hourlyRate = null))
        assertEquals(500.0, Labour.cost(Labour.split(8.0), hourlyRate = 62.5)!!, cent)
    }

    @Test
    fun `overtime costs what the bands say it costs`() {
        val day = Labour.split(12.0)
        // 13.5 payable hours at 50 an hour.
        assertEquals(675.0, Labour.cost(day, 50.0)!!, cent)
    }

    @Test
    fun `minutes become decimal hours without rounding on the way`() {
        assertEquals(8.0, Labour.hoursFrom(480L), cent)
        assertEquals(8.5, Labour.hoursFrom(510L), cent)
        assertEquals(0.25, Labour.hoursFrom(15L), cent)
        assertEquals(0.0, Labour.hoursFrom(0L), cent)
    }
}
