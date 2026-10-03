package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One site meeting as minuted: the kind, the day, who was there and what
 * was said. Written once; the points it raised are [MeetingActionEntity]
 * rows. See core.work.Meetings.
 */
@Entity(
    tableName = "meetings",
    indices = [Index("projectId")],
)
data class MeetingEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    /** MT-001, numbered per job. */
    val reference: String,
    /** A Meetings.Kind name. */
    val kind: String,
    val heldOnDay: Long,
    /** As the minutes list them: "Site manager, electrician (Cohen Ltd), architect". */
    val attendees: String? = null,
    val notes: String? = null,
    val recordedByName: String,
    val recordedAt: Long,
)

/**
 * One point agreed at a meeting: what, who, by when, and -- once it is
 * done -- what was done and who said so. Closed, never deleted.
 */
@Entity(
    tableName = "meeting_actions",
    indices = [Index("meetingId"), Index("projectId")],
)
data class MeetingActionEntity(
    @PrimaryKey val id: String,
    val meetingId: String,
    /** Copied from the meeting, so a job's open points are read without a join. */
    val projectId: String,
    /** MT-004/2. */
    val reference: String,
    val text: String,
    val ownerName: String? = null,
    val dueOnDay: Long? = null,
    val raisedAt: Long,
    val closedAt: Long? = null,
    val closedByName: String? = null,
    val closingNote: String? = null,
)
