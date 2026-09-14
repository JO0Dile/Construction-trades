package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.PhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {

    @Upsert
    suspend fun upsert(photo: PhotoEntity)

    @Query("SELECT * FROM photos WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY capturedAt DESC")
    fun observeFor(ownerType: String, ownerId: String): Flow<List<PhotoEntity>>

    /** A project's plan and its progress photos are one query, two owner types. */
    @Query(
        """
        SELECT * FROM photos
        WHERE ownerId = :ownerId AND ownerType IN (:ownerTypes)
        ORDER BY capturedAt DESC
        """,
    )
    fun observeForAny(ownerId: String, ownerTypes: List<String>): Flow<List<PhotoEntity>>

    /**
     * How many pictures one thing has.
     *
     * Asked of the database rather than counted from a list the caller is
     * holding. A rule that rests on "there is evidence" must not be satisfied
     * by a caller's own arithmetic — that is exactly how the work-package
     * submit rule passed for weeks while the screen handed it a one.
     */
    @Query("SELECT COUNT(*) FROM photos WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun countFor(ownerType: String, ownerId: String): Int

    /**
     * The most recent one, for owners that have at most one thing worth
     * showing -- a person's face being the case this exists for.
     *
     * Newest rather than only, because somebody who retakes their photograph
     * has two rows and the second one is the answer. Keeping the first is
     * deliberate: it is what a register from March showed.
     */
    @Query(
        """
        SELECT * FROM photos
        WHERE ownerType = :ownerType AND ownerId = :ownerId
        ORDER BY capturedAt DESC
        LIMIT 1
        """,
    )
    suspend fun newestFor(ownerType: String, ownerId: String): PhotoEntity?

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun photo(id: String): PhotoEntity?

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun delete(id: String)

    /** The thumbnail on a stock row: the newest photo of that item, if any. */
    @Query(
        """
        SELECT * FROM photos
        WHERE ownerType = :ownerType AND ownerId IN (:ownerIds)
        ORDER BY capturedAt DESC
        """,
    )
    fun observeForOwners(ownerType: String, ownerIds: List<String>): Flow<List<PhotoEntity>>

    /**
     * Every photo of a given kind. Used for stock thumbnails: a van's worth of
     * item photos is small, and one query beats one per visible row.
     */
    @Query("SELECT * FROM photos WHERE ownerType = :ownerType ORDER BY capturedAt DESC")
    fun observeAllOfType(ownerType: String): Flow<List<PhotoEntity>>

    /**
     * Every project image in one query. A cover thumbnail per project row is
     * otherwise one query per visible row, which is the classic way to make a
     * list scroll badly.
     */
    @Query("SELECT * FROM photos WHERE ownerType IN (:ownerTypes) ORDER BY capturedAt DESC")
    fun observeAllOfTypes(ownerTypes: List<String>): Flow<List<PhotoEntity>>

    @Query("UPDATE photos SET ownerType = :ownerType WHERE id = :id")
    suspend fun setOwnerType(id: String, ownerType: String)
}
