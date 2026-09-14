package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.ViolationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Insert and read. There is no delete.
 *
 * A cancelled draft stays, marked cancelled. A pattern of violations raised
 * against the same person and quietly dropped is itself worth being able to
 * see, and a register that could be emptied is not a register.
 */
@Dao
interface ViolationDao {

    @Upsert
    suspend fun upsert(violation: ViolationEntity)

    @Query("SELECT * FROM violations WHERE id = :id")
    fun observe(id: String): Flow<ViolationEntity?>

    @Query("SELECT * FROM violations WHERE id = :id")
    suspend fun violation(id: String): ViolationEntity?

    /**
     * Everything on one firm's site, newest first.
     *
     * The null arm is not a special case, it is the one-man band: somebody
     * working alone has a membership with no company on it, and their
     * violations carry no company either. `companyId = NULL` matches nothing
     * in SQL, so without the first clause their own register reads as empty
     * to them. The same shape as ProjectDao.observeProjects, for the same
     * reason.
     *
     * Bounded: an officer walking a large site for two years writes a lot of
     * these, and reading every one to draw a list is how a screen freezes.
     */
    @Query(
        """
        SELECT * FROM violations
        WHERE (:companyId IS NULL AND companyId IS NULL) OR companyId = :companyId
        ORDER BY recordedAt DESC
        LIMIT :limit
        """,
    )
    fun observeForCompany(companyId: String?, limit: Int = 300): Flow<List<ViolationEntity>>

    /**
     * What is on one person's name.
     *
     * Confirmed only. A draft is not yet an accusation and the person it names
     * has not been told about it — showing them one an officer is still
     * deciding about would be worse than not showing them anything.
     */
    @Query(
        """
        SELECT * FROM violations
        WHERE againstAccountId = :accountId AND status = 'CONFIRMED'
        ORDER BY recordedAt DESC
        LIMIT :limit
        """,
    )
    fun observeConfirmedAgainst(accountId: String, limit: Int = 200): Flow<List<ViolationEntity>>

    /** The officer's own drafts, which nobody else can see. */
    @Query(
        """
        SELECT * FROM violations
        WHERE recordedByAccountId = :accountId AND status = 'DRAFT'
        ORDER BY recordedAt DESC
        """,
    )
    fun observeDraftsBy(accountId: String): Flow<List<ViolationEntity>>
}
