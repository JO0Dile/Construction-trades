package il.co.tradesmanager.ui.export

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import il.co.tradesmanager.R
import il.co.tradesmanager.core.safety.WeeklySafety
import il.co.tradesmanager.core.work.Attention
import il.co.tradesmanager.data.local.entity.ComplaintEntity
import il.co.tradesmanager.data.local.entity.DelayEventEntity
import il.co.tradesmanager.data.local.entity.DesignQueryEntity
import il.co.tradesmanager.data.local.entity.FirePointCheckEntity
import il.co.tradesmanager.data.local.entity.FirePointEntity
import il.co.tradesmanager.data.local.entity.InspectionEntity
import il.co.tradesmanager.data.local.entity.JobEmergencyEntity
import il.co.tradesmanager.data.local.entity.MeetingActionEntity
import il.co.tradesmanager.data.local.entity.MeetingEntity
import il.co.tradesmanager.data.local.entity.RiskAssessmentEntity
import il.co.tradesmanager.data.local.entity.SubmittalEntity
import il.co.tradesmanager.data.local.entity.SubstanceEntity
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The registers as documents.
 *
 * Run through Android's own resources, as the printout is, because the
 * things worth catching are the ones a reader would: a row with a cell
 * missing, a heading in the wrong language, a revision shown without its
 * number, a delay still going counted to the wrong day.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RegisterExportTest {

    private fun inLanguage(tag: String): Context {
        val base = RuntimeEnvironment.getApplication()
        val configuration = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return base.createConfigurationContext(configuration)
    }

    private val today = LocalDate.of(2026, 10, 8)

    private val inspections = listOf(
        InspectionEntity(
            id = "i1", projectId = "job", reference = "IR-001", kind = "REINFORCEMENT", element = "L3 slab",
            requestedOf = "Engineer", requestedAt = 1_000L, requestedByName = "Foreman",
            result = "FAILED", inspectorName = "Dana Levi", comments = "Laps short", decidedAt = 2_000L,
        ),
        InspectionEntity(
            id = "i2", projectId = "job", reference = "IR-002", kind = "REINFORCEMENT", element = "L3 slab",
            requestedOf = "Engineer", requestedAt = 3_000L, requestedByName = "Foreman", reinspectionOf = "i1",
        ),
    )

    private val submittals = listOf(
        SubmittalEntity(
            id = "s1", projectId = "job", reference = "MS-001", revision = 0, item = "Tile, grey",
            submittedTo = "Architect", submittedAt = 1_000L, submittedByName = "Manager",
            decision = "REJECTED", reviewerName = "Noa Cohen", notes = "Wrong shade", decidedAt = 2_000L,
        ),
        SubmittalEntity(
            id = "s2", projectId = "job", reference = "MS-001", revision = 1, item = "Tile, light grey",
            submittedTo = "Architect", submittedAt = 3_000L, submittedByName = "Manager", resubmissionOf = "s1",
        ),
    )

    private val delays = listOf(
        DelayEventEntity(
            id = "d1", projectId = "job", reference = "DE-001", cause = "WEATHER", description = "Rain",
            startedOnDay = today.minusDays(3).toEpochDay(), recordedByName = "Manager", recordedAt = 1_000L,
        ),
        DelayEventEntity(
            id = "d2", projectId = "job", reference = "DE-002", cause = "LATE_INFORMATION", description = "No answer to Q-004",
            startedOnDay = today.minusDays(10).toEpochDay(), endedOnDay = today.minusDays(9).toEpochDay(),
            recordedByName = "Manager", recordedAt = 2_000L, notifiedTo = "Supervisor",
            notifiedOnDay = today.minusDays(10).toEpochDay(),
        ),
    )

    private fun ExportDocument.Table.assertSquare() {
        rows.forEach { assertEquals("every row has a cell for every heading", headers.size, it.size) }
    }

    @Test
    fun `the inspection register has every column, and a request not yet decided reads as waiting`() {
        val context = inLanguage("en")
        val table = ExportDocument.InspectionRegister("Tower A", inspections).table(context, "en", Locale.ENGLISH)
        table.assertSquare()
        assertTrue(table.title.endsWith("Tower A"))
        assertEquals(listOf("IR-001", "IR-002"), table.rows.map { it[0] })
        assertEquals(context.getString(R.string.ir_result_failed), table.rows[0][5])
        assertEquals(context.getString(R.string.ex_waiting), table.rows[1][5])
        assertEquals("Laps short", table.rows[0][8])
    }

    @Test
    fun `the material register shows each revision under its number`() {
        val context = inLanguage("en")
        val table = ExportDocument.SubmittalRegister("Tower A", submittals).table(context, "en", Locale.ENGLISH)
        table.assertSquare()
        assertEquals("MS-001", table.rows[0][0])
        assertEquals(context.getString(R.string.ms_revision_of, "MS-001", 1), table.rows[1][0])
        assertEquals(context.getString(R.string.ms_decision_rejected), table.rows[0][5])
        assertEquals(context.getString(R.string.ex_waiting), table.rows[1][5])
    }

    @Test
    fun `the delay register counts a delay still going to the day it was printed, and says where notice is missing`() {
        val context = inLanguage("en")
        val table = ExportDocument.DelayRegister("Tower A", delays, today).table(context, "en", Locale.ENGLISH)
        table.assertSquare()
        // Oldest first: the late answer, then the rain.
        assertEquals(listOf("DE-002", "DE-001"), table.rows.map { it[0] })
        assertEquals("two days, counting both", "2", table.rows[0][6])
        assertEquals("still going, counted to the printout's day", "4", table.rows[1][6])
        assertEquals(context.getString(R.string.ex_still_going), table.rows[1][5])
        assertEquals(context.getString(R.string.de_no_notice), table.rows[1][7])
        assertTrue(table.rows[0][7].startsWith("Supervisor"))
    }

    @Test
    fun `the risk register shows each score with its band, and a closed risk as closed`() {
        val context = inLanguage("en")
        val risks = listOf(
            RiskAssessmentEntity(
                id = "r1", projectId = "job", reference = "RA-001", activity = "Formwork at the edge", hazard = "Fall",
                likelihoodBefore = 4, severityBefore = 5, controls = "Edge protection", likelihoodAfter = 2, severityAfter = 5,
                recordedByName = "Safety officer", createdAt = 1_000L,
            ),
            RiskAssessmentEntity(
                id = "r2", projectId = "job", reference = "RA-002", activity = "Deliveries", hazard = "Struck by a lorry",
                likelihoodBefore = 2, severityBefore = 2, likelihoodAfter = 2, severityAfter = 2,
                closed = true, recordedByName = "Safety officer", createdAt = 2_000L,
            ),
        )
        val table = ExportDocument.RiskRegister("Tower A", risks).table(context, "en", Locale.ENGLISH)
        table.assertSquare()
        assertEquals("20 " + context.getString(R.string.ra_band_extreme), table.rows[0][4])
        assertEquals("10 " + context.getString(R.string.ra_band_high), table.rows[0][6])
        assertEquals(context.getString(R.string.ra_closed), table.rows[1][8])
    }

    @Test
    fun `the questions register shows an unanswered question as waiting`() {
        val context = inLanguage("en")
        val queries = listOf(
            DesignQueryEntity(
                id = "q1", projectId = "job", reference = "Q-001", question = "Beam clashes with the duct",
                askedOf = "Engineer", drawingNumber = "S-201", askedAt = 1_000L, askedByName = "Foreman",
                answer = "Drop the duct 200", answeredAt = 2_000L,
            ),
            DesignQueryEntity(
                id = "q2", projectId = "job", reference = "Q-002", question = "Which tile?",
                askedOf = "Architect", askedAt = 3_000L, askedByName = "Foreman",
            ),
        )
        val table = ExportDocument.QueryRegister("Tower A", queries).table(context, "en", Locale.ENGLISH)
        table.assertSquare()
        assertEquals("Drop the duct 200", table.rows[0][6])
        assertEquals(context.getString(R.string.ex_waiting), table.rows[1][6])
    }

    @Test
    fun `the complaints register never prints how to reach the person who complained`() {
        val context = inLanguage("en")
        val complaints = listOf(
            ComplaintEntity(
                id = "c1", projectId = "job", reference = "CP-001", fromWhom = "Neighbour at 12 Herzl",
                contact = "054-1234567", channel = "PHONE", subject = "NOISE", description = "Pump at six",
                receivedAt = 1_000L, receivedByName = "Safety officer",
            ),
        )
        val table = ExportDocument.ComplaintRegister("Tower A", complaints).table(context, "en", Locale.ENGLISH)
        table.assertSquare()
        assertTrue(table.rows.flatten().none { it.contains("054-1234567") })
        assertTrue(table.extraCells.flatten().none { it.contains("054-1234567") })
        assertEquals(context.getString(R.string.ex_waiting), table.rows[0][5])
    }

    @Test
    fun `the substances register says which have no data sheet, which have an old one, and which have gone`() {
        val context = inLanguage("en")
        val substances = listOf(
            SubstanceEntity(
                id = "h1", projectId = "job", reference = "HS-001", name = "Diesel", hazards = "FLAMMABLE,HARMFUL",
                keptWhere = "Cage by the gate", quantity = "200 l", addedAt = 1_000L, addedByName = "Safety officer",
            ),
            SubstanceEntity(
                id = "h2", projectId = "job", reference = "HS-002", name = "Form oil", hazards = "ENVIRONMENT",
                keptWhere = "Store", sheetOnDay = today.minusYears(7).toEpochDay(), addedAt = 2_000L, addedByName = "Safety officer",
            ),
            SubstanceEntity(
                id = "h3", projectId = "job", reference = "HS-003", name = "Acid", hazards = "CORROSIVE",
                keptWhere = "Store", sheetOnDay = today.minusYears(1).toEpochDay(), addedAt = 3_000L, addedByName = "Safety officer",
                removedAt = 4_000L, removedByName = "Site manager",
            ),
        )
        val table = ExportDocument.SubstanceRegister("Tower A", substances, today).table(context, "en", Locale.ENGLISH)
        table.assertSquare()
        assertEquals(listOf("HS-001", "HS-002", "HS-003"), table.rows.map { it[0] })
        assertEquals(
            context.getString(R.string.hs_hazard_flammable) + ", " + context.getString(R.string.hs_hazard_harmful),
            table.rows[0][2],
        )
        assertEquals(context.getString(R.string.hs_no_sheet), table.rows[0][7])
        assertTrue(table.rows[1][7].endsWith(context.getString(R.string.hs_sheet_old, "").substringAfter(":").trim()))
        assertEquals("still on site", "", table.rows[0][8])
        assertTrue("gone, with the day", table.rows[2][8].isNotEmpty())
    }

    @Test
    fun `the fire points register shows the newest look, and the CSV carries every one`() {
        val context = inLanguage("en")
        val zone = ZoneId.systemDefault()
        val now = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        fun daysAgo(days: Long) = today.minusDays(days).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val points = listOf(
            FirePointEntity(
                id = "f1", projectId = "job", reference = "FP-001", kind = "CO2", location = "Stair 2",
                serviceDueOnDay = today.minusDays(2).toEpochDay(), addedAt = 1_000L, addedByName = "Safety officer",
            ),
            FirePointEntity(
                id = "f2", projectId = "job", reference = "FP-002", kind = "HOSE_REEL", location = "Gate",
                addedAt = 2_000L, addedByName = "Safety officer",
            ),
        )
        val checks = listOf(
            FirePointCheckEntity(id = "c1", firePointId = "f1", projectId = "job", checkedAt = daysAgo(40), ok = true, checkedByName = "Foreman"),
            FirePointCheckEntity(
                id = "c2", firePointId = "f1", projectId = "job", checkedAt = daysAgo(3), ok = false,
                note = "Pin missing", checkedByName = "Foreman",
            ),
        )
        val table = ExportDocument.FirePointRegister("Tower A", points, checks, now).table(context, "en", Locale.ENGLISH)
        table.assertSquare()
        assertEquals(listOf("FP-001", "FP-002"), table.rows.map { it[0] })
        assertEquals(context.getString(R.string.fp_kind_co2), table.rows[0][1])
        assertTrue("a service date passed says so", table.rows[0][4].endsWith(context.getString(R.string.ex_overdue)))
        assertEquals(context.getString(R.string.fp_fault) + " — Pin missing", table.rows[0][6])
        assertEquals(context.getString(R.string.fp_state_never_checked), table.rows[1][5])
        assertEquals("both looks, newest first", 2, table.extraCells[0][0].split("; ").size)
        assertTrue(table.extraCells[0][0].startsWith(table.rows[0][5]))
    }

    @Test
    fun `the emergency sheet prints the national numbers first, and a gap as not recorded`() {
        val context = inLanguage("en")
        val sheet = JobEmergencyEntity(
            projectId = "job", hospitalName = "Soroka", hospitalPhone = "08-6400111", assemblyPoint = "Car park by the gate",
            updatedAt = 1_000L, updatedByName = "Safety officer",
        )
        val table = ExportDocument.EmergencyInformation("Tower A", "12 Herzl, Beersheba", sheet).table(context, "en", Locale.ENGLISH)
        table.assertSquare()
        assertEquals(context.getString(R.string.muster_call_ambulance), table.rows[0][1])
        assertEquals("12 Herzl, Beersheba", table.rows[3][1])
        assertEquals("Soroka · 08-6400111", table.rows[4][1])
        assertEquals(context.getString(R.string.es_not_recorded), table.rows[6][1])
        val blank = ExportDocument.EmergencyInformation("Tower A", "", null).table(context, "en", Locale.ENGLISH)
        blank.assertSquare()
        assertEquals("the numbers print even with nothing recorded", 3 + 8, blank.rows.size)
        assertTrue(blank.rows.drop(3).all { it[1] == context.getString(R.string.es_not_recorded) })
    }

    @Test
    fun `the action log lists every point under its meeting, overdue said as overdue and done with what was done`() {
        val context = inLanguage("en")
        val meetings = listOf(
            MeetingEntity(
                id = "m1", projectId = "job", reference = "MT-001", kind = "COORDINATION", heldOnDay = today.minusDays(14).toEpochDay(),
                recordedByName = "Site manager", recordedAt = 1_000L,
            ),
        )
        val actions = listOf(
            MeetingActionEntity(
                id = "a1", meetingId = "m1", projectId = "job", reference = "MT-001/1", text = "Send rev C", ownerName = "Architect",
                dueOnDay = today.minusDays(7).toEpochDay(), raisedAt = 1_000L,
            ),
            MeetingActionEntity(
                id = "a2", meetingId = "m1", projectId = "job", reference = "MT-001/2", text = "Book the pump",
                raisedAt = 2_000L, closedAt = 3_000L, closedByName = "Foreman", closingNote = "Booked for Tuesday",
            ),
        )
        val table = ExportDocument.MeetingActionLog("Tower A", meetings, actions, today).table(context, "en", Locale.ENGLISH)
        table.assertSquare()
        assertEquals(listOf("MT-001/1", "MT-001/2"), table.rows.map { it[0] })
        assertTrue(table.rows[0][1].startsWith(context.getString(R.string.mt_kind_coordination)))
        assertEquals(context.getString(R.string.ex_overdue), table.rows[0][5])
        assertEquals("Booked for Tuesday", table.rows[1][6])
        assertTrue(table.rows[1][5].isNotEmpty())
    }

    @Test
    fun `the weekly safety report prints every count, then what is still open`() {
        val context = inLanguage("en")
        val report = WeeklySafety.Report(peopleOnSite = 14, nearMisses = 2, incidents = 1)
        val open = listOf(Attention.Line(Attention.Item.FIRE_POINTS, 3))
        val table = ExportDocument.WeeklySafetyReport("Tower A", LocalDate.of(2026, 10, 4), report, open).table(context, "en", Locale.ENGLISH)
        table.assertSquare()
        assertEquals(11 + 1, table.rows.size)
        assertEquals(listOf(context.getString(R.string.ws_people), "14"), table.rows[0])
        assertEquals(listOf(context.getString(R.string.ws_incidents), "1"), table.rows[5])
        assertTrue(table.rows.last()[0].startsWith(context.getString(R.string.ws_open_now)))
        assertEquals("3", table.rows.last()[1])
    }

    @Test
    fun `a Hebrew printout is headed in Hebrew`() {
        val context = inLanguage("he")
        val table = ExportDocument.DelayRegister("מגדל א", delays, today).table(context, "he", Locale.forLanguageTag("he"))
        table.assertSquare()
        assertTrue(table.title.startsWith("עיכובים"))
        assertEquals("ימים", table.headers[6])
    }
}
