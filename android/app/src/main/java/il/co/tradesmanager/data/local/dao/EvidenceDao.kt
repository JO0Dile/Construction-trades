package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.PermitEntity
import il.co.tradesmanager.data.local.entity.PermitPrecautionEntity
import il.co.tradesmanager.data.local.entity.SnagEntity
import il.co.tradesmanager.data.local.entity.ToolboxTalkAttendeeEntity
import il.co.tradesmanager.data.local.entity.ToolboxTalkEntity
import kotlinx.coroutines.flow.Flow

/** One person and the last time they sat through a briefing, for the rollup. */
data class BriefingRecord(
    val accountId: String,
    val name: String,
    val lastAttendedAt: Long?,
)

@Dao
interface EvidenceDao {

    @Upsert
    suspend fun upsertTalk(talk: ToolboxTalkEntity)

    @Delete
    suspend fun deleteTalk(talk: ToolboxTalkEntity)

    @Query("SELECT * FROM toolbox_talks WHERE id = :id")
    fun observeTalk(id: String): Flow<ToolboxTalkEntity?>

    @Query("SELECT * FROM toolbox_talks WHERE id = :id")
    suspend fun talk(id: String): ToolboxTalkEntity?

    @Query(
        """
        SELECT * FROM toolbox_talks
        WHERE (:projectId IS NULL OR projectId = :projectId)
        ORDER BY heldAt DESC LIMIT 200
        """,
    )
    fun observeTalks(projectId: String?): Flow<List<ToolboxTalkEntity>>

    @Upsert
    suspend fun upsertAttendee(attendee: ToolboxTalkAttendeeEntity)

    @Delete
    suspend fun deleteAttendee(attendee: ToolboxTalkAttendeeEntity)

    @Query("SELECT * FROM toolbox_talk_attendees WHERE talkId = :talkId ORDER BY name")
    fun observeAttendees(talkId: String): Flow<List<ToolboxTalkAttendeeEntity>>

    @Query("SELECT COUNT(*) FROM toolbox_talk_attendees WHERE talkId = :talkId")
    suspend fun attendeeCount(talkId: String): Int

    /**
     * Everyone on the books and when they were last briefed.
     *
     * A LEFT JOIN, so somebody who has never attended anything comes back with
     * a null rather than vanishing from the list — which is the one row a
     * safety officer most needs to see.
     */
    @Query(
        """
        SELECT a.id AS accountId, a.displayName AS name, MAX(t.heldAt) AS lastAttendedAt
        FROM accounts a
        LEFT JOIN toolbox_talk_attendees att ON att.accountId = a.id
        LEFT JOIN toolbox_talks t ON t.id = att.talkId
        WHERE a.deletedAt IS NULL
        GROUP BY a.id, a.displayName
        ORDER BY lastAttendedAt IS NOT NULL, lastAttendedAt, a.displayName
        """,
    )
    fun observeBriefingRecords(): Flow<List<BriefingRecord>>

    @Upsert
    suspend fun upsertPermit(permit: PermitEntity)

    @Delete
    suspend fun deletePermit(permit: PermitEntity)

    @Query("SELECT * FROM permits WHERE id = :id")
    fun observePermit(id: String): Flow<PermitEntity?>

    @Query("SELECT * FROM permits WHERE id = :id")
    suspend fun permit(id: String): PermitEntity?

    /**
     * Live permits first, then everything else newest first.
     *
     * The ordering is deliberately coarse: SQL knows which permits are ISSUED
     * but not which of those have run out, because that depends on the clock.
     * The screen sorts the live ones by how long they have left, using the one
     * tested definition of that in `core.evidence.Permits`.
     */
    @Query(
        """
        SELECT * FROM permits
        WHERE (:projectId IS NULL OR projectId = :projectId)
        ORDER BY status = 'ISSUED' DESC, validTo IS NULL, validTo, createdAt DESC
        LIMIT 200
        """,
    )
    fun observePermits(projectId: String?): Flow<List<PermitEntity>>

    @Query("SELECT COUNT(*) FROM permits")
    suspend fun permitCount(): Int

    @Upsert
    suspend fun upsertPrecaution(precaution: PermitPrecautionEntity)

    @Upsert
    suspend fun upsertPrecautions(precautions: List<PermitPrecautionEntity>)

    @Query("SELECT * FROM permit_precautions WHERE permitId = :permitId ORDER BY sortOrder")
    fun observePrecautions(permitId: String): Flow<List<PermitPrecautionEntity>>

    @Query("SELECT * FROM permit_precautions WHERE permitId = :permitId ORDER BY sortOrder")
    suspend fun precautions(permitId: String): List<PermitPrecautionEntity>

    @Upsert
    suspend fun upsertSnag(snag: SnagEntity)

    @Delete
    suspend fun deleteSnag(snag: SnagEntity)

    @Query("SELECT * FROM snags WHERE id = :id")
    fun observeSnag(id: String): Flow<SnagEntity?>

    @Query("SELECT * FROM snags WHERE id = :id")
    suspend fun snag(id: String): SnagEntity?

    /**
     * Outstanding first, then by when they were due.
     *
     * SQL can tell a closed snag from an open one, but not an overdue one from
     * a merely late-ish one — that depends on the clock. The coarse ordering
     * is here; the rest is done in Kotlin against one tested definition.
     */
    @Query(
        """
        SELECT * FROM snags
        WHERE (:projectId IS NULL OR projectId = :projectId)
        ORDER BY status = 'CLOSED', dueOn IS NULL, dueOn, raisedAt DESC
        LIMIT 500
        """,
    )
    fun observeSnags(projectId: String?): Flow<List<SnagEntity>>

    @Query("SELECT COUNT(*) FROM snags")
    suspend fun snagCount(): Int
}
