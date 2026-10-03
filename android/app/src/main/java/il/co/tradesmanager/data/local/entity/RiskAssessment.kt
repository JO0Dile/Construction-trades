package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One hazard in one activity on one job, scored before and after its controls.
 *
 * A living row, unlike the registers that write their answer once: an
 * assessment is reviewed and rescored as the work changes, and every change
 * goes through the audit trail, which is where its history lives. Review days
 * are the site's calendar days (LocalDate.toEpochDay). See core.safety.Risks.
 */
@Entity(
    tableName = "risk_assessments",
    indices = [Index("projectId")],
)
data class RiskAssessmentEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    /** RA-001, numbered per job. */
    val reference: String,
    /** The work: "Formwork at the slab edge, level 5". */
    val activity: String,
    /** What could hurt somebody in it: "Fall from height". */
    val hazard: String,
    /** Who is exposed: "carpenters, anyone below". */
    val whoAtRisk: String? = null,
    val likelihoodBefore: Int,
    val severityBefore: Int,
    /** What is done about it. */
    val controls: String? = null,
    val likelihoodAfter: Int,
    val severityAfter: Int,
    /** Who sees to the controls. */
    val ownerName: String? = null,
    val reviewOnDay: Long? = null,
    val closed: Boolean = false,
    val recordedByName: String,
    val createdAt: Long,
    val lastReviewedAt: Long? = null,
    val lastReviewedByName: String? = null,
)
