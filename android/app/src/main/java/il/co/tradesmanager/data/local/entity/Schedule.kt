package il.co.tradesmanager.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One block on a day's plan. The day is stored as an epoch day and the times as
 * minutes past midnight, so a block never shifts when a phone crosses a
 * timezone or a daylight-saving boundary — the 07:00 start stays 07:00.
 */
@Entity(tableName = "task_blocks", indices = [Index("epochDay"), Index("projectId")])
data class TaskBlockEntity(
    @PrimaryKey val id: String,
    val epochDay: Long,
    val startMinute: Int,
    val endMinute: Int,
    val title: String,
    val notes: String? = null,
    val projectId: String? = null,
    val assigneeId: String? = null,
    /** RRULE-style repeat, e.g. FREQ=WEEKLY;BYDAY=SU,MO. Null means one-off. */
    val recurrenceRule: String? = null,
    val isDone: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A check-in/check-out pair, optionally stamped with where it happened. */
@Entity(tableName = "time_entries", indices = [Index("projectId"), Index("checkInAt")])
data class TimeEntryEntity(
    @PrimaryKey val id: String,
    val blockId: String? = null,
    val projectId: String? = null,
    val workerId: String? = null,
    val workerName: String,
    val checkInAt: Long,
    val checkOutAt: Long? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val hourlyRate: Double? = null,
) {
    val minutesWorked: Long?
        get() = checkOutAt?.let { (it - checkInAt) / 60_000L }
}
