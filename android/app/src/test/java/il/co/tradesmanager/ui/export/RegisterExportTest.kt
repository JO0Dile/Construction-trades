package il.co.tradesmanager.ui.export

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import il.co.tradesmanager.R
import il.co.tradesmanager.data.local.entity.ComplaintEntity
import il.co.tradesmanager.data.local.entity.DelayEventEntity
import il.co.tradesmanager.data.local.entity.DesignQueryEntity
import il.co.tradesmanager.data.local.entity.InspectionEntity
import il.co.tradesmanager.data.local.entity.RiskAssessmentEntity
import il.co.tradesmanager.data.local.entity.SubmittalEntity
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The inspection, material and delay registers as documents.
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
    fun `a Hebrew printout is headed in Hebrew`() {
        val context = inLanguage("he")
        val table = ExportDocument.DelayRegister("מגדל א", delays, today).table(context, "he", Locale.forLanguageTag("he"))
        table.assertSquare()
        assertTrue(table.title.startsWith("עיכובים"))
        assertEquals("ימים", table.headers[6])
    }
}
