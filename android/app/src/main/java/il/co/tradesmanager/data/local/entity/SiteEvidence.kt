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
