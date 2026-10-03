package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.SubstanceEntity
import kotlinx.coroutines.flow.Flow

/** Write and read. There is no delete: what was kept on the site was kept there. */
@Dao
interface SubstanceDao {

    @Upsert
    suspend fun upsert(substance: SubstanceEntity)

    @Query("SELECT * FROM substances WHERE id = :id")
    suspend fun substance(id: String): SubstanceEntity?

    @Query("SELECT * FROM substances WHERE projectId = :projectId ORDER BY addedAt")
    fun observeForProject(projectId: String): Flow<List<SubstanceEntity>>

    @Query("SELECT COUNT(*) FROM substances WHERE projectId = :projectId")
    suspend fun countForProject(projectId: String): Int

    /** Every row on every job, for the search box. Capped: search wants the recent ones. */
    @Query("SELECT * FROM substances ORDER BY addedAt DESC LIMIT 500")
    suspend fun all(): List<SubstanceEntity>
}
