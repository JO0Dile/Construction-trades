package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.SiteVisitEntity
import kotlinx.coroutines.flow.Flow

/** Write and read. There is no delete: a visit that happened happened. */
@Dao
interface VisitDao {

    @Upsert
    suspend fun upsert(visit: SiteVisitEntity)

    @Query("SELECT * FROM site_visits WHERE id = :id")
    suspend fun visit(id: String): SiteVisitEntity?

    /** One job's visits, newest first. */
    @Query("SELECT * FROM site_visits WHERE projectId = :projectId ORDER BY arrivedAt DESC LIMIT :limit")
    fun observeForProject(projectId: String, limit: Int = 200): Flow<List<SiteVisitEntity>>

    /**
     * Everybody signed in and not out, on any job this phone holds. What a
     * roll call starts from, alongside the clockings.
     */
    @Query("SELECT * FROM site_visits WHERE leftAt IS NULL ORDER BY arrivedAt")
    suspend fun stillHere(): List<SiteVisitEntity>

    /** How many are signed in and not out: the visitors the next roll call will list. */
    @Query("SELECT COUNT(*) FROM site_visits WHERE leftAt IS NULL")
    fun observeStillHereCount(): Flow<Int>

    /** Every row on every job, for the search box. Capped: search wants the recent ones. */
    @Query("SELECT * FROM site_visits ORDER BY arrivedAt DESC LIMIT 500")
    suspend fun all(): List<SiteVisitEntity>
}
