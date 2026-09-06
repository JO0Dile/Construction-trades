package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A safety briefing that was actually given, and who stood there for it.
 *
 * The talk row on its own proves nothing — anybody can record that a briefing
 * happened. The attendance register is the evidence, which is why the two are
 * separate tables and why a talk with an empty register is treated as
 * unfinished rather than as a record.
 */
@Entity(tableName = "toolbox_talks", indices = [Index("projectId"), Index("heldAt")])
data class ToolboxTalkEntity(
    @PrimaryKey val id: String,
    /** Null for a yard or office briefing that is not against a job. */
    val projectId: String?,
    val topic: String,
    val notes: String?,
    val heldAt: Long,
    val presenterName: String,
    val presenterId: String?,
    /** Drawn on the device by whoever gave the talk. */
    val signatureStrokes: String?,
    val createdAt: Long,
)

/**
 * One person on a talk's register.
 *
 * [name] is stored rather than only [accountId] because the register has to
 * keep reading correctly years later, including for a labourer who was on site
 * for a fortnight and never had an account on anybody's phone.
 */
@Entity(
    tableName = "toolbox_talk_attendees",
    foreignKeys = [
        ForeignKey(
            entity = ToolboxTalkEntity::class,
            parentColumns = ["id"],
            childColumns = ["talkId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("talkId"), Index("accountId")],
)
data class ToolboxTalkAttendeeEntity(
    @PrimaryKey val id: String,
    val talkId: String,
    val accountId: String?,
    val name: String,
    val signatureStrokes: String?,
    val signedAt: Long?,
)

/**
 * A permit to work: authorisation for one dangerous job, for one window of
 * time, on conditions somebody checked.
 *
 * [status] records what a person did. Whether the permit authorises anything
 * right now is worked out from the clock by `core.evidence.Permits`, never
 * read off this column — nobody goes round updating rows at five o'clock.
 */
@Entity(
    tableName = "permits",
    indices = [Index("projectId"), Index("status"), Index("validTo")],
)
data class PermitEntity(
    @PrimaryKey val id: String,
    /** Site form: PTW-001. Permits get shouted across a site; they need a name. */
    val reference: String,
    val projectId: String?,
    /** HOT_WORK, HEIGHT, CONFINED_SPACE, EXCAVATION, ELECTRICAL. */
    val type: String,
    /** DRAFT, ISSUED, CLOSED or CANCELLED. */
    val status: String,
    val description: String,
    val location: String?,
    /** Who authorised it. Null until it is issued. */
    val issuedByName: String?,
    /** Who is doing the work, and who has to sign it back. */
    val issuedToName: String,
    val validFrom: Long?,
    val validTo: Long?,
    val issuedAt: Long?,
    val issuerSignature: String?,
    /**
     * When the work actually stopped, which is not when the permit runs out.
     *
     * On hot work it is the moment the fire watch starts, and the reason it is
     * stored separately: a welder who finishes at two on a permit that runs to
     * five has an hour to be watched from two, not from five.
     */
    val workStoppedAt: Long?,
    val closedAt: Long?,
    val closedByName: String?,
    val closeNotes: String?,
    val createdBy: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One condition that has to hold before the work may start.
 *
 * The wording is copied onto the row when the permit is raised rather than
 * referenced from the app's string table. A permit is a record of what a
 * person read and ticked, and it must not quietly change its meaning because
 * a later version of the app reworded a line.
 */
@Entity(
    tableName = "permit_precautions",
    foreignKeys = [
        ForeignKey(
            entity = PermitEntity::class,
            parentColumns = ["id"],
            childColumns = ["permitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("permitId")],
)
data class PermitPrecautionEntity(
    @PrimaryKey val id: String,
    val permitId: String,
    val label: String,
    val checked: Boolean,
    val checkedAt: Long?,
    val sortOrder: Int,
)

/**
 * A defect found on a job.
 *
 * [status] records what somebody did about it; whether that means it is
 * finished is `core.evidence.Snags`' business, and the answer is no until
 * somebody other than the person who claimed it has been and looked.
 *
 * The pictures are not on this row. A snag has two of them — the one that
 * raised it and the one that says it was put right — and they live in the
 * photos table under their own owner types, which is why adding snagging
 * needed no change to how photos work.
 */
@Entity(
    tableName = "snags",
    indices = [Index("projectId"), Index("status"), Index("dueOn")],
)
data class SnagEntity(
    @PrimaryKey val id: String,
    /** Site form: SNAG-001. A defect gets argued about; an argument needs a name. */
    val reference: String,
    /** Never null. A snag that belongs to no job belongs to nobody. */
    val projectId: String,
    val title: String,
    /** Which room, which floor, which wall. The half of a snag that saves a walk. */
    val location: String?,
    val tradeId: String?,
    val assignedToName: String?,
    /** OPEN, FIXED, CLOSED or REJECTED — see Snags.Status. */
    val status: String,
    /**
     * Whether this one holds up handover.
     *
     * Separate from the outstanding count on purpose: a scuff to touch up next
     * week is a real snag that stays on the list without pretending to hold up
     * a building.
     */
    val blocksHandover: Boolean,
    val raisedByName: String,
    val raisedAt: Long,
    val dueOn: Long?,
    val fixedByName: String?,
    val fixedAt: Long?,
    val verifiedByName: String?,
    val verifiedAt: Long?,
    val verifyNotes: String?,
    val updatedAt: Long,
)

/**
 * One day on one job — the יומן עבודה an Israeli site manager has to keep.
 *
 * Only the columns here are typed. Everything else in the log is gathered from
 * what the app already watched happen that day, so the manager is not asked to
 * remember at five o'clock what was delivered at nine.
 *
 * [logDate] is a day number rather than a timestamp, so a log for the fifth of
 * September stays the fifth of September when the record is opened in another
 * country years later. Unique per job per day: two logs for one day is two
 * accounts of it, which is worse than none.
 */
@Entity(
    tableName = "daily_logs",
    indices = [
        Index(value = ["projectId", "logDate"], unique = true),
        Index("logDate"),
    ],
)
data class DailyLogEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    /** Days since 1970-01-01, in the site's local calendar. */
    val logDate: Long,
    /** DRAFT or SIGNED — see core.evidence.DailyLog. */
    val status: String,
    val weather: String?,
    /** What a clock-in system would know. Until there is one, somebody counts. */
    val workforceCount: Int?,
    val notes: String?,
    val preparedByName: String,
    val preparedById: String?,
    val signature: String?,
    val signedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)
