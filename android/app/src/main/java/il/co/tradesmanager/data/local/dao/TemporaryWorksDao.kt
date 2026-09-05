package il.co.tradesmanager.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import il.co.tradesmanager.data.local.entity.TemporaryWorksEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TemporaryWorksDao {

    @Upsert
    suspend fun upsert(item: TemporaryWorksEntity)

    @Delete
    suspend fun delete(item: TemporaryWorksEntity)

    @Query("SELECT * FROM temporary_works WHERE id = :id")
    fun observe(id: String): Flow<TemporaryWorksEntity?>

    /**
     * Standing first, struck last; within each, oldest first.
     *
     * SQL cannot say which props are still owed days — that needs the clock and
     * the site's zone — so this orders only the part that does not depend on
     * when somebody looks.
     */
    @Query(
        """
        SELECT * FROM temporary_works
        WHERE projectId = :projectId
        ORDER BY struckAt IS NOT NULL, erectedAt IS NULL DESC, erectedAt
        LIMIT 300
        """,
    )
    fun observeForProject(projectId: String): Flow<List<TemporaryWorksEntity>>

    @Query("SELECT COUNT(*) FROM temporary_works")
    suspend fun count(): Int
}
