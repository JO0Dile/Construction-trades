package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.TaskBlockEntity
import il.co.tradesmanager.data.local.entity.TimeEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Query("SELECT * FROM task_blocks WHERE epochDay = :epochDay ORDER BY startMinute")
    fun observeDay(epochDay: Long): Flow<List<TaskBlockEntity>>

    @Query("SELECT * FROM task_blocks WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, startMinute")
    fun observeRange(from: Long, to: Long): Flow<List<TaskBlockEntity>>

    @Query("SELECT * FROM task_blocks WHERE id = :id")
    suspend fun block(id: String): TaskBlockEntity?

    @Upsert
    suspend fun upsert(block: TaskBlockEntity)

    @Upsert
    suspend fun upsertAll(blocks: List<TaskBlockEntity>)

    @Query("DELETE FROM task_blocks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE task_blocks SET isDone = :done, updatedAt = :now WHERE id = :id")
    suspend fun setDone(id: String, done: Boolean, now: Long)

    @Upsert
    suspend fun upsertTimeEntry(entry: TimeEntryEntity)

    @Query("SELECT * FROM time_entries WHERE checkOutAt IS NULL ORDER BY checkInAt DESC LIMIT 1")
    fun observeOpenTimeEntry(): Flow<TimeEntryEntity?>

    @Query("SELECT * FROM time_entries WHERE projectId = :projectId ORDER BY checkInAt DESC")
    fun observeTimeEntries(projectId: String): Flow<List<TimeEntryEntity>>

    /**
     * Everybody whose check-in is still open, anywhere, oldest first.
     *
     * Read once when a roll call starts. Not filtered by job on purpose: a
     * roll call over-includes or it is useless, and a man whose check-in
     * carries the wrong job -- or no job at all, which is every entry this app
     * wrote before it knew about jobs -- has to appear on the list somebody is
     * shouting names off.
     *
     * Oldest first so the man who has been in there longest is at the top.
     */
    @Query("SELECT * FROM time_entries WHERE checkOutAt IS NULL ORDER BY checkInAt LIMIT 1000")
    suspend fun openCheckIns(): List<TimeEntryEntity>

    /**
     * How many are on site right now, for a screen that wants the number and
     * not the names.
     */
    @Query("SELECT COUNT(*) FROM time_entries WHERE checkOutAt IS NULL")
    fun observeOnSiteCount(): Flow<Int>

    /**
     * Finished entries on a job, oldest first.
     *
     * Only the ones somebody has clocked out of: an open entry has no hours
     * yet, and counting a shift that has not ended would put a cost on the job
     * that changes every time the screen is looked at.
     *
     * Ordered oldest first because the timesheet groups them into days and
     * weeks, and the overtime bands depend on which day an hour fell in.
     */
    @Query(
        """
        SELECT * FROM time_entries
        WHERE projectId = :projectId AND checkOutAt IS NOT NULL
        ORDER BY checkInAt
        LIMIT 2000
        """,
    )
    fun observeCompletedTimeEntries(projectId: String): Flow<List<TimeEntryEntity>>
}
