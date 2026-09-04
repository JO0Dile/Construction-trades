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

    @Query("UPDATE photos SET ownerType = :ownerType WHERE id = :id")
    suspend fun setOwnerType(id: String, ownerType: String)
}
