package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.NonConformanceEntity
import kotlinx.coroutines.flow.Flow

/** Write and read. There is no delete: a non-conformance found was found, and is closed, not removed. */
@Dao
interface NonConformanceDao {

    @Upsert
    suspend fun upsert(report: NonConformanceEntity)

    @Query("SELECT * FROM non_conformances WHERE id = :id")
    suspend fun report(id: String): NonConformanceEntity?

    @Query("SELECT * FROM non_conformances WHERE projectId = :projectId ORDER BY raisedAt")
    fun observeForProject(projectId: String): Flow<List<NonConformanceEntity>>

    @Query("SELECT COUNT(*) FROM non_conformances WHERE projectId = :projectId")
    suspend fun countForProject(projectId: String): Int

    /** Every row on every job, for the search box. Capped: search wants the recent ones. */
    @Query("SELECT * FROM non_conformances ORDER BY raisedAt DESC LIMIT 500")
    suspend fun all(): List<NonConformanceEntity>
}
