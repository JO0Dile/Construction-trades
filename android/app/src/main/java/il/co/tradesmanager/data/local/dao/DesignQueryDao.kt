package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.DesignQueryEntity
import kotlinx.coroutines.flow.Flow

/** Write and read. There is no delete: a question that was asked was asked. */
@Dao
interface DesignQueryDao {

    @Upsert
    suspend fun upsert(query: DesignQueryEntity)

    @Query("SELECT * FROM design_queries WHERE id = :id")
    suspend fun query(id: String): DesignQueryEntity?

    @Query("SELECT * FROM design_queries WHERE projectId = :projectId ORDER BY askedAt")
    fun observeForProject(projectId: String): Flow<List<DesignQueryEntity>>

    @Query("SELECT COUNT(*) FROM design_queries WHERE projectId = :projectId")
    suspend fun countForProject(projectId: String): Int

    /** Every row on every job, for the search box. Capped: search wants the recent ones. */
    @Query("SELECT * FROM design_queries ORDER BY askedAt DESC LIMIT 500")
    suspend fun all(): List<DesignQueryEntity>
}
