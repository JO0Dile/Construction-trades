package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.MeetingActionEntity
import il.co.tradesmanager.data.local.entity.MeetingEntity
import kotlinx.coroutines.flow.Flow

/** Write and read. There is no delete: what a meeting agreed stays agreed, and a point is closed, not removed. */
@Dao
interface MeetingDao {

    @Upsert
    suspend fun upsert(meeting: MeetingEntity)

    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun meeting(id: String): MeetingEntity?

    @Query("SELECT * FROM meetings WHERE projectId = :projectId ORDER BY heldOnDay DESC, recordedAt DESC")
    fun observeForProject(projectId: String): Flow<List<MeetingEntity>>

    @Query("SELECT COUNT(*) FROM meetings WHERE projectId = :projectId")
    suspend fun countForProject(projectId: String): Int

    @Upsert
    suspend fun upsertAction(action: MeetingActionEntity)

    @Query("SELECT * FROM meeting_actions WHERE id = :id")
    suspend fun action(id: String): MeetingActionEntity?

    @Query("SELECT COUNT(*) FROM meeting_actions WHERE meetingId = :meetingId")
    suspend fun countActionsFor(meetingId: String): Int

    @Query("SELECT * FROM meeting_actions WHERE projectId = :projectId ORDER BY raisedAt")
    fun observeActionsForProject(projectId: String): Flow<List<MeetingActionEntity>>

    /** Every point on every job, for the search box. Capped: search wants the recent ones. */
    @Query("SELECT * FROM meeting_actions ORDER BY raisedAt DESC LIMIT 500")
    suspend fun allActions(): List<MeetingActionEntity>
}
