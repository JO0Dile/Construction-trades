package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A lift plan: one load, one appliance, one set of numbers off the duty chart.
 *
 * The weights and the radius are the reason this is a row rather than a note.
 * A crane at ninety-six per cent of its chart at thirty metres is a different
 * machine from the same crane at forty per cent, and the difference is a
 * calculation nobody does twice — least of all with a load in the air. Writing
 * it down before the lift is the only moment it can be checked.
 *
 * [windLimitKmh] belongs to the appliance rather than the app: a tower crane
 * comes out of service around twenty metres a second, a mobile crane's limit is
 * whatever its manufacturer says, and a single hard-coded number would be wrong
 * for one of them.
 */
@Entity(
    tableName = "lift_plans",
    indices = [Index("projectId"), Index("plannedFor")],
)
data class LiftPlanEntity(
    @PrimaryKey val id: String,
    /** Site form: LIFT-001. */
    val reference: String,
    val projectId: String,
    /** What is going up, in the words used on the radio. */
    val description: String,
    val loadWeightKg: Double?,
    /** Slings, shackles, spreader beam, hook block — all on the same rope. */
    val riggingWeightKg: Double?,
    val applianceName: String?,
    /** Optional link to the plant register, when the crane is one of ours. */
    val applianceId: String?,
    /**
     * When the appliance's own periodic test runs out.
     *
     * Not derived from the plant register: a hired crane is not in it, and the
     * certificate is a fact about the machine on site today.
     */
    val applianceCertificateExpiresOn: Long?,
    /** False for an appliance that needs no periodic test. Rare, but real. */
    val applianceCertificateRequired: Boolean,
    val radiusMetres: Double?,
    /** Off the duty chart, at the radius above. Not the crane's headline tonnage. */
    val capacityAtRadiusKg: Double?,
    val windLimitKmh: Double,
    /** What somebody read on the day. Null means nobody looked. */
    val windSpeedKmh: Double?,
    val plannedFor: Long?,
    val approvedByName: String?,
    val approvedAt: Long?,
    val completedAt: Long?,
    val notes: String?,
    val createdByName: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One person on one lift, and the ticket they are doing it on.
 *
 * A child row rather than three columns because the ticket matters as much as
 * the name. A slinger holds several certificates and only one of them is the
 * slinging ticket; blocking a lift because somebody's first-aid card lapsed
 * would be wrong, and blocking it because their slinging ticket lapsed is the
 * entire point. So the plan names which certificate it is relying on, the way
 * a paper lift plan carries a name and a licence number.
 *
 * [certificateExpiresOn] is copied from that certificate rather than joined at
 * read time: the date is what is being relied on, and a plan that silently
 * changes its mind because somebody edited a certificate row afterwards is not
 * a record of anything. The state is still computed against the clock, so a
 * ticket that runs out between planning and lifting still stops the lift.
 */
@Entity(
    tableName = "lift_crew",
    foreignKeys = [
        ForeignKey(
            entity = LiftPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("planId")],
)
data class LiftCrewEntity(
    @PrimaryKey val id: String,
    val planId: String,
    /** OPERATOR, SLINGER or BANKSMAN — see LiftingRepository. */
    val role: String,
    /** Set when this is somebody with an account. Null for a hired-in operator. */
    val accountId: String?,
    val name: String,
    val certificationId: String?,
    /** The number on the card, for an inspector who asks. */
    val certificateReference: String?,
    /** Null means the ticket does not expire, which is a pass, not a gap. */
    val certificateExpiresOn: Long?,
    val createdAt: Long,
)
