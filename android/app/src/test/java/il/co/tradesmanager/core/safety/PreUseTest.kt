package il.co.tradesmanager.core.safety

import il.co.tradesmanager.core.safety.PreUse.Answer
import il.co.tradesmanager.core.safety.PreUse.Item
import il.co.tradesmanager.core.safety.PreUse.Outcome
import il.co.tradesmanager.core.safety.PreUse.Refusal
import il.co.tradesmanager.core.safety.PreUse.Today
import il.co.tradesmanager.core.safety.PreUse.Verdict
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class PreUseTest {

    private val allOk = Item.entries.associateWith { Answer.OK }
    private val zone = ZoneId.of("Asia/Jerusalem")

    // 2026-09-23 06:30 and 12:00, and 2026-09-22 23:50, Israel time (UTC+3).
    private val morning = 1_790_134_200_000L
    private val noon = 1_790_154_000_000L
    private val lastNight = 1_790_110_200_000L

    @Test
    fun `nothing wrong is fit`() {
        assertEquals(Verdict.Judged(Outcome.FIT, emptyList()), PreUse.judge(allOk, null))
    }

    @Test
    fun `an item left unanswered is not a check`() {
        assertEquals(
            Verdict.Refused(Refusal.UNANSWERED),
            PreUse.judge(allOk - Item.GUARDS, null),
        )
        assertEquals(Verdict.Refused(Refusal.UNANSWERED), PreUse.judge(emptyMap(), "anything"))
    }

    @Test
    fun `a check in which nothing applied looked at nothing`() {
        val nothing = Item.entries.associateWith { Answer.NOT_APPLICABLE }
        assertEquals(Verdict.Refused(Refusal.NOTHING_CHECKED), PreUse.judge(nothing, null))

        // One real look is enough; a small machine really does not have most of these.
        val generator = nothing + (Item.VISIBLE_DAMAGE to Answer.OK)
        assertEquals(Verdict.Judged(Outcome.FIT, emptyList()), PreUse.judge(generator, null))
    }

    @Test
    fun `a defect has to say what it is`() {
        val leaking = allOk + (Item.BRAKES to Answer.DEFECT) + (Item.LEAKS to Answer.DEFECT)

        assertEquals(Verdict.Refused(Refusal.DEFECT_NOT_DESCRIBED), PreUse.judge(leaking, "  "))
        assertEquals(Verdict.Refused(Refusal.DEFECT_NOT_DESCRIBED), PreUse.judge(leaking, null))
        assertEquals(
            Verdict.Judged(Outcome.UNFIT, listOf(Item.LEAKS, Item.BRAKES)),
            PreUse.judge(leaking, "hydraulic leak by the boom ram"),
        )
    }

    @Test
    fun `a check is good for the day it was made, where the phone is`() {
        assertEquals(Today.NEVER_CHECKED, PreUse.today(null, null, noon, zone))
        assertEquals(Today.FIT_TODAY, PreUse.today(morning, Outcome.FIT, noon, zone))
        assertEquals(Today.UNFIT_TODAY, PreUse.today(morning, Outcome.UNFIT, noon, zone))
        assertEquals(
            "ten to midnight does not carry the machine through the morning",
            Today.NOT_CHECKED_TODAY,
            PreUse.today(lastNight, Outcome.FIT, morning, zone),
        )
    }
}
