package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.MusterEntity
import il.co.tradesmanager.data.local.entity.MusterPersonEntity
import kotlinx.coroutines.flow.Flow

/**
 * Write and read. There is no delete, on either table.
 *
 * A roll call that ended with somebody unaccounted for is exactly the record a
 * firm would want gone, which is the reason it cannot be.
 */
@Dao
interface MusterDao {

    @Upsert
    suspend fun upsert(muster: MusterEntity)

    @Upsert
    suspend fun upsertPerson(person: MusterPersonEntity)

    @Upsert
    suspend fun upsertPeople(rows: List<MusterPersonEntity>)

    @Query("SELECT * FROM musters WHERE id = :id")
    fun observe(id: String): Flow<MusterEntity?>

    @Query("SELECT * FROM musters WHERE id = :id")
    suspend fun muster(id: String): MusterEntity?

    /**
     * The roll call that is running, if one is.
     *
     * Deliberately not scoped to a job. If an alarm is going somewhere, the app
     * should say so wherever the phone happens to be, and two roll calls at
     * once on one phone is not a case worth supporting — the newest wins.
     */
    @Query("SELECT * FROM musters WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeLive(): Flow<MusterEntity?>

    /** The same row, read once, for the check that refuses a second roll call. */
    @Query("SELECT * FROM musters WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun liveNow(): MusterEntity?

    /**
     * One firm's past roll calls, newest first.
     *
     * The null arm is the one-man band, not everybody: somebody working alone
     * has a membership with no company on it, and `companyId = NULL` matches
     * nothing in SQL, so without the first clause their own history would read
     * as empty to them. The same shape as ViolationDao.observeForCompany, for
     * the same reason.
     */
    @Query(
        """
        SELECT * FROM musters
        WHERE (:companyId IS NULL AND companyId IS NULL) OR companyId = :companyId
        ORDER BY startedAt DESC
        LIMIT :limit
        """,
    )
    fun observeForCompany(companyId: String?, limit: Int = 100): Flow<List<MusterEntity>>

    @Query("SELECT * FROM muster_people WHERE musterId = :musterId")
    fun observePeople(musterId: String): Flow<List<MusterPersonEntity>>

    @Query("SELECT * FROM muster_people WHERE musterId = :musterId")
    suspend fun people(musterId: String): List<MusterPersonEntity>

    @Query("SELECT * FROM muster_people WHERE id = :id")
    suspend fun person(id: String): MusterPersonEntity?
}
