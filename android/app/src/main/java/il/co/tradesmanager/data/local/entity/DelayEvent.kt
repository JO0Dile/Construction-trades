package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One stretch of days the work could not go ahead.
 *
 * Days are kept as the site's own calendar day (LocalDate.toEpochDay), not
 * as a moment: rain "on Sunday" is Sunday wherever the phone thinks it is.
 * The end and the notice are each written once. Photographs -- the flooded
 * trench, the locked gate -- are in the photos table under `Owner.DELAY`.
 * See core.work.Delays for the rules.
 */
@Entity(
    tableName = "delay_events",
    indices = [Index("projectId")],
)
data class DelayEventEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    /** DE-001, numbered per job in the order recorded. */
    val reference: String,
    /** A Delays.Cause name. */
    val cause: String,
    /** What happened. */
    val description: String,
    /** The work it held: "Level 4 slab pour", "external plaster, north side". */
    val affectedWork: String? = null,
    val startedOnDay: Long,
    val endedOnDay: Long? = null,
    /** What it rests on: "Q-004", the engineer's letter, the client's instruction number. */
    val relatedReference: String? = null,
    val recordedByName: String,
    val recordedAt: Long,
    val endRecordedByName: String? = null,
    /** Who was told, formally, and on what day. */
    val notifiedTo: String? = null,
    val notifiedOnDay: Long? = null,
    val noticeRecordedByName: String? = null,
)
